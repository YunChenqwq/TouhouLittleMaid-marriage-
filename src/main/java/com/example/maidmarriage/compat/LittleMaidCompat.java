package com.example.maidmarriage.compat;

import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
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
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        ModTaskData.registerAll(register);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void addMaidTips(MaidTipsOverlay overlay) {
        overlay.addTips("overlay.maidmarriage.proposal_ring.tip", ModItems.PROPOSAL_RING.get());
        overlay.addTips("overlay.maidmarriage.yes_pillow.tip", ModItems.YES_PILLOW.get());
        overlay.addTips("overlay.maidmarriage.longing_tester.tip", ModItems.LONGING_TESTER.get());
    }
}
