package com.tygabeast.rootgolem.entity;

import com.tygabeast.rootgolem.RootGolemMod;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;

/**
 * Decides which items the golem can plant and how (single, 2x2, or either).
 *
 * The general rule covers almost every tree mod with no extra work: any block item in
 * #minecraft:saplings or #rootgolem:plantables, or whose block extends SaplingBlock, is plantable.
 * Whether it can or must grow as a 2x2 is read from the sapling's TreeGrower (the same data vanilla
 * uses to grow it). Saplings with their own growth code can be described with item tags instead:
 * #rootgolem:two_by_two (single or 2x2), #rootgolem:two_by_two_only, and #rootgolem:excluded.
 *
 * Results are cached per item and cleared whenever tags reload, so the golem never re-reads tags
 * on its hot path.
 */
public final class Plantables {
    public static final TagKey<Item> PLANTABLES = tag("plantables");
    public static final TagKey<Item> EXCLUDED = tag("excluded");
    public static final TagKey<Item> TWO_BY_TWO = tag("two_by_two");
    public static final TagKey<Item> TWO_BY_TWO_ONLY = tag("two_by_two_only");

    public enum Mode { NONE, SINGLE, SINGLE_OR_2X2, ONLY_2X2 }

    private static final Reference2ObjectOpenHashMap<Item, Mode> CACHE = new Reference2ObjectOpenHashMap<>();

    private Plantables() {}

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, RootGolemMod.id(path));
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    public static boolean isPlantable(ItemStack stack) {
        return !stack.isEmpty() && mode(stack.getItem()) != Mode.NONE;
    }

    public static Mode mode(Item item) {
        synchronized (CACHE) {
            Mode m = CACHE.get(item);
            if (m == null) {
                m = classify(item);
                CACHE.put(item, m);
            }
            return m;
        }
    }

    @SuppressWarnings("deprecation")
    private static Mode classify(Item item) {
        if (!(item instanceof BlockItem blockItem)) return Mode.NONE;
        Holder<Item> holder = item.builtInRegistryHolder();
        if (holder.is(EXCLUDED)) return Mode.NONE;
        Block block = blockItem.getBlock();
        boolean plantable = holder.is(ItemTags.SAPLINGS) || holder.is(PLANTABLES) || block instanceof SaplingBlock;
        if (!plantable) return Mode.NONE;

        if (holder.is(TWO_BY_TWO_ONLY)) return Mode.ONLY_2X2;
        if (holder.is(TWO_BY_TWO)) return Mode.SINGLE_OR_2X2;
        if (block instanceof SaplingBlock sapling) {
            TreeGrower grower = sapling.treeGrower;
            boolean mega = grower.megaTree.isPresent();
            boolean single = grower.tree.isPresent() || grower.secondaryTree.isPresent();
            if (mega) return single ? Mode.SINGLE_OR_2X2 : Mode.ONLY_2X2;
        }
        return Mode.SINGLE;
    }
}
