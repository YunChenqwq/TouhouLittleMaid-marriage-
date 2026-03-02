package com.example.maidmarriage.entity;

import com.example.maidmarriage.config.ModConfigs;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Child maid entity with growth stages and parent/model inheritance.
 */
public class MaidChildEntity extends EntityMaid {
    private static final int DAY_TICKS = 24000;

    public static final String BORN_MAID_TAG = "maidmarriage_born_maid";
    public static final String PERSISTENT_MOTHER_UUID_KEY = "maidmarriage_mother_uuid";
    public static final String PERSISTENT_FATHER_UUID_KEY = "maidmarriage_father_uuid";
    public static final String PERSISTENT_CHILD_ACTIVE_KEY = "maidmarriage_child_active";
    public static final String PERSISTENT_GROWTH_TICKS_KEY = "maidmarriage_child_growth_ticks";
    public static final String PERSISTENT_GROWTH_STAGE_KEY = "maidmarriage_child_growth_stage";
    public static final String PERSISTENT_TAME_INITIALIZED_KEY = "maidmarriage_child_tame_initialized";

    private static final double HEALTH_MULTIPLIER = 1.3D;
    private static final String TAG_GROWTH_TICKS = "GrowthTicks";
    private static final String TAG_STAGE = "GrowthStage";
    private static final String TAG_MOTHER_UUID = "MotherUuid";
    private static final String TAG_FATHER_UUID = "FatherUuid";

    private int growthTicks = 0;
    private UUID motherUuid;
    private UUID fatherUuid;
    private GrowthStage stage = GrowthStage.INFANT;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public MaidChildEntity(EntityType<? extends MaidChildEntity> type, Level level) {
        super((EntityType<EntityMaid>) (EntityType) type, level);
        this.setPersistenceRequired();
    }

    public void setParents(UUID motherUuid, UUID fatherUuid) {
        this.motherUuid = motherUuid;
        this.fatherUuid = fatherUuid;
        writeParentData(this, motherUuid, fatherUuid);
    }

    public void inheritModelFromMother(EntityMaid mother) {
        if (mother.isYsmModel()) {
            String ysmModelId = mother.getYsmModelId();
            String ysmTexture = mother.getYsmModelTexture();
            Component ysmName = mother.getYsmModelName();
            this.setIsYsmModel(true);
            this.setYsmModel(ysmModelId, ysmTexture, ysmName);
            return;
        }
        this.setIsYsmModel(false);
        this.setModelId(mother.getModelId());
    }

    public void applyBornMaidTraits() {
        applyBornMaidTraits(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ensureFullTameState();
        getPersistentData().putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, true);
        this.growthTicks++;
        updateGrowthStage();
        syncPersistentGrowthData();
        if (this.growthTicks >= getAdultAfterTicks()) {
            promoteToAdult();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_GROWTH_TICKS, this.growthTicks);
        if (this.motherUuid != null) {
            tag.putUUID(TAG_MOTHER_UUID, this.motherUuid);
        }
        if (this.fatherUuid != null) {
            tag.putUUID(TAG_FATHER_UUID, this.fatherUuid);
        }
        tag.putString(TAG_STAGE, this.stage.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.growthTicks = tag.getInt(TAG_GROWTH_TICKS);
        if (tag.hasUUID(TAG_MOTHER_UUID)) {
            this.motherUuid = tag.getUUID(TAG_MOTHER_UUID);
        }
        if (tag.hasUUID(TAG_FATHER_UUID)) {
            this.fatherUuid = tag.getUUID(TAG_FATHER_UUID);
        }
        if (tag.contains(TAG_STAGE)) {
            this.stage = GrowthStage.byName(tag.getString(TAG_STAGE));
        }
        CompoundTag persistent = this.getPersistentData();
        if (persistent.contains(PERSISTENT_GROWTH_TICKS_KEY)) {
            this.growthTicks = Math.max(this.growthTicks, persistent.getInt(PERSISTENT_GROWTH_TICKS_KEY));
        }
        if (persistent.contains(PERSISTENT_GROWTH_STAGE_KEY)) {
            this.stage = GrowthStage.byName(persistent.getString(PERSISTENT_GROWTH_STAGE_KEY));
        }
        if (this.motherUuid != null || this.fatherUuid != null) {
            writeParentData(this, this.motherUuid, this.fatherUuid);
        }
    }

    private void promoteToAdult() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        EntityMaid adult = new EntityMaid(serverLevel);
        adult.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        if (this.getOwner() instanceof Player owner) {
            adult.tame(owner);
        }
        if (this.hasCustomName()) {
            adult.setCustomName(this.getCustomName());
        }
        if (this.isYsmModel()) {
            adult.setIsYsmModel(true);
            adult.setYsmModel(this.getYsmModelId(), this.getYsmModelTexture(), this.getYsmModelName());
        } else {
            adult.setIsYsmModel(false);
            adult.setModelId(this.getModelId());
        }
        applyBornMaidTraits(adult);
        writeParentData(adult, this.motherUuid, this.fatherUuid);
        markAsAdult(adult);
        serverLevel.addFreshEntity(adult);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, adult.getX(), adult.getY(1.0D), adult.getZ(),
                10, 0.3D, 0.2D, 0.3D, 0.02D);
        this.discard();
    }

    private void updateGrowthStage() {
        int middleStageTicks = getMiddleStageTicks();
        int adultAfterTicks = getAdultAfterTicks();

        if (this.growthTicks < middleStageTicks) {
            this.stage = GrowthStage.INFANT;
            return;
        }
        if (this.growthTicks < adultAfterTicks) {
            if (this.stage != GrowthStage.MIDDLE) {
                this.stage = GrowthStage.MIDDLE;
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY(1.0D), this.getZ(),
                            8, 0.25D, 0.2D, 0.25D, 0.02D);
                }
                if (this.getOwner() instanceof Player owner) {
                    owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.growth.middle"));
                }
            }
            return;
        }
        this.stage = GrowthStage.ADULT;
    }

    private void syncPersistentGrowthData() {
        CompoundTag persistent = this.getPersistentData();
        persistent.putInt(PERSISTENT_GROWTH_TICKS_KEY, this.growthTicks);
        persistent.putString(PERSISTENT_GROWTH_STAGE_KEY, this.stage.name());
    }

    private void ensureFullTameState() {
        CompoundTag persistent = this.getPersistentData();
        if (persistent.getBoolean(PERSISTENT_TAME_INITIALIZED_KEY)) {
            return;
        }
        if (this.getOwner() instanceof Player owner) {
            this.tame(owner);
            persistent.putBoolean(PERSISTENT_TAME_INITIALIZED_KEY, true);
        }
    }

    private static int getAdultAfterTicks() {
        return Math.max(1, ModConfigs.childGrowthDays()) * DAY_TICKS;
    }

    private static int getMiddleStageTicks() {
        return Math.max(1, getAdultAfterTicks() / 2);
    }

    private static void applyBornMaidTraits(EntityMaid maid) {
        maid.addTag(BORN_MAID_TAG);
        maid.setFavorability(64);
        if (maid instanceof MaidChildEntity) {
            maid.getPersistentData().putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, true);
            maid.getPersistentData().putBoolean(PERSISTENT_TAME_INITIALIZED_KEY, maid.isTame() && maid.getOwnerUUID() != null);
        }
        AttributeInstance maxHealth = maid.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * HEALTH_MULTIPLIER);
            maid.setHealth(maid.getMaxHealth());
        }
    }

    private static void writeParentData(EntityMaid maid, UUID motherUuid, UUID fatherUuid) {
        CompoundTag tag = maid.getPersistentData();
        if (motherUuid != null) {
            tag.putUUID(PERSISTENT_MOTHER_UUID_KEY, motherUuid);
        }
        if (fatherUuid != null) {
            tag.putUUID(PERSISTENT_FATHER_UUID_KEY, fatherUuid);
        }
    }

    public static boolean isChildOfPlayer(EntityMaid maid, UUID playerUuid) {
        CompoundTag tag = maid.getPersistentData();
        return tag.hasUUID(PERSISTENT_FATHER_UUID_KEY) && playerUuid.equals(tag.getUUID(PERSISTENT_FATHER_UUID_KEY));
    }

    public static boolean shouldStayChild(EntityMaid maid) {
        return maid.getPersistentData().getBoolean(PERSISTENT_CHILD_ACTIVE_KEY);
    }

    public static void markAsAdult(EntityMaid maid) {
        CompoundTag persistent = maid.getPersistentData();
        persistent.putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, false);
        persistent.remove(PERSISTENT_GROWTH_TICKS_KEY);
        persistent.remove(PERSISTENT_GROWTH_STAGE_KEY);
        persistent.remove(PERSISTENT_TAME_INITIALIZED_KEY);
    }

    public enum GrowthStage {
        INFANT,
        MIDDLE,
        ADULT;

        public static GrowthStage byName(String name) {
            for (GrowthStage stage : values()) {
                if (stage.name().equals(name)) {
                    return stage;
                }
            }
            return INFANT;
        }
    }
}
