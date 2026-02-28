package com.example.maidmarriage.advancement;

import com.example.maidmarriage.MaidMarriageMod;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * 成就发放工具：统一处理结婚、同眠、生育相关成就奖励。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ModAdvancements {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation ROOT = id("root");
    public static final ResourceLocation MARRIAGE = id("marriage");
    public static final ResourceLocation FIRST_ROMANCE = id("first_romance");
    public static final ResourceLocation CHILDBIRTH = id("childbirth");
    public static final ResourceLocation ROMANCE_TEN = id("romance_ten");

    private ModAdvancements() {
    }

    public static void grantMarriage(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, MARRIAGE);
    }

    public static void grantFirstRomance(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, FIRST_ROMANCE);
    }

    public static void grantChildbirth(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, CHILDBIRTH);
    }

    public static void grantRomanceTen(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, ROMANCE_TEN);
    }

    private static void grant(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.serverLevel().getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder holder = server.getAdvancements().get(id);
        if (holder == null) {
            LOGGER.warn("Cannot find advancement {} while granting to {}", id, player.getGameProfile().getName());
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : holder.value().criteria().keySet()) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MaidMarriageMod.MOD_ID, path);
    }
}
