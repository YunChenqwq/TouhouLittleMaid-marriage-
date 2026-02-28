package com.example.maidmarriage;

import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.init.ModEntities;
import com.example.maidmarriage.init.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MaidMarriageMod.MOD_ID)
/**
 * 模组主入口：负责注册物品、实体与配置界面。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class MaidMarriageMod {
    public static final String MOD_ID = "maidmarriage";

    public MaidMarriageMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(MaidMarriageMod::addCreativeTabItems);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfigs.SPEC);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            event.accept(ModItems.PROPOSAL_RING);
            event.accept(ModItems.YES_PILLOW);
            event.accept(ModItems.LONGING_TESTER);
        }
    }
}
