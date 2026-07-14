package net.solace.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.solace.Feature;
import net.solace.Features;
import net.solace.Preset;
import net.solace.SolaceData;
import net.solace.SolaceState;
import net.solace.command.SolaceCommands;
import net.solace.config.SolaceConfig;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The Mod Menu screen. Two audiences, gated by context:
 *
 * <ul>
 *   <li><b>My Solace</b> (shown whenever the player is in a world): the player's own
 *   controls. On Save, the panel makes the server match what the toggles show by
 *   dispatching the minimal set of {@code /solace} commands, diffed against the player's
 *   live {@link SolaceData} (synced to their client). This is the server-authoritative
 *   path, so it works on any server and respects permissions and feature caps.
 *   <li><b>Server config</b> ({@link SolaceConfig}): editable at the title screen or as
 *   a singleplayer/LAN host; on a remote server the client only holds a local copy, so
 *   it shows a read-only notice instead of silently editing nothing.
 * </ul>
 *
 * <p>On a host the client owns the config, so caps and the default preset are known and
 * the panel is exact. On a dedicated server those are not synced to the client, so the
 * panel shows the player's intent and the server has the final say.
 */
public final class SolaceConfigScreen {

    private SolaceConfigScreen() {
    }

    public static Screen create(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean canEditConfig = mc.level == null || mc.isLocalServer(); // title screen or host

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("solace.config.title"));

        ConfigEntryBuilder eb = builder.entryBuilder();

        Intent intent = (player != null) ? addMySolace(builder, eb, player) : null;
        if (canEditConfig) {
            addServerConfig(builder, eb, SolaceConfig.get());
        } else {
            addRemoteNotice(builder, eb);
        }

        builder.setSavingRunnable(() -> {
            if (intent != null) {
                intent.apply();
            }
            // Only touch the server config when this client actually owns it, and hop to
            // the server thread for the command-tree resend (it walks server state).
            if (canEditConfig) {
                SolaceConfig.save();
                MinecraftServer server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> SolaceCommands.resyncCommandTrees(server));
                }
            }
        });
        return builder.build();
    }

    // ----- My Solace: the player's own controls -----

    /**
     * Collected panel state, applied on Save. Toggles diff against the player's live
     * state at Save time (not a frozen open-time snapshot), so re-saving is idempotent
     * and a change made elsewhere while the screen is open is not clobbered by a stale
     * baseline; each feature is resolved against the preset currently selected here.
     */
    private static final class Intent {
        boolean enabled;
        Preset preset;
        final Map<Feature, Boolean> features = new EnumMap<>(Feature.class);

        Intent(boolean enabled, Preset preset) {
            this.enabled = enabled;
            this.preset = preset;
        }

        void apply() {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return; // disconnected while the screen was open
            }
            SolaceConfig config = SolaceConfig.get();
            SolaceData cur = SolaceState.get(player);
            if (enabled != cur.enabled()) {
                send(player, enabled ? "solace on" : "solace off");
            }
            if (preset != cur.preset()) {
                send(player, "solace preset " + preset.id);
            }
            for (Feature feature : Feature.values()) {
                // Capped-off features can't change; skip so we never pin a redundant
                // override on them (isAllowed is exact on a host, permissive on remote).
                if (!Features.isAllowed(feature, config)) {
                    continue;
                }
                boolean want = features.get(feature);
                // Desired override relative to the SELECTED preset: null means "follow the
                // preset default", so setting a toggle back to the preset's value clears it.
                Boolean desiredOverride = (want == feature.defaultFor(preset)) ? null : want;
                Boolean curOverride = cur.overrides().get(feature);
                if (!Objects.equals(desiredOverride, curOverride)) {
                    send(player, desiredOverride == null
                            ? "solace set " + feature.id + " clear"
                            : "solace set " + feature.id + " " + (want ? "on" : "off"));
                }
            }
        }
    }

    private static Intent addMySolace(ConfigBuilder builder, ConfigEntryBuilder eb, LocalPlayer player) {
        SolaceConfig config = SolaceConfig.get();
        SolaceData cur = SolaceState.get(player);
        // A pristine player receives the server default preset on first enable; show that
        // (and its feature defaults) instead of the Comfort placeholder they never get.
        Preset shownPreset = cur.pristine() ? config.resolvedDefaultPreset() : cur.preset();
        SolaceData shown = cur.pristine() ? cur.withPreset(shownPreset) : cur;

        Intent intent = new Intent(cur.enabled(), shownPreset);

        ConfigCategory cat = builder.getOrCreateCategory(Component.translatable("solace.config.category.mysolace"));
        cat.addEntry(eb.startTextDescription(
                Component.translatable("solace.mysolace.hint").withStyle(ChatFormatting.GRAY)).build());

        cat.addEntry(eb.startBooleanToggle(Component.translatable("solace.mysolace.enabled"), cur.enabled())
                .setSaveConsumer(v -> intent.enabled = v)
                .build());

        cat.addEntry(eb.startEnumSelector(Component.translatable("solace.mysolace.preset"), Preset.class, shownPreset)
                .setEnumNameProvider(p -> presetName((Preset) p))
                .setSaveConsumer(p -> intent.preset = p)
                .build());

        for (Feature feature : Feature.values()) {
            // Cap-aware on a host (effective includes caps); best-effort on a dedicated server.
            boolean value = Features.effective(shown, feature, config);
            intent.features.put(feature, value);
            cat.addEntry(eb.startBooleanToggle(featureName(feature), value)
                    .setSaveConsumer(v -> intent.features.put(feature, v))
                    .build());
        }
        return intent;
    }

    private static void send(LocalPlayer player, String command) {
        player.connection.sendCommand(command);
    }

    // ----- Server config: host-editable -----

    private static void addServerConfig(ConfigBuilder builder, ConfigEntryBuilder eb, SolaceConfig cfg) {
        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("solace.config.category.general"));
        general.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.allow_self_service"), cfg.allowSelfService)
                .setTooltip(Component.translatable("solace.config.allow_self_service.tooltip"))
                .setSaveConsumer(v -> SolaceConfig.get().allowSelfService = v)
                .build());
        general.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.pacifist"), cfg.pacifist)
                .setTooltip(Component.translatable("solace.config.pacifist.tooltip"))
                .setSaveConsumer(v -> SolaceConfig.get().pacifist = v)
                .build());
        general.addEntry(eb.startEnumSelector(Component.translatable("solace.config.default_preset"), Preset.class, cfg.resolvedDefaultPreset())
                .setEnumNameProvider(p -> presetName((Preset) p))
                .setSaveConsumer(p -> SolaceConfig.get().defaultPreset = p.id)
                .build());
        general.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.allow_multiple_players"), cfg.allowMultiplePlayers)
                .setTooltip(Component.translatable("solace.config.allow_multiple_players.tooltip"))
                .setSaveConsumer(v -> SolaceConfig.get().allowMultiplePlayers = v)
                .build());

        ConfigCategory teleport = builder.getOrCreateCategory(Component.translatable("solace.config.category.teleport"));
        teleport.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.teleport.enabled"), cfg.teleport.enabled)
                .setSaveConsumer(v -> SolaceConfig.get().teleport.enabled = v).build());
        teleport.addEntry(eb.startIntField(Component.translatable("solace.config.teleport.cooldown"), cfg.teleport.cooldownSeconds)
                .setMin(0)
                .setSaveConsumer(v -> SolaceConfig.get().teleport.cooldownSeconds = v).build());
        teleport.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.teleport.cross_dimension"), cfg.teleport.crossDimension)
                .setSaveConsumer(v -> SolaceConfig.get().teleport.crossDimension = v).build());

        ConfigCategory protection = builder.getOrCreateCategory(Component.translatable("solace.config.category.protection"));
        protection.addEntry(eb.startSelector(Component.translatable("solace.config.protection.mode"),
                        SolaceConfig.BaseProtection.MODES,
                        cfg.baseProtection.resolvedMode())
                .setNameProvider(SolaceConfigScreen::modeName)
                .setTooltip(Component.translatable("solace.config.protection.mode.tooltip"))
                .setSaveConsumer(v -> SolaceConfig.get().baseProtection.mode = v)
                .build());
        protection.addEntry(eb.startIntField(Component.translatable("solace.config.protection.home_radius"), cfg.baseProtection.homeRadius)
                .setMin(1)
                .setTooltip(Component.translatable("solace.config.protection.home_radius.tooltip"))
                .setSaveConsumer(v -> SolaceConfig.get().baseProtection.homeRadius = v).build());

        ConfigCategory caps = builder.getOrCreateCategory(Component.translatable("solace.config.category.caps"));
        caps.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.caps.flight"), cfg.featureCaps.flight)
                .setSaveConsumer(v -> SolaceConfig.get().featureCaps.flight = v).build());
        caps.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.caps.reach"), cfg.featureCaps.reachBoost)
                .setSaveConsumer(v -> SolaceConfig.get().featureCaps.reachBoost = v).build());
        caps.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.caps.mining"), cfg.featureCaps.miningBoost)
                .setSaveConsumer(v -> SolaceConfig.get().featureCaps.miningBoost = v).build());
        caps.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.caps.infinite_blocks"), cfg.featureCaps.infiniteBasicBlocks)
                .setSaveConsumer(v -> SolaceConfig.get().featureCaps.infiniteBasicBlocks = v).build());
        caps.addEntry(eb.startBooleanToggle(Component.translatable("solace.config.caps.no_durability"), cfg.featureCaps.noDurability)
                .setSaveConsumer(v -> SolaceConfig.get().featureCaps.noDurability = v).build());
    }

    private static void addRemoteNotice(ConfigBuilder builder, ConfigEntryBuilder eb) {
        ConfigCategory info = builder.getOrCreateCategory(Component.translatable("solace.config.category.serverconfig"));
        info.addEntry(eb.startTextDescription(
                Component.translatable("solace.config.remote.heading").withStyle(ChatFormatting.YELLOW)).build());
        info.addEntry(eb.startTextDescription(Component.translatable("solace.config.remote.body")).build());
    }

    // ----- shared -----

    /** Readable dropdown label for a preset. Renders the raw key if no lang entry exists. */
    private static Component presetName(Preset preset) {
        return Component.translatable("solace.preset." + preset.name().toLowerCase(Locale.ROOT));
    }

    private static Component modeName(String mode) {
        return Component.translatable("solace.protection." + mode);
    }

    private static Component featureName(Feature feature) {
        return Component.translatable("solace.feature." + feature.id);
    }
}
