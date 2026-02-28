package com.example.maidmarriage.entity;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 子代女仆实体：负责成长阶段、父母信息与外观继承。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public class MaidChildEntity extends EntityMaid {
    // 三天成长为成年（1 天 = 24000 tick）
    public static final int ADULT_AFTER_TICKS = 3 * 24000;
    public static final int MIDDLE_STAGE_TICKS = ADULT_AFTER_TICKS / 2;
    public static final String BORN_MAID_TAG = "maidmarriage_born_maid";
    public static final String PERSISTENT_MOTHER_UUID_KEY = "maidmarriage_mother_uuid";
    public static final String PERSISTENT_FATHER_UUID_KEY = "maidmarriage_father_uuid";
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
        this.growthTicks++;
        updateGrowthStage();
        if (this.growthTicks >= ADULT_AFTER_TICKS) {
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
            adult.setYsmModel(this.getYsmModelId(), this.getYsmModelTexture(), this.getYsmModelName());
        } else {
            adult.setIsYsmModel(false);
            adult.setModelId(this.getModelId());
        }
        applyBornMaidTraits(adult);
        writeParentData(adult, this.motherUuid, this.fatherUuid);
        serverLevel.addFreshEntity(adult);
        this.discard();
    }

    private void updateGrowthStage() {
        if (this.growthTicks < MIDDLE_STAGE_TICKS) {
            this.stage = GrowthStage.INFANT;
            return;
        }
        if (this.growthTicks < ADULT_AFTER_TICKS) {
            if (this.stage != GrowthStage.MIDDLE) {
                this.stage = GrowthStage.MIDDLE;
                if (this.getOwner() instanceof Player owner) {
                    owner.sendSystemMessage(Component.translatable("message.maidmarriage.child.growth.middle"));
                }
            }
            return;
        }
        this.stage = GrowthStage.ADULT;
    }

    private static void applyBornMaidTraits(EntityMaid maid) {
        maid.addTag(BORN_MAID_TAG);
        maid.setFavorability(300);
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
