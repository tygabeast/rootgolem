package com.tygabeast.rootgolem;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The golem's own sound events. assets/rootgolem/sounds.json maps them onto pitched vanilla sound
 * files (roots, rooted dirt, azalea leaves, the iron golem's footsteps), so the mod ships no audio of
 * its own, and resource packs can replace each one.
 */
public final class RootGolemSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, RootGolemMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT = register("entity.root_golem.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = register("entity.root_golem.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = register("entity.root_golem.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = register("entity.root_golem.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANT = register("entity.root_golem.plant");

    private RootGolemSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(RootGolemMod.id(name)));
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
