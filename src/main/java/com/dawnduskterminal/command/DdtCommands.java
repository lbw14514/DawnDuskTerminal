package com.dawnduskterminal.command;

import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.portal.PortalTeleporter;
import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.world.ChronoLine;
import com.dawnduskterminal.world.ChronoLineState;
import com.dawnduskterminal.world.ChronoSkyLight;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class DdtCommands {
    private DdtCommands() {}

    public static void register(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ddt")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("line").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ChronoLine line = ChronoLineState.serverLine();
                double distance = line.distance(player.getX(), player.getZ());
                double param = line.param(player.getX(), player.getZ());
                int light = ChronoSkyLight.maxSkyLight(line,
                    (int) Math.floor(player.getX()), (int) Math.floor(player.getZ()));
                ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "origin=(%.0f, %.0f) normal=(%.4f, %.4f) distance=%.1f param=%.3f skyLight=%d dimension=%s",
                    line.originX(), line.originZ(), line.normalX(), line.normalZ(),
                    distance, param, light, player.level().dimension().location())), false);
                return 1;
            }))
            .then(Commands.literal("tp")
                .then(Commands.argument("param", DoubleArgumentType.doubleArg(-1.0D, 1.0D))
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        ServerLevel chrono = player.server.getLevel(ModDimensions.CHRONO);
                        if (chrono == null) {
                            ctx.getSource().sendFailure(Component.literal("chrono dimension is not loaded"));
                            return 0;
                        }
                        double target = DoubleArgumentType.getDouble(ctx, "param");
                        ChronoLine line = ChronoLineState.serverLine();
                        double distance = target * DdtConfig.maxDistance();
                        double x = line.originX() + line.normalX() * distance;
                        double z = line.originZ() + line.normalZ() * distance;
                        Vec3 landing = PortalTeleporter.findSafeLanding(chrono, x, z, 160);
                        player.teleportTo(chrono, landing.x, landing.y, landing.z,
                            player.getYRot(), player.getXRot());
                        int light = ChronoSkyLight.maxSkyLight(line,
                            (int) Math.floor(landing.x), (int) Math.floor(landing.z));
                        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                            "param=%.2f landed at %.1f %.1f %.1f skyLight=%d",
                            target, landing.x, landing.y, landing.z, light)), false);
                        return 1;
                    }))));
    }
}
