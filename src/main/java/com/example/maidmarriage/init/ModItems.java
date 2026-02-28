package com.example.maidmarriage.init;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.item.DescriptionItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册表：注册戒指、YES 枕头与测试道具。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaidMarriageMod.MOD_ID);

    public static final DeferredItem<Item> PROPOSAL_RING = ITEMS.register("proposal_ring",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.maidmarriage.proposal_ring"));

    public static final DeferredItem<Item> YES_PILLOW = ITEMS.register("yes_pillow",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.maidmarriage.yes_pillow"));

    public static final DeferredItem<Item> LONGING_TESTER = ITEMS.register("longing_tester",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private ModItems() {
    }
}
