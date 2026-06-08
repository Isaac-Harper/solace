package net.solace.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * M5 — native Mod Menu integration. Loaded only when Mod Menu is present (client).
 */
public class SolaceModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SolaceConfigScreen::create;
    }
}
