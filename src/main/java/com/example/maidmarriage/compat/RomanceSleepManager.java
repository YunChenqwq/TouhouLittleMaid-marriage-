package com.example.maidmarriage.compat;

import com.example.maidmarriage.advancement.ModAdvancements;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModEntities;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 同眠与生育流程管理：处理剧情、心情循环、怀孕与分娩。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class RomanceSleepManager {
    private static final int SCENE_INTERVAL_TICKS = 24;
    private static final int SINGLE_LINE_BUBBLE_TICKS = 100;
    private static final int MULTI_LINE_BUBBLE_TICKS = 60;
    private static final int MULTI_LINE_GAP_TICKS = 30;
    private static final int MULTI_LINE_STEP_TICKS = MULTI_LINE_BUBBLE_TICKS + MULTI_LINE_GAP_TICKS;
    private static final long LONGING_SADDLE_COOLDOWN_TICKS = 200L;
    private static final double LONGING_HEART_RANGE_SQR = 25.0D;
    private static final String TAG_ROMANCE_COUNT = "maidmarriage_romance_count";
    private static final String TAG_LONGING_NEXT_TALK_TICK = "maidmarriage_longing_next_talk_tick";
    private static final String TAG_LONGING_LINE_INDEX = "maidmarriage_longing_line_index";
    private static final String TAG_LONGING_SADDLE_NEXT_TALK_TICK = "maidmarriage_longing_saddle_next_talk_tick";
    /**
     * 第一次同寝专属文案，强调“初次体验”的仪式感。
     */
    private static final List<String> FIRST_SCENE_LINES = List.of(
            "scene.maidmarriage.sleep.1",
            "scene.maidmarriage.sleep.2",
            "scene.maidmarriage.sleep.3",
            "scene.maidmarriage.sleep.4"
    );
    /**
     * 非首次时随机抽取的剧情组：温柔/热烈/日常甜蜜三种风格。
     */
    private static final List<List<String>> RANDOM_SCENE_VARIANTS = List.of(
            List.of(
                    "scene.maidmarriage.sleep.gentle.1",
                    "scene.maidmarriage.sleep.gentle.2",
                    "scene.maidmarriage.sleep.gentle.3",
                    "scene.maidmarriage.sleep.gentle.4"
            ),
            List.of(
                    "scene.maidmarriage.sleep.passion.1",
                    "scene.maidmarriage.sleep.passion.2",
                    "scene.maidmarriage.sleep.passion.3",
                    "scene.maidmarriage.sleep.passion.4"
            ),
            List.of(
                    "scene.maidmarriage.sleep.sweet.1",
                    "scene.maidmarriage.sleep.sweet.2",
                    "scene.maidmarriage.sleep.sweet.3",
                    "scene.maidmarriage.sleep.sweet.4"
            )
    );
    private static final List<String> PROPOSAL_LINES = List.of(
            "dialogue.maidmarriage.proposal.1",
            "dialogue.maidmarriage.proposal.2",
            "dialogue.maidmarriage.proposal.3"
    );
    private static final List<String> LONGING_LOOP_LINES = List.of(
            "dialogue.maidmarriage.longing.loop1",
            "dialogue.maidmarriage.longing.loop2",
            "dialogue.maidmarriage.longing.loop3"
    );
    private static final Map<UUID, RomanceSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, ProposalDialogueSession> PROPOSAL_DIALOGUES = new ConcurrentHashMap<>();

    private RomanceSleepManager() {
    }

    public static boolean tryStartRomanceSleep(ServerPlayer player, EntityMaid maid) {
        if (!maid.isSleeping()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_sleeping_maid"));
            return false;
        }

        Optional<BlockPos> maidBedPos = maid.getSleepingPos();
        if (maidBedPos.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.cannot_find_maid_bed"));
            return false;
        }

        Optional<BlockPos> playerBedPos = findAdjacentPlayerBed(player.serverLevel(), player.blockPosition(), maidBedPos.get());
        if (playerBedPos.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_adjacent_bed"));
            return false;
        }

        PregnancyData pregnancy = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        if (pregnancy.pregnant() && !pregnancy.isPregnantWith(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.pregnant_with_other"));
            return false;
        }

        player.lookAt(EntityAnchorArgument.Anchor.EYES, maid.position().add(0, 0.3, 0));
        var sleepResult = player.startSleepInBed(playerBedPos.get());
        if (sleepResult.left().isPresent()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.sleep_failed"));
            return false;
        }

        RomanceSession session = new RomanceSession(maid.getUUID(), pickSceneLines(player, maid));
        SESSIONS.put(player.getUUID(), session);
        sendNextSceneLine(player, session);
        return true;
    }

    public static void startProposalDialogue(ServerPlayer player, EntityMaid maid) {
        ProposalDialogueSession session = new ProposalDialogueSession(
                maid.getUUID(),
                player.serverLevel().getGameTime());
        PROPOSAL_DIALOGUES.put(player.getUUID(), session);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        tickProposalDialogue(player);

        RomanceSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        if (!player.isSleeping()) {
            finishSession(player, session);
            SESSIONS.remove(player.getUUID());
            return;
        }

        session.sleepTicks++;
        if (session.sleepTicks % SCENE_INTERVAL_TICKS == 0) {
            sendNextSceneLine(player, session);
        }
    }

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide() || maid.tickCount % 20 != 0) {
            return;
        }
        MarriageData marriage = maid.getData(ModTaskData.MARRIAGE_DATA);
        if (marriage == null || !marriage.married()) {
            return;
        }
        PregnancyData pregnancy = maid.getData(ModTaskData.PREGNANCY_DATA);
        if (pregnancy == null
                || pregnancy.currentMood(maid.level().getGameTime()).orElse(null) != PregnancyData.MoodState.LONGING) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(maid.getOwner() instanceof ServerPlayer owner) || owner.level() != maid.level()) {
            return;
        }
        double distanceSqr = maid.distanceToSqr(owner);
        boolean saddleTriggered = tryTriggerSaddleDialogue(level, maid, owner);

        if (distanceSqr <= LONGING_HEART_RANGE_SQR) {
            level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.0D), maid.getZ(),
                    2, 0.25D, 0.15D, 0.25D, 0.01D);
            if (!saddleTriggered) {
                tryTriggerLongingLoopDialogue(level, maid);
            }
        } else {
            resetLongingLoopDialogue(maid);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        SESSIONS.remove(player.getUUID());
        PROPOSAL_DIALOGUES.remove(player.getUUID());
    }

    private static void finishSession(ServerPlayer player, RomanceSession session) {
        if (!session.sceneFinished) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.scene_interrupted"));
            return;
        }

        Entity maidEntity = player.serverLevel().getEntity(session.maidUuid);
        if (!(maidEntity instanceof EntityMaid maid) || !maid.isAlive()) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.mother_missing"));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.need_owner"));
            return;
        }
        int romanceCount = increaseRomanceCount(player);
        if (romanceCount == 1) {
            ModAdvancements.grantFirstRomance(player);
        }
        if (romanceCount >= 10) {
            ModAdvancements.grantRomanceTen(player);
        }

        PregnancyData current = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        long gameTime = maid.level().getGameTime();
        PregnancyData updated = current.markRomance(gameTime);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, updated);
        speakSingleLine(maid, "dialogue.maidmarriage.after_romance");

        if (!updated.pregnant()) {
            if (maid.getRandom().nextDouble() >= ModConfigs.pregnancyChance()) {
                player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.no_conception"));
                return;
            }
            maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, updated.conceive(player.getUUID(), gameTime));
            maid.level().playSound(null, maid.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.75F, 1.2F);
            if (maid.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1), maid.getZ(),
                        10, 0.3, 0.2, 0.3, 0.02);
            }
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.pregnant_success"));
            return;
        }

        if (!updated.isPregnantWith(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.pregnant_with_other"));
            return;
        }

        if (!spawnChild(player, maid)) {
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.spawn_fail"));
            return;
        }
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, updated.completeBirth());
        player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.birth_success"));
        ModAdvancements.grantChildbirth(player);
    }

    private static boolean spawnChild(ServerPlayer player, EntityMaid mother) {
        if (!(mother.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        MaidChildEntity child = ModEntities.MAID_CHILD.get().create(serverLevel);
        if (child == null) {
            return false;
        }

        double spawnX = mother.getX();
        double spawnY = mother.getY();
        double spawnZ = mother.getZ();

        child.moveTo(spawnX, spawnY, spawnZ, player.getYRot(), 0);
        child.tame(player);
        child.setParents(mother.getUUID(), player.getUUID());
        child.applyBornMaidTraits();
        child.inheritModelFromMother(mother);
        child.setCustomName(readPlannedChildName(player));

        boolean success = serverLevel.addFreshEntity(child);
        if (success) {
            serverLevel.sendParticles(ParticleTypes.HEART, spawnX, spawnY + 0.75, spawnZ, 16, 0.4, 0.25, 0.4, 0.02);
            serverLevel.playSound(null, mother.blockPosition(), net.minecraft.sounds.SoundEvents.CHICKEN_EGG,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.1F);
        }
        return success;
    }

    private static Component readPlannedChildName(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.NAME_TAG) && offhand.has(DataComponents.CUSTOM_NAME)) {
            Component name = offhand.getHoverName().copy();
            if (!player.getAbilities().instabuild) {
                offhand.shrink(1);
            }
            player.sendSystemMessage(Component.translatable("message.maidmarriage.breed.use_name_tag", name));
            return name;
        }
        return Component.translatable("entity.maidmarriage.maid_child");
    }

    private static int increaseRomanceCount(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int count = data.getInt(TAG_ROMANCE_COUNT) + 1;
        data.putInt(TAG_ROMANCE_COUNT, count);
        return count;
    }

    private static void tryTriggerLongingLoopDialogue(ServerLevel level, EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        long now = level.getGameTime();
        long nextTalkTick = data.getLong(TAG_LONGING_NEXT_TALK_TICK);
        if (nextTalkTick > now) {
            return;
        }
        int index = data.getInt(TAG_LONGING_LINE_INDEX);
        speakMultiLine(maid, LONGING_LOOP_LINES.get(index));
        data.putInt(TAG_LONGING_LINE_INDEX, (index + 1) % LONGING_LOOP_LINES.size());
        data.putLong(TAG_LONGING_NEXT_TALK_TICK, now + MULTI_LINE_STEP_TICKS);
    }

    private static void resetLongingLoopDialogue(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        data.remove(TAG_LONGING_NEXT_TALK_TICK);
        data.remove(TAG_LONGING_LINE_INDEX);
    }

    private static boolean tryTriggerSaddleDialogue(ServerLevel level, EntityMaid maid, ServerPlayer owner) {
        if (!maid.isPassenger() || maid.getVehicle() != owner) {
            return false;
        }
        long now = level.getGameTime();
        CompoundTag data = maid.getPersistentData();
        if (data.getLong(TAG_LONGING_SADDLE_NEXT_TALK_TICK) > now) {
            return false;
        }
        speakSingleLine(maid, "dialogue.maidmarriage.longing_saddle");
        level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.0D), maid.getZ(),
                6, 0.3D, 0.15D, 0.3D, 0.01D);
        data.putLong(TAG_LONGING_SADDLE_NEXT_TALK_TICK, now + LONGING_SADDLE_COOLDOWN_TICKS);
        return true;
    }

    private static void tickProposalDialogue(ServerPlayer player) {
        ProposalDialogueSession session = PROPOSAL_DIALOGUES.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (player.serverLevel().getGameTime() < session.nextSpeakTick) {
            return;
        }
        Entity maidEntity = player.serverLevel().getEntity(session.maidUuid);
        if (!(maidEntity instanceof EntityMaid maid) || !maid.isAlive()) {
            PROPOSAL_DIALOGUES.remove(player.getUUID());
            return;
        }
        if (session.lineIndex >= PROPOSAL_LINES.size()) {
            PROPOSAL_DIALOGUES.remove(player.getUUID());
            return;
        }

        speakMultiLine(maid, PROPOSAL_LINES.get(session.lineIndex));
        session.lineIndex++;
        session.nextSpeakTick = player.serverLevel().getGameTime() + MULTI_LINE_STEP_TICKS;

        if (session.lineIndex >= PROPOSAL_LINES.size()) {
            PROPOSAL_DIALOGUES.remove(player.getUUID());
        }
    }

    private static void sendNextSceneLine(ServerPlayer player, RomanceSession session) {
        if (session.lineIndex >= session.sceneLines.size()) {
            session.sceneFinished = true;
            return;
        }
        player.displayClientMessage(Component.translatable(session.sceneLines.get(session.lineIndex)), true);
        session.lineIndex++;
        if (session.lineIndex >= session.sceneLines.size()) {
            session.sceneFinished = true;
        }
    }

    /**
     * 根据玩家是否“首次同寝”选择剧情：
     * - 首次：固定播放首次剧情；
     * - 非首次：在三组随机剧情中抽一组。
     */
    private static List<String> pickSceneLines(ServerPlayer player, EntityMaid maid) {
        if (player.getPersistentData().getInt(TAG_ROMANCE_COUNT) <= 0) {
            return FIRST_SCENE_LINES;
        }
        return RANDOM_SCENE_VARIANTS.get(maid.getRandom().nextInt(RANDOM_SCENE_VARIANTS.size()));
    }

    private static Optional<BlockPos> findAdjacentPlayerBed(ServerLevel level, BlockPos playerPos, BlockPos maidBedPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos bedPos = maidBedPos.relative(direction);
            BlockState state = level.getBlockState(bedPos);
            if (isPlayerBed(state) && playerPos.distManhattan(bedPos) <= 3) {
                return Optional.of(bedPos);
            }
        }
        return Optional.empty();
    }

    private static boolean isPlayerBed(BlockState state) {
        return state.getBlock() instanceof BedBlock && !(state.getBlock() instanceof BlockMaidBed);
    }

    public static void speakSingleLine(EntityMaid maid, String langKey) {
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(
                SINGLE_LINE_BUBBLE_TICKS,
                Component.translatable(langKey),
                IChatBubbleData.TYPE_2,
                IChatBubbleData.DEFAULT_PRIORITY));
    }

    private static void speakMultiLine(EntityMaid maid, String langKey) {
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(
                MULTI_LINE_BUBBLE_TICKS,
                Component.translatable(langKey),
                IChatBubbleData.TYPE_2,
                IChatBubbleData.DEFAULT_PRIORITY));
    }

    private static final class RomanceSession {
        private final UUID maidUuid;
        private final List<String> sceneLines;
        private int sleepTicks = 0;
        private int lineIndex = 0;
        private boolean sceneFinished = false;

        private RomanceSession(UUID maidUuid, List<String> sceneLines) {
            this.maidUuid = maidUuid;
            this.sceneLines = sceneLines;
        }
    }

    private static final class ProposalDialogueSession {
        private final UUID maidUuid;
        private long nextSpeakTick;
        private int lineIndex = 0;

        private ProposalDialogueSession(UUID maidUuid, long nextSpeakTick) {
            this.maidUuid = maidUuid;
            this.nextSpeakTick = nextSpeakTick;
        }
    }
}
