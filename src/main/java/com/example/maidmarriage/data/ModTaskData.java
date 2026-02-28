package com.example.maidmarriage.data;

import com.example.maidmarriage.MaidMarriageMod;
import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import net.minecraft.resources.ResourceLocation;

/**
 * 任务数据键注册：把本模组数据挂载到女仆实体。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ModTaskData {
    public static TaskDataKey<MarriageData> MARRIAGE_DATA;
    public static TaskDataKey<PregnancyData> PREGNANCY_DATA;

    private ModTaskData() {
    }

    public static void registerAll(TaskDataRegister register) {
        MARRIAGE_DATA = register.register(
                ResourceLocation.fromNamespaceAndPath(MaidMarriageMod.MOD_ID, "marriage_data"),
                MarriageData.CODEC
        );
        PREGNANCY_DATA = register.register(
                ResourceLocation.fromNamespaceAndPath(MaidMarriageMod.MOD_ID, "pregnancy_data"),
                PregnancyData.CODEC
        );
    }
}
