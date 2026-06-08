package net.solace.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.solace.Feature;
import net.solace.Features;
import net.solace.Preset;
import net.solace.SolaceData;
import net.solace.SolaceState;
import net.solace.config.SolaceConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * M1/M4 — self-service command. Admin (manage others) lands in M8.
 */
public final class SolaceCommands {

    private SolaceCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Preset.values()).map(p -> p.id).toList(), builder);

    private static final SuggestionProvider<CommandSourceStack> FEATURE_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Feature.values()).map(f -> f.id).toList(), builder);

    /** Operator check (old permission level 2). */
    private static boolean isOp(CommandSourceStack src) {
        return Commands.LEVEL_GAMEMASTERS.check(src.permissions());
    }

    /** Self-service is allowed if the config permits it, or the source is an operator. */
    private static final Predicate<CommandSourceStack> CAN_SELF_SERVE = src ->
            SolaceConfig.get().allowSelfService || isOp(src);

    /** Per-player teleport cooldown tracking (server tick of last use). */
    private static final Map<UUID, Integer> LAST_TELEPORT_TICK = new HashMap<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("solace")
                        .then(Commands.literal("on").requires(CAN_SELF_SERVE).executes(ctx -> toggle(ctx, true)))
                        .then(Commands.literal("off").requires(CAN_SELF_SERVE).executes(ctx -> toggle(ctx, false)))
                        .then(Commands.literal("status").executes(SolaceCommands::status))
                        .then(Commands.literal("preset").requires(CAN_SELF_SERVE)
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests(PRESET_SUGGESTIONS)
                                        .executes(SolaceCommands::setPreset)))
                        .then(Commands.literal("set").requires(CAN_SELF_SERVE)
                                .then(Commands.argument("feature", StringArgumentType.word())
                                        .suggests(FEATURE_SUGGESTIONS)
                                        .then(Commands.literal("on").executes(ctx -> setFeature(ctx, Boolean.TRUE)))
                                        .then(Commands.literal("off").executes(ctx -> setFeature(ctx, Boolean.FALSE)))
                                        .then(Commands.literal("clear").executes(ctx -> setFeature(ctx, null)))))
                        .then(Commands.literal("tp").requires(CAN_SELF_SERVE)
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(SolaceCommands::teleport)))
                        .then(Commands.literal("home").requires(SolaceCommands::isOp)
                                .then(Commands.literal("set").executes(SolaceCommands::setHome))
                                .then(Commands.literal("clear").executes(SolaceCommands::clearHome)))
                        .then(Commands.literal("admin").requires(SolaceCommands::isOp)
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.literal("on").executes(ctx -> adminToggle(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminToggle(ctx, false)))
                                        .then(Commands.literal("status").executes(SolaceCommands::adminStatus))
                                        .then(Commands.literal("preset")
                                                .then(Commands.argument("preset", StringArgumentType.word())
                                                        .suggests(PRESET_SUGGESTIONS)
                                                        .executes(SolaceCommands::adminSetPreset)))
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("feature", StringArgumentType.word())
                                                        .suggests(FEATURE_SUGGESTIONS)
                                                        .then(Commands.literal("on").executes(ctx -> adminSetFeature(ctx, Boolean.TRUE)))
                                                        .then(Commands.literal("off").executes(ctx -> adminSetFeature(ctx, Boolean.FALSE)))
                                                        .then(Commands.literal("clear").executes(ctx -> adminSetFeature(ctx, null)))))))
                        .then(Commands.literal("reload").requires(SolaceCommands::isOp)
                                .executes(SolaceCommands::reload))
                ));
    }

    private static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Only players can use /solace."));
        }
        return player;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean on) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        SolaceData current = SolaceState.get(player);
        Preset preset = (on && current.pristine()) ? SolaceConfig.get().resolvedDefaultPreset() : current.preset();
        SolaceState.set(player, current.withEnabled(on).withPreset(preset));
        if (on) {
            player.setHealth(player.getMaxHealth());
            net.solace.event.ComfortTicker.clearAggro(player);
        }
        final Preset shown = preset;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Solace mode " + (on ? "enabled (" + shown.id + ")" : "disabled") + "."), false);
        return 1;
    }

    private static int setPreset(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "preset");
        Preset preset = Preset.tryById(id);
        if (preset == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown preset '" + id + "'. Options: survival+, comfort, creative-lite."));
            return 0;
        }
        SolaceState.set(player, SolaceState.get(player).withPreset(preset));
        ctx.getSource().sendSuccess(() -> Component.literal("Preset set to " + preset.id + "."), false);
        return 1;
    }

    private static int setFeature(CommandContext<CommandSourceStack> ctx, Boolean value) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "feature");
        Feature feature = Feature.byIdOrNull(id);
        if (feature == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown feature '" + id + "'."));
            return 0;
        }
        SolaceState.set(player, SolaceState.get(player).withOverride(feature, value));
        boolean effective = Features.effective(SolaceState.get(player), feature, SolaceConfig.get());
        String verb = (value == null) ? "reset to preset default" : (value ? "enabled" : "disabled");
        boolean blocked = Boolean.TRUE.equals(value) && !effective;
        ctx.getSource().sendSuccess(() -> Component.literal(
                feature.id + " " + verb + " (now " + (effective ? "on" : "off") + ")"
                        + (blocked ? " — blocked by server config" : "")), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        SolaceData data = SolaceState.get(player);
        SolaceConfig config = SolaceConfig.get();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Solace: " + (data.enabled() ? "ON" : "off") + " · preset: " + data.preset().id), false);
        for (Feature feature : Feature.values()) {
            boolean effective = Features.effective(data, feature, config);
            boolean overridden = data.overrides().containsKey(feature);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  " + (effective ? "✔ " : "✘ ") + feature.id + (overridden ? " (override)" : "")), false);
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        SolaceConfig.load();
        ctx.getSource().sendSuccess(() -> Component.literal("Solace config reloaded."), false);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        SolaceConfig config = SolaceConfig.get();
        if (!config.teleport.enabled) {
            ctx.getSource().sendFailure(Component.literal("Teleport is disabled on this server."));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        if (target == player) {
            ctx.getSource().sendFailure(Component.literal("You're already there."));
            return 0;
        }
        if (!config.teleport.crossDimension && target.level() != player.level()) {
            ctx.getSource().sendFailure(Component.literal("Cross-dimension teleport is disabled."));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        int now = server.getTickCount();
        int cooldownTicks = Math.max(0, config.teleport.cooldownSeconds) * 20;
        Integer last = LAST_TELEPORT_TICK.get(player.getUUID());
        if (last != null && cooldownTicks > 0 && now - last < cooldownTicks) {
            int remaining = (cooldownTicks - (now - last) + 19) / 20;
            ctx.getSource().sendFailure(Component.literal("Teleport on cooldown (" + remaining + "s)."));
            return 0;
        }
        player.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(),
                Set.of(), target.getYRot(), target.getXRot(), false);
        LAST_TELEPORT_TICK.put(player.getUUID(), now);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Teleported to " + target.getName().getString() + "."), false);
        return 1;
    }

    private static int setHome(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        SolaceConfig.Home home = new SolaceConfig.Home();
        home.dimension = player.level().dimension().identifier().toString();
        home.x = player.getX();
        home.y = player.getY();
        home.z = player.getZ();
        SolaceConfig.get().home = home;
        SolaceConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Solace home set: %s [%.0f, %.0f, %.0f] (used when baseProtection.mode = home_region).",
                home.dimension, home.x, home.y, home.z)), false);
        return 1;
    }

    private static int clearHome(CommandContext<CommandSourceStack> ctx) {
        SolaceConfig.get().home = null;
        SolaceConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Solace home cleared."), false);
        return 1;
    }

    // ----- admin: manage another player (op-only) -----

    private static int adminToggle(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        SolaceData current = SolaceState.get(target);
        Preset preset = (on && current.pristine()) ? SolaceConfig.get().resolvedDefaultPreset() : current.preset();
        SolaceState.set(target, current.withEnabled(on).withPreset(preset));
        if (on) {
            target.setHealth(target.getMaxHealth());
            net.solace.event.ComfortTicker.clearAggro(target);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Solace " + (on ? "enabled" : "disabled") + " for " + target.getName().getString() + "."), true);
        target.sendSystemMessage(Component.literal("Solace mode " + (on ? "enabled" : "disabled") + " by an operator."));
        return 1;
    }

    private static int adminStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        SolaceData data = SolaceState.get(target);
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString()
                + " — Solace: " + (data.enabled() ? "ON" : "off") + " · preset: " + data.preset().id), false);
        return 1;
    }

    private static int adminSetPreset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String id = StringArgumentType.getString(ctx, "preset");
        Preset preset = Preset.tryById(id);
        if (preset == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown preset '" + id + "'. Options: survival+, comfort, creative-lite."));
            return 0;
        }
        SolaceState.set(target, SolaceState.get(target).withPreset(preset));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set " + target.getName().getString() + "'s preset to " + preset.id + "."), true);
        return 1;
    }

    private static int adminSetFeature(CommandContext<CommandSourceStack> ctx, Boolean value) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String id = StringArgumentType.getString(ctx, "feature");
        Feature feature = Feature.byIdOrNull(id);
        if (feature == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown feature '" + id + "'."));
            return 0;
        }
        SolaceState.set(target, SolaceState.get(target).withOverride(feature, value));
        String verb = (value == null) ? "reset to preset default" : (value ? "enabled" : "disabled");
        ctx.getSource().sendSuccess(() -> Component.literal(
                feature.id + " " + verb + " for " + target.getName().getString() + "."), true);
        return 1;
    }
}
