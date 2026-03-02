package com.example.maidmarriage.compat;

import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;

@LittleMaidExtension
/**
 * 车万女仆兼容入口：注册任务数据与交互事件监听。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public class LittleMaidCompat implements ILittleMaid {
    public LittleMaidCompat() {
        NeoForge.EVENT_BUS.register(MarriageEventHandler.class);
        NeoForge.EVENT_BUS.register(RomanceSleepManager.class);
        NeoForge.EVENT_BUS.register(MaidWorkManager.class);
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        ModTaskData.registerAll(register);
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        MaidWorkManager.addChildWorkTasks(manager);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void addMaidTips(MaidTipsOverlay overlay) {
        overlay.addTips("overlay.maidmarriage.proposal_ring.tip", ModItems.PROPOSAL_RING.get());
        overlay.addTips("overlay.maidmarriage.yes_pillow.tip", ModItems.YES_PILLOW.get());
        overlay.addTips("overlay.maidmarriage.rainbow_bouquet.tip", ModItems.RAINBOW_BOUQUET.get());
        overlay.addTips("overlay.maidmarriage.longing_tester.tip", ModItems.LONGING_TESTER.get());
        overlay.addTips("overlay.maidmarriage.flower_test_kit.tip", ModItems.FLOWER_TEST_KIT.get());
        overlay.addTips("overlay.maidmarriage.child.learn.enchantment", net.minecraft.world.item.Items.BOOK);
        overlay.addTips("overlay.maidmarriage.child.learn.alchemy", net.minecraft.world.item.Items.GLASS_BOTTLE);
        overlay.addTips("overlay.maidmarriage.child.learn.tactics", net.minecraft.world.item.Items.IRON_SWORD);
        overlay.addTips("overlay.maidmarriage.child.explore.near", net.minecraft.world.item.Items.STICK);
        overlay.addTips("overlay.maidmarriage.child.explore.ruins", net.minecraft.world.item.Items.MAP);
        overlay.addTips("overlay.maidmarriage.child.explore.abyss", net.minecraft.world.item.Items.ENDER_EYE);
        overlay.addTips("overlay.maidmarriage.child.favor.recover", net.minecraft.world.item.Items.SUGAR);
    }
}
