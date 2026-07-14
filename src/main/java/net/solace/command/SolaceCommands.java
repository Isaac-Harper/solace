package net.solace.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.solace.Feature;
import net.solace.Features;
import net.solace.Preset;
import net.solace.Solace;
import net.solace.SolaceData;
import net.solace.SolaceState;
import net.solace.config.SolaceConfig;
import net.solace.event.ComfortTicker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * M1/M4: self-service command. Admin (manage others) lands in M8.
 */
public final class SolaceCommands {

    private SolaceCommands() {
    }

    private static final int TICKS_PER_SECOND = SharedConstants.TICKS_PER_SECOND;

    private static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Preset.values()).map(p -> p.id).toList(), builder);

    private static final SuggestionProvider<CommandSourceStack> FEATURE_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Feature.values()).map(f -> f.id).toList(), builder);

    private static final Identifier NODE_USE = Identifier.fromNamespaceAndPath(Solace.MOD_ID, "use");
    private static final Identifier NODE_ADMIN = Identifier.fromNamespaceAndPath(Solace.MOD_ID, "admin");

    /** The permission module is experimental; guard so its absence degrades instead of NoClassDefFoundError. */
    private static final boolean PERMISSION_API_PRESENT =
            FabricLoader.getInstance().isModLoaded("fabric-permission-api-v1");

    /** True when a permissions mod explicitly grants the node (unset nodes grant nothing). */
    private static boolean hasNode(CommandSourceStack src, Identifier node) {
        return PERMISSION_API_PRESENT
                && ((Object) src) instanceof PermissionContextOwner owner
                && owner.checkPermission(node, false);
    }

    /** Admin: vanilla operator level 2 (never deniable by a permissions mod), or the solace:admin node. */
    private static boolean isOp(CommandSourceStack src) {
        return Commands.LEVEL_GAMEMASTERS.check(src.permissions()) || hasNode(src, NODE_ADMIN);
    }

    /** Self-service: config permits everyone, or the source is an admin or holds solace:use. */
    private static final Predicate<CommandSourceStack> CAN_SELF_SERVE = src ->
            SolaceConfig.get().allowSelfService || isOp(src) || hasNode(src, NODE_USE);

    /** Per-player teleport cooldown tracking (server tick of last use). Cleared with the server. */
    private static final Map<UUID, Integer> LAST_TELEPORT_TICK = new HashMap<>();

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST_TELEPORT_TICK.clear());
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
                        .then(Commands.literal("fly").requires(CAN_SELF_SERVE)
                                .executes(SolaceCommands::toggleFly))
                        .then(Commands.literal("tp").requires(CAN_SELF_SERVE)
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(SolaceCommands::teleport)))
                        .then(Commands.literal("home")
                                .then(Commands.literal("set").requires(SolaceCommands::isOp)
                                        .executes(SolaceCommands::setHome))
                                .then(Commands.literal("clear").requires(SolaceCommands::isOp)
                                        .executes(SolaceCommands::clearHome))
                                .then(Commands.literal("tp").requires(CAN_SELF_SERVE)
                                        .executes(SolaceCommands::homeTeleport)))
                        .then(Commands.literal("admin").requires(SolaceCommands::isOp)
                                .then(Commands.literal("slot")
                                        .executes(SolaceCommands::showSlot)
                                        .then(Commands.literal("clear").executes(SolaceCommands::clearSlot)))
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

    /** Every state write goes through here so the reconcile can never be forgotten. */
    private static SolaceData update(ServerPlayer target, UnaryOperator<SolaceData> change) {
        SolaceData updated = change.apply(SolaceState.get(target));
        SolaceState.set(target, updated);
        ComfortTicker.reconcileNow(target);
        return updated;
    }

    /**
     * Shared enable/disable path for /solace on|off and /solace admin. With
     * allowMultiplePlayers off, the persistent slot holder decides who may enable
     * (offline holders keep their claim); the slot is claimed on enable and released
     * on disable, and is not maintained at all while the limit is off (the ticker
     * clears stale claims). Returns the applied preset, or null when blocked.
     */
    private static Preset setEnabled(CommandContext<CommandSourceStack> ctx, ServerPlayer target, boolean on) {
        MinecraftServer server = ctx.getSource().getServer();
        boolean limited = !SolaceConfig.get().allowMultiplePlayers;
        SolaceData current = SolaceState.get(target);
        if (!on && !current.enabled()) {
            // No-op for the player's state, but still free a stale slot claim
            // (persistence skew can leave the slot pointing at a disabled player).
            if (limited && target.getUUID().equals(SolaceState.slotHolder(server))) {
                SolaceState.setSlotHolder(server, null);
            }
            return current.preset(); // do not mark a never-configured player as configured
        }
        if (on && limited) {
            UUID holder = SolaceState.slotHolder(server);
            if (holder != null && !holder.equals(target.getUUID())) {
                ServerPlayer holderPlayer = server.getPlayerList().getPlayer(holder);
                String who = (holderPlayer != null) ? holderPlayer.getName().getString()
                        : "an offline player (ops: /solace admin slot)";
                ctx.getSource().sendFailure(Component.literal(
                        "Only one Solace player is allowed on this server (the slot is held by " + who + ")."));
                return null;
            }
        }
        Preset preset = (on && current.pristine()) ? SolaceConfig.get().resolvedDefaultPreset() : current.preset();
        update(target, data -> data.withEnabled(on).withPreset(preset));
        if (on) {
            if (limited) {
                SolaceState.setSlotHolder(server, target.getUUID());
            }
            target.setHealth(target.getMaxHealth());
            ComfortTicker.clearAggro(target);
        } else if (limited && target.getUUID().equals(SolaceState.slotHolder(server))) {
            SolaceState.setSlotHolder(server, null);
        }
        return preset;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean on) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        if (on && SolaceState.isEnabled(player)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Solace is already on (" + SolaceState.get(player).preset().id + ")."), false);
            return 1;
        }
        boolean noOp = !on && !SolaceState.isEnabled(player);
        Preset applied = setEnabled(ctx, player, on);
        if (applied == null) {
            return 0;
        }
        String message = noOp ? "Solace is already off."
                : "Solace mode " + (on ? "enabled (" + applied.id + ")" : "disabled") + ".";
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    /** Parse a preset id or send the failure message (options derived from the enum). */
    private static Preset presetOrNull(CommandContext<CommandSourceStack> ctx, String id) {
        Preset preset = Preset.tryById(id);
        if (preset == null) {
            String options = String.join(", ", Arrays.stream(Preset.values()).map(p -> p.id).toList());
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown preset '" + id + "'. Options: " + options + "."));
        }
        return preset;
    }

    /** The preset a player would actually get: pristine players receive the server default on enable. */
    private static Preset displayPreset(SolaceData data, SolaceConfig config) {
        return data.pristine() ? config.resolvedDefaultPreset() : data.preset();
    }

    private static int setPreset(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        Preset preset = presetOrNull(ctx, StringArgumentType.getString(ctx, "preset"));
        if (preset == null) {
            return 0;
        }
        update(player, data -> data.withPreset(preset));
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
        SolaceData updated = update(player, data -> data.withOverride(feature, value));
        boolean effective = Features.effective(updated, feature, SolaceConfig.get());
        String verb = (value == null) ? "reset to preset default" : (value ? "enabled" : "disabled");
        boolean blocked = Boolean.TRUE.equals(value) && !effective;
        String suffix = blocked ? ", blocked by server config"
                : (!updated.enabled() ? " (takes effect once Solace is on)" : "");
        ctx.getSource().sendSuccess(() -> Component.literal(
                feature.id + " " + verb + " (now " + (effective ? "on" : "off") + ")" + suffix), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        SolaceData data = SolaceState.get(player);
        SolaceConfig config = SolaceConfig.get();
        // A pristine player will receive the server default preset on first enable;
        // show that state instead of the placeholder they would never get. The copy
        // is display-only and never persisted.
        boolean pristine = data.pristine();
        SolaceData shown = pristine ? data.withPreset(displayPreset(data, config)) : data;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Solace: " + (shown.enabled() ? "ON" : "off") + " · preset: " + shown.preset().id
                        + (pristine ? " (server default)" : "")), false);
        for (Feature feature : Feature.values()) {
            boolean effective = Features.effective(shown, feature, config);
            boolean overridden = shown.overrides().containsKey(feature);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  " + (effective ? "✔ " : "✘ ") + feature.id + (overridden ? " (override)" : "")), false);
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        SolaceConfig.load();
        resyncCommandTrees(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Solace config reloaded."), false);
        return 1;
    }

    /**
     * Resend every online player's command tree. requires() results (allowSelfService)
     * are baked into each client's tree, so a config change is invisible until a resend.
     * Must run on the server thread (the config screen hops via {@code server.execute}).
     */
    public static void resyncCommandTrees(MinecraftServer server) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(online);
        }
    }

    /** Shared gate: the source player must have Solace enabled. Sends the failure message. */
    private static boolean requireEnabled(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (SolaceState.isEnabled(player)) {
            return true;
        }
        ctx.getSource().sendFailure(Component.literal("Solace is off. Enable it first with /solace on."));
        return false;
    }

    /** First teleport gate, before any destination is resolved or revealed: Solace on, teleports enabled. */
    private static boolean requireTeleportReady(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (!requireEnabled(ctx, player)) {
            return false;
        }
        if (!SolaceConfig.get().teleport.enabled) {
            ctx.getSource().sendFailure(Component.literal("Teleport is disabled on this server."));
            return false;
        }
        return true;
    }

    /** Second teleport gate, once the destination is known: dimension rules and cooldown. */
    private static boolean checkDestination(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                            ServerLevel destination) {
        if (!SolaceConfig.get().teleport.crossDimension && destination != player.level()) {
            ctx.getSource().sendFailure(Component.literal("Cross-dimension teleport is disabled."));
            return false;
        }
        int remaining = cooldownRemaining(player, ctx.getSource().getServer());
        if (remaining > 0) {
            ctx.getSource().sendFailure(Component.literal("Teleport on cooldown (" + remaining + "s)."));
            return false;
        }
        return true;
    }

    /**
     * Seconds of teleport cooldown remaining, 0 when ready. Expired entries are evicted.
     * A stored tick ahead of the current one means the map outlived a server instance
     * (the stopping hook missed, e.g. a crash): treat as expired.
     */
    private static int cooldownRemaining(ServerPlayer player, MinecraftServer server) {
        int now = server.getTickCount();
        // Long math: a huge hand-edited cooldownSeconds must clamp, not overflow to
        // negative (which would silently disable the cooldown entirely).
        int cooldownTicks = (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, SolaceConfig.get().teleport.cooldownSeconds) * TICKS_PER_SECOND);
        Integer last = LAST_TELEPORT_TICK.get(player.getUUID());
        if (last == null) {
            return 0;
        }
        if (cooldownTicks == 0 || last > now || now - last >= cooldownTicks) {
            LAST_TELEPORT_TICK.remove(player.getUUID());
            return 0;
        }
        return Mth.positiveCeilDiv(cooldownTicks - (now - last), TICKS_PER_SECOND);
    }

    /** Teleport, honoring vanilla's veto: no cooldown or success message on a refused move. */
    private static int doTeleport(CommandContext<CommandSourceStack> ctx, ServerPlayer player, ServerLevel level,
                                  double x, double y, double z, float yaw, float pitch, String successMessage) {
        if (!player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false)) {
            ctx.getSource().sendFailure(Component.literal("Teleport failed."));
            return 0;
        }
        LAST_TELEPORT_TICK.put(player.getUUID(), ctx.getSource().getServer().getTickCount());
        ctx.getSource().sendSuccess(() -> Component.literal(successMessage), false);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null || !requireTeleportReady(ctx, player)) {
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        if (target == player) {
            ctx.getSource().sendFailure(Component.literal("You're already there."));
            return 0;
        }
        ServerLevel destination = (ServerLevel) target.level();
        if (!checkDestination(ctx, player, destination)) {
            return 0;
        }
        return doTeleport(ctx, player, destination,
                target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot(),
                "Teleported to " + target.getName().getString() + ".");
    }

    /** Warp to the shared Solace home, if one has been set (/solace home set). */
    private static int homeTeleport(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null || !requireTeleportReady(ctx, player)) {
            return 0;
        }
        SolaceConfig.Home home = SolaceConfig.get().home;
        if (home == null || home.dimension == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No Solace home is set (an operator can run /solace home set)."));
            return 0;
        }
        ServerLevel level = home.resolveLevel(ctx.getSource().getServer());
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("Home dimension '" + home.dimension + "' does not exist."));
            return 0;
        }
        if (!checkDestination(ctx, player, level)) {
            return 0;
        }
        return doTeleport(ctx, player, level, home.x, home.y, home.z,
                player.getYRot(), player.getXRot(), "Teleported home.");
    }

    /** Toggle the flight feature override and apply it immediately. */
    private static int toggleFly(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null || !requireEnabled(ctx, player)) {
            return 0;
        }
        if (player.isCreative() || player.isSpectator()) {
            ctx.getSource().sendFailure(Component.literal("Your game mode already manages flight."));
            return 0;
        }
        SolaceConfig config = SolaceConfig.get();
        if (!Features.isAllowed(Feature.FLIGHT, config)) {
            ctx.getSource().sendFailure(Component.literal("Flight is disabled by the server config."));
            return 0;
        }
        boolean flying = Features.effective(SolaceState.get(player), Feature.FLIGHT, config);
        update(player, data -> data.withOverride(Feature.FLIGHT, !flying));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Flight " + (!flying ? "enabled" : "disabled") + "."), false);
        return 1;
    }

    private static int setHome(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        SolaceConfig.Home home = SolaceConfig.Home.of(player);
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
        if (on && SolaceState.isEnabled(target)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Solace is already on for " + target.getName().getString() + "."), false);
            return 1;
        }
        boolean noOp = !on && !SolaceState.isEnabled(target);
        if (setEnabled(ctx, target, on) == null) {
            return 0;
        }
        if (noOp) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Solace is already off for " + target.getName().getString() + "."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Solace " + (on ? "enabled" : "disabled") + " for " + target.getName().getString() + "."), true);
        target.sendSystemMessage(Component.literal("Solace mode " + (on ? "enabled" : "disabled") + " by an operator."));
        return 1;
    }

    private static int adminStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        SolaceData data = SolaceState.get(target);
        boolean pristine = data.pristine();
        Preset shown = displayPreset(data, SolaceConfig.get());
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString()
                + " · Solace: " + (data.enabled() ? "ON" : "off") + " · preset: " + shown.id
                + (pristine ? " (server default)" : "")), false);
        return 1;
    }

    /**
     * Escape hatch for allowMultiplePlayers=false: the slot survives its holder going
     * offline (by design), and /solace admin off cannot target offline players, so ops
     * need a direct way to inspect and free it.
     */
    private static int showSlot(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        UUID holder = SolaceState.slotHolder(server);
        if (holder == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("The Solace slot is free."), false);
            return 1;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(holder);
        String who = (online != null) ? online.getName().getString() : holder + " (offline)";
        ctx.getSource().sendSuccess(() -> Component.literal("The Solace slot is held by " + who + "."), false);
        return 1;
    }

    private static int clearSlot(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        // A still-enabled online holder would silently re-claim within a second;
        // clearing the slot means evicting them, so do it explicitly and say so.
        UUID holder = SolaceState.slotHolder(server);
        ServerPlayer holderPlayer = (holder == null) ? null : server.getPlayerList().getPlayer(holder);
        if (holderPlayer != null && SolaceState.isEnabled(holderPlayer)) {
            update(holderPlayer, data -> data.withEnabled(false));
            holderPlayer.sendSystemMessage(Component.literal("Solace mode disabled by an operator."));
        }
        SolaceState.setSlotHolder(server, null);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Solace slot cleared. The next player to enable Solace claims it."), true);
        return 1;
    }

    private static int adminSetPreset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        Preset preset = presetOrNull(ctx, StringArgumentType.getString(ctx, "preset"));
        if (preset == null) {
            return 0;
        }
        update(target, data -> data.withPreset(preset));
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
        update(target, data -> data.withOverride(feature, value));
        String verb = (value == null) ? "reset to preset default" : (value ? "enabled" : "disabled");
        ctx.getSource().sendSuccess(() -> Component.literal(
                feature.id + " " + verb + " for " + target.getName().getString() + "."), true);
        return 1;
    }
}
