package org.solofausto.minecad.client;

import net.fabricmc.api.ClientModInitializer;

public class MinecadClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlueprintOverlayRenderer.register();
        SketchToolBase.register();
    }
}
