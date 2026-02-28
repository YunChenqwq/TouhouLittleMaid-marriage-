package com.example.maidmarriage.init;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.item.DescriptionItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册表：注册戒指、YES 枕头与测试道具。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidMarriageMod.MOD_ID);

    public static final RegistryObject<Item> PROPOSAL_RING = ITEMS.register("proposal_ring",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.maidmarriage.proposal_ring"));

    public static final RegistryObject<Item> YES_PILLOW = ITEMS.register("yes_pillow",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.maidmarriage.yes_pillow"));

    public static final RegistryObject<Item> LONGING_TESTER = ITEMS.register("longing_tester",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private ModItems() {
    }
}
