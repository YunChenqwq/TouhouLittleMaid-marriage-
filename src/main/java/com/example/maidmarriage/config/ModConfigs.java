package com.example.maidmarriage.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置定义：后宫模式、结婚好感度与怀孕概率。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ModConfigs {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue HAREM_MODE;
    private static final ModConfigSpec.IntValue REQUIRED_FAVORABILITY;
    private static final ModConfigSpec.DoubleValue PREGNANCY_CHANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("女仆婚姻模组通用设置。")
                .translation("config.maidmarriage.general")
                .push("general");
        HAREM_MODE = builder
                .comment("是否允许一名玩家与多位女仆结婚。")
                .translation("config.maidmarriage.harem_mode")
                .define("haremMode", false);
        REQUIRED_FAVORABILITY = builder
                .comment("结婚所需好感度阈值。")
                .translation("config.maidmarriage.required_favorability")
                .defineInRange("requiredFavorability", 50, 0, 10000);
        PREGNANCY_CHANCE = builder
                .comment("每次成功同寝后的怀孕概率，范围 0.0~1.0。")
                .translation("config.maidmarriage.pregnancy_chance")
                .defineInRange("pregnancyChance", 0.6D, 0.0D, 1.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private ModConfigs() {
    }

    public static boolean haremMode() {
        return HAREM_MODE.get();
    }

    public static int requiredFavorability() {
        return REQUIRED_FAVORABILITY.get();
    }

    public static double pregnancyChance() {
        return PREGNANCY_CHANCE.get();
    }
}

