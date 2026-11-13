package com.tygabeast.rootgolem.client;

import com.tygabeast.rootgolem.RootGolemMod;
import com.tygabeast.rootgolem.network.ClientBoundaryData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only registration. Only referenced behind a Dist.CLIENT check, so servers never load it. */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        ClientBoundaryData.screenOpener = BoundaryScreen::show;
        modBus.addListener(ClientSetup::onLayers);
        modBus.addListener(ClientSetup::onRenderers);
        NeoForge.EVENT_BUS.addListener(BoundaryParticles::onClientTick);
        NeoForge.EVENT_BUS.addListener(MarkerDistanceHud::onClientTick);
        modBus.addListener(ClientSetup::onGuiLayers);
    }

    private static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, MarkerDistanceHud.LAYER_ID, MarkerDistanceHud::render);
    }

    private static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RootGolemModel.LAYER, RootGolemModel::createBodyLayer);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RootGolemMod.ROOT_GOLEM.get(), RootGolemRenderer::new);
    }
}
