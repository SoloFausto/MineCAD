package org.solofausto.minecad.blueprint;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class BlueprintCommands {
        private BlueprintCommands() {
        }

        public static void register() {
                CommandRegistrationCallback.EVENT.register(BlueprintCommands::registerDispatcher);
        }

        private static Direction parseDirection(String faceName) {
                for (Direction direction : Direction.values()) {
                        if (direction.asString().equalsIgnoreCase(faceName)) {
                                return direction;
                        }
                }
                return null;
        }

        private static void registerDispatcher(CommandDispatcher<ServerCommandSource> dispatcher,
                        CommandRegistryAccess registryAccess,
                        CommandManager.RegistrationEnvironment environment) {
                dispatcher.register(CommandManager.literal("blueprint")
                                .then(CommandManager.literal("start")
                                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                                                .then(CommandManager.argument("face",
                                                                                StringArgumentType.word())
                                                                                .executes(context -> {
                                                                                        ServerPlayerEntity player = context
                                                                                                        .getSource()
                                                                                                        .getPlayer();
                                                                                        BlockPos pos = BlockPosArgumentType
                                                                                                        .getBlockPos(context,
                                                                                                                        "pos");
                                                                                        String faceName = StringArgumentType
                                                                                                        .getString(context,
                                                                                                                        "face");
                                                                                        Direction face = parseDirection(
                                                                                                        faceName);
                                                                                        if (face == null) {
                                                                                                context.getSource()
                                                                                                                .sendError(Text
                                                                                                                                .literal("Invalid face: "
                                                                                                                                                + faceName));
                                                                                                return 0;
                                                                                        }

                                                                                        BlueprintSession session = BlueprintManager
                                                                                                        .getInstance()
                                                                                                        .getOrCreate(player
                                                                                                                        .getUuid());
                                                                                        session.start(new BlueprintOrigin(
                                                                                                        pos, face));

                                                                                        context.getSource()
                                                                                                        .sendFeedback(
                                                                                                                        () -> Text
                                                                                                                                        .literal("Blueprint started at "
                                                                                                                                                        + pos
                                                                                                                                                        + " facing "
                                                                                                                                                        + face),
                                                                                                                        false);
                                                                                        return 1;
                                                                                }))))
                                .then(CommandManager.literal("add")
                                                .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                                                                .then(CommandManager
                                                                                .argument("y", DoubleArgumentType
                                                                                                .doubleArg())
                                                                                .executes(context -> {
                                                                                        ServerPlayerEntity player = context
                                                                                                        .getSource()
                                                                                                        .getPlayer();
                                                                                        BlueprintSession session = BlueprintManager
                                                                                                        .getInstance()
                                                                                                        .getOrCreate(player
                                                                                                                        .getUuid());
                                                                                        if (session.getOrigin() == null) {
                                                                                                context.getSource()
                                                                                                                .sendError(Text
                                                                                                                                .literal("No active blueprint. Use /blueprint start first."));
                                                                                                return 0;
                                                                                        }

                                                                                        double x = DoubleArgumentType
                                                                                                        .getDouble(context,
                                                                                                                        "x");
                                                                                        double y = DoubleArgumentType
                                                                                                        .getDouble(context,
                                                                                                                        "y");
                                                                                        session.addPoint(
                                                                                                        new BlueprintPoint(
                                                                                                                        x,
                                                                                                                        y));

                                                                                        context.getSource()
                                                                                                        .sendFeedback(
                                                                                                                        () -> Text.literal(
                                                                                                                                        "Added point (" + x
                                                                                                                                                        + ", "
                                                                                                                                                        + y
                                                                                                                                                        + ")"),
                                                                                                                        false);
                                                                                        return 1;
                                                                                }))))
                                .then(CommandManager.literal("info")
                                                .executes(context -> {
                                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                                        BlueprintSession session = BlueprintManager.getInstance()
                                                                        .getOrCreate(player.getUuid());
                                                        if (session.getOrigin() == null) {
                                                                context.getSource().sendError(
                                                                                Text.literal("No active blueprint."));
                                                                return 0;
                                                        }

                                                        context.getSource().sendFeedback(
                                                                        () -> Text.literal("Blueprint origin: "
                                                                                        + session.getOrigin().blockPos()
                                                                                        + " face: "
                                                                                        + session.getOrigin().face()
                                                                                        + " points: "
                                                                                        + session.getPoints().size()),
                                                                        false);
                                                        return 1;
                                                }))
                                .then(CommandManager.literal("clear")
                                                .executes(context -> {
                                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                                        BlueprintManager.getInstance().clear(player.getUuid());
                                                        context.getSource().sendFeedback(
                                                                        () -> Text.literal("Blueprint cleared."),
                                                                        false);
                                                        return 1;
                                                })));
        }
}
