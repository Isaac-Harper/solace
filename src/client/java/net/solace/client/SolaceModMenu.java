package net.solace.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * M5: native Mod Menu integration. Loaded only when Mod Menu is present (client).
 * The screen needs Cloth Config, a separate mod; without it the factory yields no
 * screen instead of a NoClassDefFoundError when the button is clicked.
 */
public class SolaceModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> null;
        }
        return SolaceConfigScreen::create;
    }
}
