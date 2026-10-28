package com.tygabeast.rootgolem;

import com.tygabeast.rootgolem.boundary.BoundaryCommand;
import com.tygabeast.rootgolem.boundary.BoundaryInfoHandler;
import com.tygabeast.rootgolem.network.BoundaryInfoPayloads;
import com.tygabeast.rootgolem.boundary.BoundaryMarkerBlock;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.tygabeast.rootgolem.boundary.BoundarySync;
import com.tygabeast.rootgolem.client.ClientSetup;
import com.tygabeast.rootgolem.entity.GolemBuildHandler;
import com.tygabeast.rootgolem.entity.Plantables;
import com.tygabeast.rootgolem.entity.RootGolem;
import com.tygabeast.rootgolem.network.BoundarySyncPayload;
import com.tygabeast.rootgolem.network.ClientBoundaryData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RootGolemMod.MODID)
public final class RootGolemMod {
    public static final String MODID = "rootgolem";

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // Hitbox matches the vanilla Iron Golem (1.4 x 2.7).
    public static final DeferredHolder<EntityType<?>, EntityType<RootGolem>> ROOT_GOLEM = ENTITIES.register("root_golem",
            () -> EntityType.Builder.of(RootGolem::new, MobCategory.MISC)
                    .sized(1.4F, 2.7F)
                    .clientTrackingRange(10)
                    .build(MODID + ":root_golem"));

    public static final DeferredBlock<BoundaryMarkerBlock> BOUNDARY_MARKER = BLOCKS.register("boundary_marker",
            () -> new BoundaryMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(0.5F)
                    .sound(SoundType.ROOTED_DIRT)
                    .noOcclusion()
                    .lightLevel(s -> 12)
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredItem<BlockItem> BOUNDARY_MARKER_ITEM = ITEMS.registerSimpleBlockItem("boundary_marker", BOUNDARY_MARKER);

    public static final DeferredItem<Item> ROOT_GOLEM_SPAWN_EGG = ITEMS.register("root_golem_spawn_egg",
            () -> new DeferredSpawnEggItem(ROOT_GOLEM, 0x62462F, 0x688434, new Item.Properties()));

    public RootGolemMod(IEventBus modBus, ModContainer container) {
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        RootGolemSounds.init(modBus);
        container.registerConfig(ModConfig.Type.SERVER, RootGolemConfig.SPEC);

        modBus.addListener(RootGolemMod::onAttributes);
        modBus.addListener(RootGolemMod::onCreativeTab);
        modBus.addListener(RootGolemMod::onRegisterPayloads);

        NeoForge.EVENT_BUS.addListener((BlockEvent.NeighborNotifyEvent e) -> GolemBuildHandler.onNeighborNotify(e));
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> BoundarySync.onPlayerTick(e));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            BoundarySync.forget(e.getEntity());
            BoundaryInfoHandler.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            BoundarySync.clear();
            BoundaryInfoHandler.clear();
        });
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> BoundaryMarkerBlock.onBreak(e));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BoundaryCommand.register(e.getDispatcher()));
        // Tag contents can change on /reload, so the plantable cache is rebuilt lazily afterwards.
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent e) -> Plantables.clearCache());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ROOT_GOLEM.get(), RootGolem.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ROOT_GOLEM_SPAWN_EGG.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BOUNDARY_MARKER_ITEM.get());
        }
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // The handler only stores plain data in a common class; rendering it is client-only code.
        var registrar = event.registrar("4");
        registrar.playToClient(BoundarySyncPayload.TYPE, BoundarySyncPayload.STREAM_CODEC,
                (payload, ctx) -> ClientBoundaryData.set(payload));
        registrar.playToClient(BoundaryInfoPayloads.Info.TYPE, BoundaryInfoPayloads.Info.STREAM_CODEC,
                (payload, ctx) -> ClientBoundaryData.setInfo(payload));
        registrar.playToServer(BoundaryInfoPayloads.Request.TYPE, BoundaryInfoPayloads.Request.STREAM_CODEC,
                BoundaryInfoHandler::handle);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
