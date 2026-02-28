package com.example.maidmarriage.compat;

import com.example.maidmarriage.advancement.ModAdvancements;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * 结婚与交互核心逻辑：处理求婚、戒指刻字、测试道具等。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class MarriageEventHandler {
    private static final String TAG_PLAYER_PRIMARY_MAID = "maidmarriage_primary_maid";
    private static final String TAG_RING_USED = "maidmarriage_ring_used";
    private static final String TAG_RING_PLAYER = "maidmarriage_ring_player";
    private static final String TAG_RING_MAID = "maidmarriage_ring_maid";

    private MarriageEventHandler() {
    }

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        if (event.getWorld().isClientSide()) {
            return;
        }
        if (ModTaskData.MARRIAGE_DATA == null) {
            return;
        }

        ItemStack stack = event.getStack();
        if (stack.isEmpty()) {
            return;
        }

        if (stack.is(ModItems.PROPOSAL_RING.get())) {
            handleProposal(event.getPlayer(), event.getMaid(), stack);
            event.setCanceled(true);
            return;
        }
        if (stack.is(ModItems.YES_PILLOW.get())) {
            handleBreedingTest(event.getPlayer(), event.getMaid());
            event.setCanceled(true);
            return;
        }
        if (stack.is(ModItems.LONGING_TESTER.get())) {
            handleLongingTest(event.getPlayer(), event.getMaid());
            event.setCanceled(true);
        }
    }

    private static void handleProposal(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.need_owner"));
            return;
        }

        // 仅限制玩家和自己的子代女仆结婚，其他人的子代可结婚
        if (maid.getTags().contains(MaidChildEntity.BORN_MAID_TAG) && MaidChildEntity.isChildOfPlayer(maid, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.own_child_cannot_marry"));
            return;
        }

        int requiredFavorability = ModConfigs.requiredFavorability();
        if (maid.getFavorability() < requiredFavorability) {
            player.sendSystemMessage(Component.translatable(
                    "message.maidmarriage.proposal.need_favorability", requiredFavorability));
            return;
        }
        if (!ModConfigs.haremMode() && hasOtherMarriage(player, maid)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.harem_disabled"));
            return;
        }

        MarriageData currentData = maid.getOrCreateData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
        if (currentData.isMarriedWith(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.already_married_with_you"));
            return;
        }
        if (currentData.married()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.already_married"));
            return;
        }

        ItemStack offhandRing = player.getOffhandItem();
        if (!offhandRing.is(ModItems.PROPOSAL_RING.get())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.need_offhand_ring"));
            return;
        }
        if (isRingUsed(stack) || isRingUsed(offhandRing)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.ring_used"));
            return;
        }

        ItemStack maidRing = stack.copyWithCount(1);

        maid.setAndSyncData(ModTaskData.MARRIAGE_DATA, currentData.marry(player.getUUID(), maid.level().getGameTime()));
        engraveRing(offhandRing, player, maid);
        engraveRing(maidRing, player, maid);
        giveRingToMaid(maid, maidRing);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        giveMarriagePillows(player, maid);
        if (!ModConfigs.haremMode()) {
            player.getPersistentData().putUUID(TAG_PLAYER_PRIMARY_MAID, maid.getUUID());
        }

        if (maid.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1), maid.getZ(),
                    10, 0.25, 0.25, 0.25, 0.01);
        }
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.sendSystemMessage(Component.translatable("message.maidmarriage.proposal.success"));
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ModAdvancements.grantMarriage(serverPlayer);
            RomanceSleepManager.startProposalDialogue(serverPlayer, maid);
        }
    }

    private static void handleBreedingTest(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_owner"));
            return;
        }
        if (!isMarriedWithPlayer(maid, player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.not_married"));
            return;
        }
        if (player.level().isDay()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.not_time_yet"));
            RomanceSleepManager.speakSingleLine(maid, "dialogue.maidmarriage.daytime");
            return;
        }

        RomanceSleepManager.tryStartRomanceSleep(serverPlayer, maid);
    }

    private static void handleLongingTest(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_owner"));
            return;
        }
        PregnancyData current = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, current.forceLonging(maid.level().getGameTime()));
        player.sendSystemMessage(Component.translatable("message.maidmarriage.debug.longing_applied"));
    }

    private static boolean isMarriedWithPlayer(EntityMaid maid, net.minecraft.world.entity.player.Player player) {
        MarriageData data = maid.getData(ModTaskData.MARRIAGE_DATA);
        return data != null && data.isMarriedWith(player.getUUID());
    }

    private static boolean hasOtherMarriage(net.minecraft.world.entity.player.Player player, EntityMaid currentMaid) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.hasUUID(TAG_PLAYER_PRIMARY_MAID)) {
            return false;
        }
        return !tag.getUUID(TAG_PLAYER_PRIMARY_MAID).equals(currentMaid.getUUID());
    }

    private static boolean isRingUsed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(TAG_RING_USED);
    }

    private static void engraveRing(ItemStack ring, net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        CustomData.update(DataComponents.CUSTOM_DATA, ring, tag -> {
            tag.putBoolean(TAG_RING_USED, true);
            tag.putUUID(TAG_RING_PLAYER, player.getUUID());
            tag.putUUID(TAG_RING_MAID, maid.getUUID());
        });
        ring.set(DataComponents.CUSTOM_NAME, Component.translatable("item.maidmarriage.vow_ring"));
        ring.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.maidmarriage.vow_ring.pair", player.getName(), maid.getName()),
                Component.translatable("item.maidmarriage.vow_ring.desc"))));
    }

    private static void giveRingToMaid(EntityMaid maid, ItemStack ring) {
        if (ring.isEmpty()) {
            return;
        }
        if (maid.getMainHandItem().isEmpty()) {
            maid.setItemInHand(InteractionHand.MAIN_HAND, ring);
            return;
        }
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), ring, false);
        if (!remaining.isEmpty()) {
            ItemEntity drop = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5, maid.getZ(), remaining);
            maid.level().addFreshEntity(drop);
        }
    }

    private static void giveMarriagePillows(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        ItemStack pillowForPlayer = new ItemStack(ModItems.YES_PILLOW.get());
        if (!player.getInventory().add(pillowForPlayer)) {
            player.drop(pillowForPlayer, false);
        }

        ItemStack pillowForMaid = new ItemStack(ModItems.YES_PILLOW.get());
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), pillowForMaid, false);
        if (!remaining.isEmpty()) {
            ItemEntity drop = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5, maid.getZ(), remaining);
            maid.level().addFreshEntity(drop);
        }
    }
}
