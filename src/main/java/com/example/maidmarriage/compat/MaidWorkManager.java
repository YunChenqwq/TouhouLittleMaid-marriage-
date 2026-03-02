package com.example.maidmarriage.compat;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModEntities;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Child maid work system:
 * 1) use maid task modes to run study/exploration;
 * 2) consume backpack items automatically for each run;
 * 3) handle favorability cost, lock, and idle recovery.
 */
public final class MaidWorkManager {
    private static final double GLOBAL_DURATION_SCALE = 0.8D;
    private static final double FAVOR_DURATION_BASE = 50.0D;
    private static final double MIN_FAVOR_DURATION_COEFFICIENT = 0.2D;
    private static final int MIN_ACTION_DURATION_TICKS = 100;

    private static final int LEARN_DURATION_TICKS = 3000;
    private static final int EXPLORE_NEAR_DURATION_TICKS = 3000;
    private static final int EXPLORE_RUINS_DURATION_TICKS = 6000;
    private static final int EXPLORE_ABYSS_DURATION_TICKS = 9000;
    private static final int IDLE_RECOVER_TICKS = 24000;
    private static final int IDLE_RECOVER_FAVOR = 5;

    private static final int FAVOR_DEFAULT = 64;
    private static final int FAVOR_MAX = 300;
    private static final int FAVOR_ACTION_COST = 2;
    private static final int FAVOR_BLOCK = 10;
    private static final int FAVOR_UNLOCK = 15;
    private static final float EXPLORE_MIN_HEALTH_RATIO = 0.30F;

    private static final String TAG_ACTION_MODE = "maidmarriage_child_action_mode";
    private static final String TAG_ACTION_END = "maidmarriage_child_action_end";
    private static final String TAG_ACTION_OWNER = "maidmarriage_child_action_owner";
    private static final String TAG_ACTION_LOCKED = "maidmarriage_child_action_locked";
    private static final String TAG_IDLE_START = "maidmarriage_child_idle_start";
    private static final String TAG_FAVOR_INITIALIZED = "maidmarriage_child_favor_initialized";
    private static final String TAG_LAST_HEALTH_HINT = "maidmarriage_child_last_health_hint";
    private static final String TAG_MISSING_MATERIAL_MODE = "maidmarriage_child_missing_material_mode";
    private static final String TAG_LAST_COUNTDOWN_SECOND = "maidmarriage_child_last_countdown_second";
    private static final String TAG_LAST_WORK_TIME_HINT = "maidmarriage_child_last_work_time_hint";
    private static final String TAG_GENERATED_REWARD = "maidmarriage_generated_reward";

    private static final long HINT_COOLDOWN_TICKS = 200L;

    private static final List<ResourceKey<Enchantment>> HIGH_TIER_ENCHANTS = List.of(
            Enchantments.MENDING, Enchantments.UNBREAKING, Enchantments.SHARPNESS, Enchantments.PROTECTION,
            Enchantments.POWER, Enchantments.EFFICIENCY, Enchantments.LOOTING, Enchantments.FORTUNE
    );
    private static final List<ResourceKey<Enchantment>> NORMAL_TIER_ENCHANTS = List.of(
            Enchantments.FEATHER_FALLING, Enchantments.RESPIRATION, Enchantments.QUICK_CHARGE,
            Enchantments.KNOCKBACK, Enchantments.SMITE, Enchantments.FIRE_ASPECT
    );

    private static final List<Holder<Potion>> HIGH_TIER_POTIONS = List.of(
            Potions.STRONG_HEALING, Potions.STRONG_REGENERATION, Potions.STRONG_STRENGTH,
            Potions.STRONG_SWIFTNESS, Potions.LONG_INVISIBILITY, Potions.LONG_FIRE_RESISTANCE
    );
    private static final List<Holder<Potion>> NORMAL_TIER_POTIONS = List.of(
            Potions.HEALING, Potions.REGENERATION, Potions.STRENGTH,
            Potions.SWIFTNESS, Potions.FIRE_RESISTANCE, Potions.NIGHT_VISION
    );

    private MaidWorkManager() {
    }

    /**
     * Register child work modes into the maid task panel.
     */
    public static void addChildWorkTasks(TaskManager manager) {
        for (WorkMode mode : WorkMode.values()) {
            manager.add(new ChildWorkTask(mode));
        }
    }

    /**
     * Keep only favorability recovery on right click.
     * Study/exploration is now task-mode driven.
     */
    public static boolean tryHandleFavorRecovery(Player player, EntityMaid maid, ItemStack stack) {
        if (player.level().isClientSide() || !isBornMaid(maid) || !maid.isOwnedBy(player)) {
            return false;
        }
        ensureDefaultFavorability(maid);
        return tryRecoverFavorability(player, maid, stack);
    }

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide() || maid.tickCount % 20 != 0 || !isBornMaid(maid)) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        if (tryRestoreChildMaidEntity(level, maid)) {
            return;
        }

        ensureDefaultFavorability(maid);
        CompoundTag tag = maid.getPersistentData();
        long now = level.getGameTime();

        if (isActionBusy(maid)) {
            WorkMode runningMode = WorkMode.fromKey(tag.getString(TAG_ACTION_MODE)).orElse(null);
            WorkMode selectedMode = WorkMode.fromTask(maid.getTask()).orElse(null);
            if (runningMode == null || selectedMode != runningMode) {
                clearAction(tag);
                return;
            }
            tag.remove(TAG_IDLE_START);
            if (!canProgressCurrentAction(maid)) {
                freezeActionProgress(tag, now);
                return;
            }
            pushActionCountdown(level, maid, tag);
            if (now >= tag.getLong(TAG_ACTION_END)) {
                finishCurrentAction(level, maid, tag);
                if (tryStartActionByTask(level, maid, tag)) {
                    return;
                }
            }
            return;
        }

        if (tryStartActionByTask(level, maid, tag)) {
            tag.remove(TAG_IDLE_START);
            return;
        }

        tickIdleFavorRecovery(level, maid, tag);
    }

    private static void finishCurrentAction(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        WorkMode mode = WorkMode.fromKey(tag.getString(TAG_ACTION_MODE)).orElse(null);
        if (mode == null) {
            clearAction(tag);
            return;
        }

        ServerPlayer owner = resolveOwner(level, maid, tag);
        if (mode.actionType == ActionType.LEARN) {
            completeLearning(level, maid, owner, mode.learnType);
        } else {
            completeExploration(level, maid, owner, mode.exploreDifficulty);
        }
        clearAction(tag);
    }

    private static boolean tryStartActionByTask(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        WorkMode mode = WorkMode.fromTask(maid.getTask()).orElse(null);
        if (mode == null) {
            return false;
        }

        ServerPlayer owner = getOwner(maid);
        if (!checkFavorabilityGate(owner, maid)) {
            return false;
        }
        if (!canStartActionNow(level, maid)) {
            maybeSendCooldownHint(level, owner, tag, "message.maidmarriage.breed.not_time_yet", TAG_LAST_WORK_TIME_HINT);
            return false;
        }

        if (mode.actionType == ActionType.EXPLORE && maid.getHealth() < maid.getMaxHealth() * EXPLORE_MIN_HEALTH_RATIO) {
            maybeSendCooldownHint(level, owner, tag, "message.maidmarriage.child.explore.need_health", TAG_LAST_HEALTH_HINT);
            return false;
        }

        if (!consumeInputForMode(maid, mode)) {
            notifyMissingMaterialOnce(owner, tag, mode);
            return false;
        }

        spendActionCost(maid);
        tag.remove(TAG_MISSING_MATERIAL_MODE);
        tag.putString(TAG_ACTION_MODE, mode.key);
        int actionDurationTicks = calculateActionDurationTicks(mode.durationTicks, maid.getFavorability());
        tag.putLong(TAG_ACTION_END, level.getGameTime() + actionDurationTicks);
        UUID ownerUuid = maid.getOwnerUUID();
        if (ownerUuid != null) {
            tag.putUUID(TAG_ACTION_OWNER, ownerUuid);
        } else {
            tag.remove(TAG_ACTION_OWNER);
        }

        if (owner != null) {
            if (mode.actionType == ActionType.LEARN) {
                owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.learn.start", mode.learnType.display()));
            } else {
                owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.explore.start", mode.exploreDifficulty.display()));
            }
        }

        level.playSound(null, maid.blockPosition(),
                mode.actionType == ActionType.LEARN ? SoundEvents.ENCHANTMENT_TABLE_USE : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.7F, 1.15F);
        return true;
    }

    private static void notifyMissingMaterialOnce(ServerPlayer owner, CompoundTag tag, WorkMode mode) {
        if (owner == null) {
            return;
        }
        String markedMode = tag.getString(TAG_MISSING_MATERIAL_MODE);
        if (mode.key.equals(markedMode)) {
            return;
        }
        tag.putString(TAG_MISSING_MATERIAL_MODE, mode.key);
        owner.sendSystemMessage(Component.translatable(
                "message.maidmarriage.child.material.missing",
                mode.display(),
                mode.requirementDisplay()));
    }

    private static void tickIdleFavorRecovery(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        long now = level.getGameTime();
        long idleStart = tag.contains(TAG_IDLE_START) ? tag.getLong(TAG_IDLE_START) : now;
        if (!tag.contains(TAG_IDLE_START)) {
            tag.putLong(TAG_IDLE_START, now);
            return;
        }

        if (now - idleStart < IDLE_RECOVER_TICKS) {
            return;
        }

        int newFavor = clampFavorability(maid.getFavorability() + IDLE_RECOVER_FAVOR);
        maid.setFavorability(newFavor);
        tag.putLong(TAG_IDLE_START, now);

        if (tag.getBoolean(TAG_ACTION_LOCKED) && newFavor >= FAVOR_UNLOCK) {
            tag.putBoolean(TAG_ACTION_LOCKED, false);
        }

        ServerPlayer owner = getOwner(maid);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.favor.rested", newFavor));
        }
    }

    private static boolean tryRecoverFavorability(Player player, EntityMaid maid, ItemStack stack) {
        int restore = 0;
        ItemStack extraReturn = ItemStack.EMPTY;

        if (stack.is(Items.SUGAR)) {
            restore = 1;
        } else if (stack.is(Items.MILK_BUCKET)) {
            restore = 2;
            extraReturn = new ItemStack(Items.BUCKET);
        } else if (stack.is(Items.GOLDEN_APPLE)) {
            restore = 3;
        }

        if (restore <= 0) {
            return false;
        }

        int favor = clampFavorability(maid.getFavorability() + restore);
        maid.setFavorability(favor);

        CompoundTag tag = maid.getPersistentData();
        if (tag.getBoolean(TAG_ACTION_LOCKED) && favor >= FAVOR_UNLOCK) {
            tag.putBoolean(TAG_ACTION_LOCKED, false);
            player.sendSystemMessage(Component.translatable("message.maidmarriage.child.favor.unlocked", favor));
        } else {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.child.favor.recover", favor));
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (!extraReturn.isEmpty() && !player.getInventory().add(extraReturn)) {
                player.drop(extraReturn, false);
            }
        }
        return true;
    }

    private static boolean checkFavorabilityGate(ServerPlayer owner, EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        int favor = maid.getFavorability();
        boolean locked = tag.getBoolean(TAG_ACTION_LOCKED);

        if (locked && favor >= FAVOR_UNLOCK) {
            tag.putBoolean(TAG_ACTION_LOCKED, false);
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.favor.unlocked", favor));
            }
            return true;
        }
        if (locked) {
            return false;
        }
        if (favor < FAVOR_BLOCK) {
            tag.putBoolean(TAG_ACTION_LOCKED, true);
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.favor.blocked", favor));
            }
            return false;
        }
        return true;
    }

    private static void spendActionCost(EntityMaid maid) {
        int favor = clampFavorability(maid.getFavorability() - FAVOR_ACTION_COST);
        maid.setFavorability(favor);
        if (favor < FAVOR_BLOCK) {
            maid.getPersistentData().putBoolean(TAG_ACTION_LOCKED, true);
        }
    }

    private static boolean isActionBusy(EntityMaid maid) {
        return maid.getPersistentData().contains(TAG_ACTION_END);
    }

    private static void clearAction(CompoundTag tag) {
        tag.remove(TAG_ACTION_MODE);
        tag.remove(TAG_ACTION_END);
        tag.remove(TAG_ACTION_OWNER);
        tag.remove(TAG_LAST_COUNTDOWN_SECOND);
    }

    private static ServerPlayer getOwner(EntityMaid maid) {
        if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        return null;
    }

    private static ServerPlayer resolveOwner(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        if (tag.hasUUID(TAG_ACTION_OWNER)) {
            UUID ownerUuid = tag.getUUID(TAG_ACTION_OWNER);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (player != null) {
                return player;
            }
        }
        return getOwner(maid);
    }

    private static boolean consumeInputForMode(EntityMaid maid, WorkMode mode) {
        if (consumeInputFromMainHand(maid, mode.inputMatcher)) {
            return true;
        }
        if (!mode.allowBackpackForUnstackable) {
            return false;
        }
        return consumeUnstackableInputFromBackpack(maid, mode.inputMatcher);
    }

    private static boolean consumeInputFromMainHand(EntityMaid maid, Predicate<ItemStack> matcher) {
        ItemStack mainHand = maid.getMainHandItem();
        if (!isPlayerInputMaterial(mainHand, matcher)) {
            return false;
        }
        mainHand.shrink(1);
        if (mainHand.isEmpty()) {
            maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        return true;
    }

    private static boolean consumeUnstackableInputFromBackpack(EntityMaid maid, Predicate<ItemStack> matcher) {
        var inventory = maid.getAvailableBackpackInv();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!isPlayerInputMaterial(stack, matcher) || stack.getMaxStackSize() > 1) {
                continue;
            }
            ItemStack extracted = inventory.extractItem(i, 1, false);
            if (!extracted.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void maybeSendCooldownHint(ServerLevel level, ServerPlayer owner, CompoundTag tag, String key, String hintTag) {
        if (owner == null) {
            return;
        }
        long now = level.getGameTime();
        long last = tag.getLong(hintTag);
        if (now - last < HINT_COOLDOWN_TICKS) {
            return;
        }
        tag.putLong(hintTag, now);
        owner.sendSystemMessage(Component.translatable(key));
    }

    private static void pushActionCountdown(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        ServerPlayer owner = resolveOwner(level, maid, tag);
        if (owner == null) {
            return;
        }
        WorkMode mode = WorkMode.fromKey(tag.getString(TAG_ACTION_MODE)).orElse(null);
        if (mode == null) {
            return;
        }
        long remainTicks = Math.max(0L, tag.getLong(TAG_ACTION_END) - level.getGameTime());
        int remainSeconds = (int) ((remainTicks + 19L) / 20L);
        if (tag.getInt(TAG_LAST_COUNTDOWN_SECOND) == remainSeconds) {
            return;
        }
        tag.putInt(TAG_LAST_COUNTDOWN_SECOND, remainSeconds);
        owner.displayClientMessage(Component.translatable(
                "message.maidmarriage.child.action.countdown",
                mode.display(),
                formatRemainTime(remainSeconds)), true);
    }

    private static String formatRemainTime(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static void completeLearning(ServerLevel level, EntityMaid maid, ServerPlayer owner, LearnType learnType) {
        List<ItemStack> rewards = switch (learnType) {
            case ENCHANTMENT -> createEnchantmentRewards(level, maid);
            case ALCHEMY -> createAlchemyRewards(level, maid);
            case TACTICS -> createTacticsRewards(level, maid);
        };

        deliverRewards(maid, owner, rewards);

        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.learn.finish", learnType.display()));
        }
        level.playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.35F);
    }

    private static void completeExploration(ServerLevel level, EntityMaid maid, ServerPlayer owner, ExploreDifficulty difficulty) {
        ExploreResult result = createExploreRewards(maid, difficulty);

        if (result.injured) {
            float hurtValue = (float) (maid.getMaxHealth() * result.hurtRatio);
            maid.setHealth(Math.max(1.0F, maid.getHealth() - hurtValue));
        }

        if (!result.rewards.isEmpty()) {
            deliverRewards(maid, owner, result.rewards);
        }

        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.explore.finish", difficulty.display()));
            if (result.emptyHanded) {
                owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.explore.empty"));
            }
            if (result.injured) {
                owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.explore.injured"));
            }
        }

        level.playSound(null, maid.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.1F);
    }

    private static List<ItemStack> createEnchantmentRewards(ServerLevel level, EntityMaid maid) {
        RandomSource random = maid.getRandom();
        double favorRate = favorRate(maid);
        List<ItemStack> result = new ArrayList<>();

        result.add(createRandomEnchantedBook(level, random, favorRate));
        if (random.nextDouble() < 0.15D + favorRate * 0.30D) {
            result.add(new ItemStack(Items.LAPIS_LAZULI, 2 + random.nextInt(4)));
        }
        return result;
    }

    private static ItemStack createRandomEnchantedBook(ServerLevel level, RandomSource random, double favorRate) {
        HolderGetter<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        boolean highTier = random.nextDouble() < 0.20D + favorRate * 0.45D;
        List<ResourceKey<Enchantment>> pool = highTier ? HIGH_TIER_ENCHANTS : NORMAL_TIER_ENCHANTS;
        ResourceKey<Enchantment> key = pool.get(random.nextInt(pool.size()));
        Holder<Enchantment> holder = registry.getOrThrow(key);

        int desired = highTier ? 3 + random.nextInt(3) : 1 + random.nextInt(3);
        int levelValue = Math.min(desired, holder.value().getMaxLevel());
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, levelValue));
    }

    private static List<ItemStack> createAlchemyRewards(ServerLevel level, EntityMaid maid) {
        RandomSource random = maid.getRandom();
        double favorRate = favorRate(maid);
        List<ItemStack> result = new ArrayList<>();
        boolean highTier = random.nextDouble() < 0.15D + favorRate * 0.40D;
        List<Holder<Potion>> pool = highTier ? HIGH_TIER_POTIONS : NORMAL_TIER_POTIONS;
        Holder<Potion> potion = pool.get(random.nextInt(pool.size()));
        Item potionItem = highTier && random.nextDouble() < 0.35D ? Items.LINGERING_POTION : Items.POTION;
        result.add(PotionContents.createItemStack(potionItem, potion));

        if (random.nextDouble() < 0.10D + favorRate * 0.35D) {
            result.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 1 + random.nextInt(2)));
        }
        return result;
    }

    private static List<ItemStack> createTacticsRewards(ServerLevel level, EntityMaid maid) {
        RandomSource random = maid.getRandom();
        double favorRate = favorRate(maid);
        List<ItemStack> result = new ArrayList<>();

        boolean highTier = random.nextDouble() < 0.18D + favorRate * 0.38D;
        ItemStack weapon = createRandomWeapon(level, random, highTier);
        result.add(weapon);

        if (weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW)) {
            result.add(new ItemStack(Items.ARROW, 8 + random.nextInt(17)));
        }
        return result;
    }

    private static ItemStack createRandomWeapon(ServerLevel level, RandomSource random, boolean highTier) {
        Item item;
        if (highTier) {
            Item[] highPool = {Items.DIAMOND_SWORD, Items.TRIDENT, Items.CROSSBOW, Items.NETHERITE_SWORD};
            item = highPool[random.nextInt(highPool.length)];
        } else {
            Item[] normalPool = {Items.IRON_SWORD, Items.BOW, Items.CROSSBOW, Items.SHIELD, Items.IRON_AXE};
            item = normalPool[random.nextInt(normalPool.length)];
        }

        ItemStack stack = new ItemStack(item);
        maybeEnchantWeapon(level, random, stack, highTier);
        return stack;
    }

    private static void maybeEnchantWeapon(ServerLevel level, RandomSource random, ItemStack weapon, boolean highTier) {
        if (random.nextDouble() > (highTier ? 0.80D : 0.45D)) {
            return;
        }
        HolderGetter<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ResourceKey<Enchantment> key;
        if (weapon.is(ItemTags.SWORDS) || weapon.is(Items.IRON_AXE) || weapon.is(Items.NETHERITE_SWORD)) {
            key = random.nextBoolean() ? Enchantments.SHARPNESS : Enchantments.UNBREAKING;
        } else if (weapon.is(Items.BOW)) {
            key = random.nextBoolean() ? Enchantments.POWER : Enchantments.PUNCH;
        } else if (weapon.is(Items.CROSSBOW)) {
            key = random.nextBoolean() ? Enchantments.QUICK_CHARGE : Enchantments.MULTISHOT;
        } else if (weapon.is(Items.TRIDENT)) {
            key = random.nextBoolean() ? Enchantments.LOYALTY : Enchantments.IMPALING;
        } else {
            key = Enchantments.UNBREAKING;
        }
        Holder<Enchantment> enchantment = registry.getOrThrow(key);
        int desired = highTier ? 3 + random.nextInt(3) : 1 + random.nextInt(2);
        int levelValue = Math.min(desired, enchantment.value().getMaxLevel());
        weapon.enchant(enchantment, levelValue);
    }

    private static ExploreResult createExploreRewards(EntityMaid maid, ExploreDifficulty difficulty) {
        RandomSource random = maid.getRandom();
        double favorRate = favorRate(maid);

        double emptyChance = Math.max(0.03D, difficulty.emptyChance - favorRate * 0.08D);
        double injuryChance = Math.min(0.75D, difficulty.injuryChance + (1.0D - favorRate) * 0.05D);
        boolean empty = random.nextDouble() < emptyChance;
        boolean injured = random.nextDouble() < injuryChance;

        List<ItemStack> rewards = new ArrayList<>();
        if (!empty) {
            int rolls = difficulty == ExploreDifficulty.NEAR
                    ? 2
                    : Math.max(3, difficulty.baseRolls + (maid.getFavorability() >= 120 ? 1 : 0) + (maid.getFavorability() >= 220 ? 1 : 0));
            for (int i = 0; i < rolls; i++) {
                double rareChance = difficulty.rareChance + favorRate * difficulty.favorRareBonus;
                boolean rareRoll = random.nextDouble() < rareChance;
                rewards.add(rollExploreReward(random, difficulty, rareRoll));
            }
        }

        double hurtRatio = switch (difficulty) {
            case NEAR -> 0.08D + random.nextDouble() * 0.08D;
            case RUINS -> 0.12D + random.nextDouble() * 0.12D;
            case ABYSS -> 0.16D + random.nextDouble() * 0.16D;
        };
        return new ExploreResult(rewards, empty, injured, hurtRatio);
    }

    private static ItemStack rollExploreReward(RandomSource random, ExploreDifficulty difficulty, boolean rareRoll) {
        RewardEntry entry = rareRoll
                ? difficulty.rarePool.get(random.nextInt(difficulty.rarePool.size()))
                : difficulty.commonPool.get(random.nextInt(difficulty.commonPool.size()));
        int count = entry.min + (entry.max > entry.min ? random.nextInt(entry.max - entry.min + 1) : 0);
        return new ItemStack(entry.item, Math.max(1, count));
    }

    private static void deliverRewards(EntityMaid maid, ServerPlayer owner, List<ItemStack> rewards) {
        var backpack = maid.getAvailableBackpackInv();
        for (ItemStack reward : rewards) {
            if (reward.isEmpty()) {
                continue;
            }
            ItemStack generatedReward = reward.copy();
            markAsGeneratedReward(generatedReward);
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, generatedReward, false);
            if (!remaining.isEmpty() && owner != null && owner.getInventory().add(remaining)) {
                continue;
            }
            if (!remaining.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5D, maid.getZ(), remaining);
                maid.level().addFreshEntity(itemEntity);
            }
        }
    }

    private static void ensureDefaultFavorability(EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        if (!tag.getBoolean(TAG_FAVOR_INITIALIZED)) {
            maid.setFavorability(FAVOR_DEFAULT);
            tag.putBoolean(TAG_FAVOR_INITIALIZED, true);
        }
    }

    /**
     * Soul recall may deserialize child maids as base maid entities.
     * If a maid is marked as "still child", rebuild it back to MaidChildEntity.
     */
    private static boolean tryRestoreChildMaidEntity(ServerLevel level, EntityMaid maid) {
        if (maid instanceof MaidChildEntity || !MaidChildEntity.shouldStayChild(maid)) {
            return false;
        }
        MaidChildEntity restored = ModEntities.MAID_CHILD.get().create(level);
        if (restored == null) {
            return false;
        }

        CompoundTag savedData = maid.saveWithoutId(new CompoundTag());
        savedData.remove("UUID");
        savedData.remove("UUIDMost");
        savedData.remove("UUIDLeast");
        restored.load(savedData);
        restored.setPersistenceRequired();

        if (!level.addFreshEntity(restored)) {
            return false;
        }
        maid.discard();
        return true;
    }

    private static boolean isBornMaid(EntityMaid maid) {
        return maid instanceof MaidChildEntity
                || maid.getType() == ModEntities.MAID_CHILD.get()
                || maid.getTags().contains(MaidChildEntity.BORN_MAID_TAG)
                || MaidChildEntity.shouldStayChild(maid);
    }

    private static int clampFavorability(int favorability) {
        return Math.max(0, Math.min(FAVOR_MAX, favorability));
    }

    private static double favorRate(EntityMaid maid) {
        return Math.max(0.0D, Math.min(1.0D, maid.getFavorability() / (double) FAVOR_MAX));
    }

    private static boolean canStartActionNow(ServerLevel level, EntityMaid maid) {
        if (!canProgressCurrentAction(maid)) {
            return false;
        }
        return maid.getScheduleDetail() == Activity.WORK;
    }

    private static boolean canProgressCurrentAction(EntityMaid maid) {
        if (maid.isSleeping()) {
            return false;
        }
        return !maid.isInSittingPose();
    }

    private static void freezeActionProgress(CompoundTag tag, long now) {
        if (tag.contains(TAG_ACTION_END)) {
            long currentEnd = tag.getLong(TAG_ACTION_END);
            // Pause by extending end time one second per blocked tick,
            // preserving remaining duration after maid stands up again.
            tag.putLong(TAG_ACTION_END, Math.max(currentEnd, now) + 20L);
        }
        tag.remove(TAG_LAST_COUNTDOWN_SECOND);
    }

    private static boolean isPlayerInputMaterial(ItemStack stack, Predicate<ItemStack> matcher) {
        return !stack.isEmpty() && matcher.test(stack) && !isGeneratedReward(stack);
    }

    private static boolean isGeneratedReward(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        return data.copyTag().getBoolean(TAG_GENERATED_REWARD);
    }

    private static void markAsGeneratedReward(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TAG_GENERATED_REWARD, true));
    }

    private static int calculateActionDurationTicks(int baseDurationTicks, int currentFavorability) {
        double favorCoefficient = Math.max(MIN_FAVOR_DURATION_COEFFICIENT, currentFavorability / FAVOR_DURATION_BASE);
        double scaledDuration = baseDurationTicks * GLOBAL_DURATION_SCALE / favorCoefficient;
        return Math.max(MIN_ACTION_DURATION_TICKS, (int) Math.round(scaledDuration));
    }

    private record RewardEntry(Item item, int min, int max) {
    }

    private record ExploreResult(List<ItemStack> rewards, boolean emptyHanded, boolean injured, double hurtRatio) {
    }

    private enum ActionType {
        LEARN,
        EXPLORE
    }

    private enum LearnType {
        ENCHANTMENT("enchantment"),
        ALCHEMY("alchemy"),
        TACTICS("tactics");

        private final String key;

        LearnType(String key) {
            this.key = key;
        }

        private Component display() {
            return Component.translatable("message.maidmarriage.child.learn.type." + this.key);
        }
    }

    private enum ExploreDifficulty {
        NEAR("near", 3, 0.05D, 0.0D, 0.18D, 0.08D,
                List.of(
                        new RewardEntry(Items.BREAD, 2, 6),
                        new RewardEntry(Items.COOKED_BEEF, 1, 4),
                        new RewardEntry(Items.COOKED_CHICKEN, 1, 4),
                        new RewardEntry(Items.COOKED_SALMON, 1, 4),
                        new RewardEntry(Items.APPLE, 2, 5),
                        new RewardEntry(Items.WHEAT, 2, 6),
                        new RewardEntry(Items.WHEAT_SEEDS, 3, 10),
                        new RewardEntry(Items.BEETROOT_SEEDS, 2, 8),
                        new RewardEntry(Items.MELON_SEEDS, 2, 6),
                        new RewardEntry(Items.PUMPKIN_SEEDS, 2, 6),
                        new RewardEntry(Items.POPPY, 1, 4),
                        new RewardEntry(Items.DANDELION, 1, 4),
                        new RewardEntry(Items.CORNFLOWER, 1, 3),
                        new RewardEntry(Items.BLUE_ORCHID, 1, 3),
                        new RewardEntry(Items.ALLIUM, 1, 2),
                        new RewardEntry(Items.OXEYE_DAISY, 1, 3),
                        new RewardEntry(Items.WHITE_TULIP, 1, 3),
                        new RewardEntry(Items.RED_TULIP, 1, 3),
                        new RewardEntry(Items.PINK_TULIP, 1, 3),
                        new RewardEntry(Items.ORANGE_TULIP, 1, 3),
                        new RewardEntry(Items.AZURE_BLUET, 1, 3),
                        new RewardEntry(Items.LILAC, 1, 2),
                        new RewardEntry(Items.ROSE_BUSH, 1, 2),
                        new RewardEntry(Items.PEONY, 1, 2),
                        new RewardEntry(Items.SHORT_GRASS, 2, 6),
                        new RewardEntry(Items.FERN, 1, 4),
                        new RewardEntry(Items.SUGAR_CANE, 2, 6),
                        new RewardEntry(Items.BAMBOO, 2, 6),
                        new RewardEntry(Items.VINE, 2, 6),
                        new RewardEntry(Items.CACTUS, 1, 4),
                        new RewardEntry(Items.SWEET_BERRIES, 2, 6),
                        new RewardEntry(Items.GLOW_BERRIES, 2, 5),
                        new RewardEntry(Items.GOLDEN_CARROT, 1, 2),
                        new RewardEntry(Items.COAL, 6, 12),
                        new RewardEntry(Items.IRON_INGOT, 2, 5),
                        new RewardEntry(Items.LAPIS_LAZULI, 3, 8),
                        new RewardEntry(Items.REDSTONE, 4, 10)
                ),
                List.of(
                        new RewardEntry(Items.DIAMOND, 1, 2),
                        new RewardEntry(Items.EMERALD, 1, 2),
                        new RewardEntry(Items.GOLD_INGOT, 2, 4)
                )),
        RUINS("ruins", 4, 0.22D, 0.22D, 0.15D, 0.15D,
                List.of(
                        new RewardEntry(Items.LAPIS_LAZULI, 8, 16),
                        new RewardEntry(Items.GOLD_INGOT, 2, 6),
                        new RewardEntry(Items.DIAMOND, 1, 3),
                        new RewardEntry(Items.ENDER_PEARL, 1, 3),
                        new RewardEntry(Items.ANCIENT_DEBRIS, 1, 1),
                        new RewardEntry(Items.NETHERITE_SCRAP, 1, 2)
                ),
                List.of(
                        new RewardEntry(Items.HEAVY_CORE, 1, 1),
                        new RewardEntry(Items.HEART_OF_THE_SEA, 1, 1),
                        new RewardEntry(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 1),
                        new RewardEntry(Items.TOTEM_OF_UNDYING, 1, 1),
                        new RewardEntry(Items.ENCHANTED_GOLDEN_APPLE, 1, 1),
                        new RewardEntry(Items.ELYTRA, 1, 1),
                        new RewardEntry(Items.NETHER_STAR, 1, 1)
                )),
        ABYSS("abyss", 5, 0.22D, 0.22D, 0.12D, 0.22D,
                List.of(
                        new RewardEntry(Items.DIAMOND, 2, 5),
                        new RewardEntry(Items.LAPIS_LAZULI, 10, 20),
                        new RewardEntry(Items.ANCIENT_DEBRIS, 1, 2),
                        new RewardEntry(Items.NETHERITE_SCRAP, 1, 2)
                ),
                List.of(
                        new RewardEntry(Items.HEAVY_CORE, 1, 1),
                        new RewardEntry(Items.HEART_OF_THE_SEA, 1, 1),
                        new RewardEntry(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 1),
                        new RewardEntry(Items.TOTEM_OF_UNDYING, 1, 1),
                        new RewardEntry(Items.ENCHANTED_GOLDEN_APPLE, 1, 1),
                        new RewardEntry(Items.ELYTRA, 1, 1),
                        new RewardEntry(Items.NETHER_STAR, 1, 1)
                ));

        private final String key;
        private final int baseRolls;
        private final double rareChance;
        private final double favorRareBonus;
        private final double emptyChance;
        private final double injuryChance;
        private final List<RewardEntry> commonPool;
        private final List<RewardEntry> rarePool;

        ExploreDifficulty(String key, int baseRolls, double rareChance, double favorRareBonus, double emptyChance, double injuryChance,
                          List<RewardEntry> commonPool, List<RewardEntry> rarePool) {
            this.key = key;
            this.baseRolls = baseRolls;
            this.rareChance = rareChance;
            this.favorRareBonus = favorRareBonus;
            this.emptyChance = emptyChance;
            this.injuryChance = injuryChance;
            this.commonPool = commonPool;
            this.rarePool = rarePool;
        }

        private Component display() {
            return Component.translatable("message.maidmarriage.child.explore.type." + this.key);
        }
    }

    private enum WorkMode {
        STUDY_ENCHANTMENT("child_study_enchantment", Items.BOOK, LEARN_DURATION_TICKS, ActionType.LEARN,
                stack -> stack.is(Items.BOOK), LearnType.ENCHANTMENT, null, "message.maidmarriage.child.requirement.book", false),
        STUDY_ALCHEMY("child_study_alchemy", Items.GLASS_BOTTLE, LEARN_DURATION_TICKS, ActionType.LEARN,
                stack -> stack.is(Items.GLASS_BOTTLE) || stack.is(Items.POTION), LearnType.ALCHEMY, null,
                "message.maidmarriage.child.requirement.alchemy", true),
        STUDY_TACTICS("child_study_tactics", Items.IRON_SWORD, LEARN_DURATION_TICKS, ActionType.LEARN,
                stack -> stack.is(ItemTags.SWORDS)
                        || stack.is(ItemTags.AXES)
                        || stack.is(Items.BOW)
                        || stack.is(Items.CROSSBOW)
                        || stack.is(Items.TRIDENT), LearnType.TACTICS, null, "message.maidmarriage.child.requirement.weapon", true),
        EXPLORE_NEAR("child_explore_near", Items.STICK, EXPLORE_NEAR_DURATION_TICKS, ActionType.EXPLORE,
                stack -> stack.is(Items.STICK), null, ExploreDifficulty.NEAR, "message.maidmarriage.child.requirement.stick", false),
        EXPLORE_RUINS("child_explore_ruins", Items.MAP, EXPLORE_RUINS_DURATION_TICKS, ActionType.EXPLORE,
                stack -> stack.is(Items.MAP), null, ExploreDifficulty.RUINS, "message.maidmarriage.child.requirement.map", false),
        EXPLORE_ABYSS("child_explore_abyss", Items.ENDER_EYE, EXPLORE_ABYSS_DURATION_TICKS, ActionType.EXPLORE,
                stack -> stack.is(Items.ENDER_EYE), null, ExploreDifficulty.ABYSS, "message.maidmarriage.child.requirement.ender_eye", false);

        private static final Map<ResourceLocation, WorkMode> TASK_MAP = Map.of(
                id(STUDY_ENCHANTMENT.key), STUDY_ENCHANTMENT,
                id(STUDY_ALCHEMY.key), STUDY_ALCHEMY,
                id(STUDY_TACTICS.key), STUDY_TACTICS,
                id(EXPLORE_NEAR.key), EXPLORE_NEAR,
                id(EXPLORE_RUINS.key), EXPLORE_RUINS,
                id(EXPLORE_ABYSS.key), EXPLORE_ABYSS
        );

        private final String key;
        private final Item icon;
        private final int durationTicks;
        private final ActionType actionType;
        private final Predicate<ItemStack> inputMatcher;
        private final LearnType learnType;
        private final ExploreDifficulty exploreDifficulty;
        private final String requirementKey;
        private final boolean allowBackpackForUnstackable;

        WorkMode(String key, Item icon, int durationTicks, ActionType actionType,
                 Predicate<ItemStack> inputMatcher, LearnType learnType, ExploreDifficulty exploreDifficulty,
                 String requirementKey, boolean allowBackpackForUnstackable) {
            this.key = key;
            this.icon = icon;
            this.durationTicks = durationTicks;
            this.actionType = actionType;
            this.inputMatcher = inputMatcher;
            this.learnType = learnType;
            this.exploreDifficulty = exploreDifficulty;
            this.requirementKey = requirementKey;
            this.allowBackpackForUnstackable = allowBackpackForUnstackable;
        }

        private ResourceLocation uid() {
            return id(this.key);
        }

        private static ResourceLocation id(String path) {
            return ResourceLocation.fromNamespaceAndPath(MaidMarriageMod.MOD_ID, path);
        }

        private static Optional<WorkMode> fromTask(IMaidTask task) {
            return Optional.ofNullable(TASK_MAP.get(task.getUid()));
        }

        private static Optional<WorkMode> fromKey(String key) {
            for (WorkMode mode : values()) {
                if (mode.key.equals(key)) {
                    return Optional.of(mode);
                }
            }
            return Optional.empty();
        }

        private Component display() {
            return actionType == ActionType.LEARN ? learnType.display() : exploreDifficulty.display();
        }

        private Component requirementDisplay() {
            return Component.translatable(requirementKey);
        }
    }

    private static final class ChildWorkTask implements IMaidTask {
        private final WorkMode mode;

        private ChildWorkTask(WorkMode mode) {
            this.mode = mode;
        }

        @Override
        public ResourceLocation getUid() {
            return mode.uid();
        }

        @Override
        public ItemStack getIcon() {
            return new ItemStack(mode.icon);
        }

        @Override
        public net.minecraft.sounds.SoundEvent getAmbientSound(EntityMaid maid) {
            return null;
        }

        @Override
        public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
            return Collections.emptyList();
        }

        @Override
        public boolean isEnable(EntityMaid maid) {
            return isBornMaid(maid);
        }

        @Override
        public boolean isHidden(EntityMaid maid) {
            return !isBornMaid(maid);
        }

        @Override
        public List<String> getDescription(EntityMaid maid) {
            return List.of(String.format("task.%s.%s.desc", MaidMarriageMod.MOD_ID, mode.key));
        }
    }
}
