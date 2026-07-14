package net.solace.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Solace is server-authoritative and has no client-side behavior;
 * the Mod Menu config screen is wired through {@link SolaceModMenu} (the {@code modmenu}
 * entrypoint), not here.
 */
public class SolaceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Intentionally empty: no client-side gameplay logic.
    }
}
