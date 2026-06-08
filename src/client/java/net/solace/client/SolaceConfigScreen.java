package net.solace.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.solace.Preset;
import net.solace.config.SolaceConfig;

/**
 * Cloth Config-backed screen bound to the live {@link SolaceConfig}. Saving writes
 * {@code config/solace.json}; on a singleplayer/LAN host this updates the running game live.
 */
public final class SolaceConfigScreen {

    private SolaceConfigScreen() {
    }

    public static Screen create(Screen parent) {
        SolaceConfig cfg = SolaceConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Solace"));
        builder.setSavingRunnable(SolaceConfig::save);

        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        general.addEntry(eb.startBooleanToggle(Component.literal("Allow self-service"), cfg.allowSelfService)
                .setTooltip(Component.literal("If off, only operators can use /solace."))
                .setSaveConsumer(v -> cfg.allowSelfService = v)
                .build());
        general.addEntry(eb.startBooleanToggle(Component.literal("Pacifist"), cfg.pacifist)
                .setTooltip(Component.literal("Solace players cannot deal damage to mobs or players."))
                .setSaveConsumer(v -> cfg.pacifist = v)
                .build());
        general.addEntry(eb.startEnumSelector(Component.literal("Default preset"), Preset.class, cfg.resolvedDefaultPreset())
                .setEnumNameProvider(p -> Component.literal(((Preset) p).id))
                .setSaveConsumer(p -> cfg.defaultPreset = p.id)
                .build());

        ConfigCategory caps = builder.getOrCreateCategory(Component.literal("Feature caps"));
        caps.addEntry(eb.startBooleanToggle(Component.literal("Flight"), cfg.featureCaps.flight)
                .setSaveConsumer(v -> cfg.featureCaps.flight = v).build());
        caps.addEntry(eb.startBooleanToggle(Component.literal("Reach boost"), cfg.featureCaps.reachBoost)
                .setSaveConsumer(v -> cfg.featureCaps.reachBoost = v).build());
        caps.addEntry(eb.startBooleanToggle(Component.literal("Mining boost"), cfg.featureCaps.miningBoost)
                .setSaveConsumer(v -> cfg.featureCaps.miningBoost = v).build());
        caps.addEntry(eb.startBooleanToggle(Component.literal("Infinite basic blocks"), cfg.featureCaps.infiniteBasicBlocks)
                .setSaveConsumer(v -> cfg.featureCaps.infiniteBasicBlocks = v).build());
        caps.addEntry(eb.startBooleanToggle(Component.literal("No durability"), cfg.featureCaps.noDurability)
                .setSaveConsumer(v -> cfg.featureCaps.noDurability = v).build());

        return builder.build();
    }
}
