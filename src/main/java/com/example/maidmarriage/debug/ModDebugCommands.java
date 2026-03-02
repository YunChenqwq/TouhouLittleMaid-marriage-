package com.example.maidmarriage.debug;

import com.example.maidmarriage.init.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * In-game helper command for quick feature smoke tests.
 */
public final class ModDebugCommands {
    private ModDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("maidmarriage_selftest")
                .executes(ModDebugCommands::runSelfTest)
                .then(Commands.literal("run").executes(ModDebugCommands::runSelfTest)));
        dispatcher.register(Commands.literal("mm_selftest")
                .executes(ModDebugCommands::runSelfTest));
    }

    private static int runSelfTest(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        give(player, new ItemStack(ModItems.PROPOSAL_RING.get(), 2));
        give(player, new ItemStack(ModItems.YES_PILLOW.get(), 2));
        give(player, new ItemStack(ModItems.LONGING_TESTER.get(), 1));
        give(player, new ItemStack(ModItems.FLOWER_TEST_KIT.get(), 1));
        give(player, new ItemStack(Items.DIAMOND, 8));
        give(player, new ItemStack(Items.IRON_NUGGET, 32));
        give(player, new ItemStack(Items.WHITE_WOOL, 16));
        give(player, new ItemStack(Items.RED_WOOL, 16));
        give(player, new ItemStack(Items.BREAD, 32));
        give(player, new ItemStack(Items.MAP, 16));
        give(player, new ItemStack(Items.ENDER_EYE, 16));
        give(player, new ItemStack(Items.BOOK, 16));
        give(player, new ItemStack(Items.GLASS_BOTTLE, 16));
        give(player, new ItemStack(Items.IRON_SWORD, 8));

        context.getSource().sendSuccess(() -> Component.literal("[MaidMarriage] Self-test pack granted."), false);
        context.getSource().sendSuccess(() -> Component.literal("1) Open maid GUI and switch child tasks (study/explore)."), false);
        context.getSource().sendSuccess(() -> Component.literal("2) Put materials in maid backpack and verify countdown/reward loop."), false);
        context.getSource().sendSuccess(() -> Component.literal("3) Use ring + offhand ring for proposal flow; check recipe unlock."), false);
        context.getSource().sendSuccess(() -> Component.literal("4) Use yes pillow at night to test romance and childbirth dialogues."), false);
        context.getSource().sendSuccess(() -> Component.literal("5) Use longing tester and flower test kit for mood/favor tests."), false);
        return 1;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }
}
