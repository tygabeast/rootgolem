# Root Golem

A Minecraft 1.21.1 NeoForge mod by Tygabeast. It adds a Root Golem, a treant made of rooted dirt with the size and build of an iron golem. It plants saplings from its inventory inside an area you mark out with Boundary Markers, and keeps room between trees.

## Getting one
Build it like an iron golem, but out of **rooted dirt**: four rooted dirt blocks in a T, with a **carved pumpkin** or **jack o'lantern** placed on top last. This works whether the pumpkin is placed by a player, a dispenser or a command. The spaces beside the head and beside the leg must be free of solid blocks; grass, flowers, saplings, torches and snow layers there are fine. If the shape isn't right, nearby players are told which block is wrong. There is also a spawn egg in the creative Spawn Eggs tab.

## Marking out its area
Craft **Boundary Markers** from a stick, a dirt block and any sapling (shapeless), which makes 4. Each marker is a post topped with a small glowing shroomlight bulb and gives off light level 12, so a boundary doubles as path lighting.

- Place markers one after another around the area. Each marker connects to the one you placed before it, so you can outline any shape: rectangles, L-shapes, curves, whatever you like.
- Once there are 3 or more, **click the first marker** to close the loop. Use an empty hand, or click while holding a marker. A green sparkle shows which marker is first.
- **Sneak while placing** a marker to start a separate boundary.
- **Holding a marker** shows every boundary nearby. Dots travel along each connection from one marker to the next: **green** means the boundary is closed, **orange** means it's still open, and **white** marks the edge your next marker will be added to.
- **Distance counter:** while holding a marker, the distance from the spot you're aiming at to your current marker shows under the crosshair (e.g. `Marker 4: 37.5 / 64 blocks`). It turns yellow past 80% of the limit and red when too far. A second line shows the other edge that must also fit: back to marker 1 while building an open boundary, or on to the next marker when inserting into an existing one.
- **Marker info:** aim at any marker to see, under the crosshair, its number, the boundary's size, tree spacing and owner, and how many golems are working there. The golem line also says if any of them have found no room lately.
- **Direction arrows:** in the distance counter, an arrow points toward the marker when it isn't roughly ahead of you.
- Click any marker with an empty hand to see its number and whether its boundary is closed.
- Breaking a marker joins its two neighbours together. If a boundary drops below 3 markers, it reopens.
- Limits (configurable): markers can be up to 64 blocks apart, a boundary can be up to 128 blocks across, and a boundary can have up to 64 markers.

### Tree spacing per boundary
Click any marker of a boundary with a **stick** to cycle its tree spacing:
- server default (`treeSpacing`, 8)
- 2 blocks (dense forest)
- 4 blocks (forest)
- 8 blocks (spaced)
- 12 blocks (orchard)

### Ownership
A boundary belongs to the player who started it. With `ownerOnly` on (the default), only the owner and operators can extend, close, re-space or break it, assign golems to it, or open, feed or reassign the golems tending it. Boundaries made before 1.3.0, or by command, are unowned. The first player to edit one claims it, and ops can change owners with `/rootgolem boundary owner`. Explosions and other non-player causes can still break markers; use a claims mod if you need full protection.

### Expanding a boundary
**Click any marker while holding a marker** to select it. A white spark rises from it and the edge after it turns white. The next markers you place are **inserted after it**, between it and the next marker, so that edge stretches out to wherever you put them. This works on closed boundaries too, which stay closed. To move a corner, insert a new marker next to it, then break the old one.

After you close a boundary, the next marker you place starts a new boundary unless you select a marker first.

### Assigning golems
- A golem built or placed **inside a closed boundary** adopts it.
- To assign a golem to a boundary it **isn't** inside, select any marker of that boundary (or just close it), then **right-click the golem while holding a marker**. The golem walks over and starts tending it. This works from anywhere in the same dimension.
- A golem with no boundary doesn't plant. It checks every 10 seconds for a boundary around it, and **sneak + right-click** with an empty hand re-checks right away. If boundaries overlap, the golem uses the smallest one it stands in.
- Sneak + right-click also tells you if the golem hasn't found a free spot lately, which usually means its boundary is full. With 8-block gaps, a 20×20 area only fits a handful of trees.
- The golem keeps working when no players are nearby, as long as its chunks are ticking. It never walks into chunks that don't tick entities (loaded but outside simulation distance), because it would freeze there until a player came close.

## Using it
- **Right-click** with an empty hand (or any non-sapling item) to open its 18-slot inventory. The golem stands still while the inventory is open.
- **Right-click holding saplings** to hand them straight to it.
- **Bone meal** heals it by 25%.
- It creaks and rustles now and then, and plants with a soft dig sound and a small puff of soil and green sparkles.
- When it dies it drops its inventory and 2–3 rooted dirt.

## Planting rules
- Every few seconds the golem picks a **random** sapling type from its inventory and looks for a spot **inside its boundary**. Spots on the marker line itself don't count. If it can't find room where it is, it heads to another part of the boundary, so over time it covers all of it.
- Each spot must be free, have open space above, and be accepted by the sapling's own placement rules (soil and so on). The golem never plants on farmland and never loads chunks.
- **Paths:** it never plants on dirt path or gravel, and keeps `pathClearance` (2) blocks away from them so trunks don't spill onto walkways. Vanilla saplings can't be placed on those blocks anyway; the tag matters for modded saplings. Extend with the `rootgolem:keep_clear` block tag.
- **Collecting saplings:** it picks up saplings lying on the ground inside its boundary, such as ones dropped by decaying leaves, so a finished forest keeps replanting itself (`collectSaplings`, on by default).
- **Grass:** the golem breaks short grass, tall grass, ferns, large ferns and dead bushes that are in a planting spot (`clearPlants`, on by default; extend with the `rootgolem:clearable_plants` block tag). Broken plants drop nothing. Flowers are left alone.
- **Spacing:** it keeps the boundary's tree spacing (8 empty blocks by default; see above) between a new sapling and any other sapling or tree trunk, including trees on slopes above or below it.
- **2×2 trees:** saplings that only grow as 2×2 (dark oak) are always planted as a 2×2 when the golem holds 4 or more. Saplings that can grow either way (spruce, jungle) get a 2×2 at `twoByTwoChance` (50%). All four spots are checked before it plants any.
- Saplings are used up when planted (configurable).

## Other mods' trees
No per-mod code is needed for most tree mods. A block item is plantable if it is in `#minecraft:saplings` or `#rootgolem:plantables`, or if its block extends `SaplingBlock`. Whether it can or must grow as a 2×2 is read from the sapling's own tree data.

| Tag | Use |
|---|---|
| `rootgolem:plantables` (item) | Extra plantable items (crimson and warped fungus by default) |
| `rootgolem:two_by_two` (item) | Can grow single or as 2×2 |
| `rootgolem:two_by_two_only` (item) | Only grows as 2×2 |
| `rootgolem:excluded` (item) | Never planted |
| `rootgolem:unplantable_ground` (block) | Ground it won't plant on (farmland, dirt path and gravel by default) |
| `rootgolem:keep_clear` (block) | Ground it keeps `pathClearance` blocks away from (dirt path and gravel by default) |
| `rootgolem:clearable_plants` (block) | Plants it breaks to make room (grass, ferns, dead bushes by default) |

## Operator commands
- `/rootgolem boundary create <y> <x z, x z, x z ...>` places markers at those columns and closes the loop.
- `/rootgolem boundary list` lists the boundaries in your dimension, with spacing, owner and golem status.
- `/rootgolem boundary spacing <marker> <blocks>|default` sets a boundary's tree spacing.
- `/rootgolem boundary owner <marker> <player>|none` sets or clears a boundary's owner.
- `/rootgolem boundary expand <after> <pos>` inserts a marker at `pos` right after the marker at `after`.
- `/rootgolem golem assign <golems> <marker>` assigns golems to the closed boundary that the marker belongs to.

## Config
Per-world server config: `serverconfig/rootgolem-server.toml`. It covers planting interval, per-attempt search radius, vertical range, search attempts, 2×2 chance, default tree spacing, sapling consumption, plant clearing, path clearance, sapling collection, owner-only boundaries, boundary limits, whether building is allowed, and a debug log of planted saplings (off by default).

## Performance notes
- **Planting search:** runs once per `plantInterval` (80 ticks, jittered between golems). It tests a bounded number of random spots where the boundary and a 16-block square around the golem overlap, cheapest checks first.
- **Tree spacing:** reads one heightmap value and two blocks per column. A tree it finds is remembered for the rest of the attempt, so nearby spots are rejected without re-scanning.
- **Boundaries:** saved per dimension. Each boundary caches its bounds, so "is this inside?" is a box test plus one pass over its markers.
- **Boundary display:** costs nothing unless a player holds a marker. Outlines are sent only to those players, and only when something changed (including their selection). The client draws particles every 3 ticks, capped at 300.
- **Marker light:** vanilla block light (level 12), computed once when a marker is placed. The bulb is drawn full-bright through baked model data, so it costs nothing extra per frame.
- **Grass clearing:** runs only at the moment of planting, on at most 8 blocks.
- **Sapling collection:** one item search in a 24 × 8 × 24 box every 2 seconds per working golem.
- **Path clearance:** at most 25 columns per candidate spot (36 for a 2×2), checked after the cheaper tests.
- **Marker info:** requested only while aiming at a marker, at most once a second (the server ignores faster requests). Golem status is a small in-memory table each golem updates once a second; old entries are pruned, so it can't grow.
- **Distance counter:** rebuilt every 2 client ticks only while a marker is held. Drawing it is two text draws.
- **Roaming:** golems with a boundary reset the vanilla idle timer once a second, so they keep roaming without players near. That means one path every few seconds per working golem while its chunks tick; a golem whose area is full paths only every 10 seconds.
- **Build check:** listens to block updates. Its first test is a single block comparison, so it costs almost nothing.
- **Rendering:** the model is baked once. The glowing eyes are one extra draw per golem.
- **Planting effects:** one sound and two small particle packets per planting.
- **Ambient sound:** the golem's idle creak plays about once every 20 seconds (vanilla's default is every 4 seconds).

## Art and sound
- **Generated art:** all textures are generated from scratch by `tools/golem_texture.py` and `tools/marker_texture.py`.
- **Mod icon:** `tools/icon_render.py` renders the mod icon (the golem's head) from those textures on every generator run.
- **Sounds:** the golem's sounds are its own sound events (`rootgolem:entity.root_golem.*`) in `assets/rootgolem/sounds.json`. They map onto pitched vanilla sound files (roots, rooted dirt, azalea, the iron golem's footsteps), so the mod ships no audio of its own. Resource packs can replace any of them.

## Building from source
Requires Java 21 and Gradle 8.x (tested with 8.14). Run `gradle build`; the jar is written to `build/libs/`. `tools/gen.py` regenerates the lang, sound, model, blockstate, loot, recipe and tag files and all textures; it needs Python 3 and Pillow.

## 1.4.1 changes
- **New: mod icon** in the mod list, a render of the Root Golem's head.

## 1.4.0 changes (art pass)
- **New golem texture:** vanilla-style rooted dirt with roots crawling through it and moss growing down from the shoulders, hips and head. It has azalea flowers in its moss, green glowing eyes, and the iron golem's nose in place of the mouth.
- **Restyled Boundary Marker:** it uses the golem's palette, and has its own 16×16 inventory icon instead of a tiny 3D post.
- **Spawn egg:** recoloured to rooted-dirt brown with moss-green spots.
- **Sounds:** the golem's own sound events for ambient, hurt, death, steps and planting, each with subtitles.
- **Planting effect:** a puff of soil and green sparkles when the golem plants.
- **Cleanup:** the old texture helper code was removed from the generator (the golem texture is byte-identical before and after). The texture code is now split into `golem_texture.py` and `marker_texture.py`.

## 1.3.1 changes
- **Fix: building the golem.** The corner spaces beside the head and leg had to be pure air, like the iron golem, so a flower or similar block there silently stopped the build. Anything you can walk through is now fine there.
- **New: build hints.** Placing a pumpkin on rooted dirt in a shape that doesn't work tells nearby players which block is missing or in the way.

## 1.3.0 changes
- **New: collecting saplings.** Golems pick up saplings lying inside their boundary.
- **New: ownership.** Boundaries have owners; with `ownerOnly` (default on) only the owner and ops can change a boundary or manage its golems. Old boundaries are claimed by their first editor.
- **New: path clearance.** Golems don't plant on dirt path or gravel, and keep 2 blocks away from them.
- **New: spacing presets.** Each boundary can have its own tree spacing; click a marker with a stick to cycle through the presets.
- **New: marker info and arrows.** Aiming at a marker shows its boundary's info, and the distance counter shows direction arrows.
- **New: operator commands.** `/rootgolem boundary spacing` and `/rootgolem boundary owner`; `boundary list` now shows spacing, owner and golem status.
- **Fix:** a goal could tick once more after finishing. It caused a crash in the new collect goal during testing, and the planting goal is guarded the same way.
- **Cleanup:** the sneak + right-click re-check no longer adopts a boundary the player can't use; `findBoundary` is now internal; no unused imports, lang keys or resources found.

## 1.2.1 changes
- **Fix: golem stopping.** Vanilla stops a mob's wandering once no player has been within 32 blocks for 5 seconds. The golem then stayed in one spot, and once that patch was full it stopped planting until a player came right up to it. Golems with a boundary now keep roaming whether or not players are near.
- **Fix: smarter roaming.** After two searches with no room, the golem heads to a random part of its boundary instead of strolling locally. If the area looks full, it slows down to every 10 seconds to save pathfinding.
- **Fix: frozen golems.** The golem no longer targets spots in chunks that don't tick entities, so it can't walk into a frozen chunk.
- **New: distance counter** under the crosshair while holding a marker.
- **New: "full" status.** Sneak + right-click reports when the golem hasn't found room to plant lately.

## 1.2.0 changes
- **Marker recipe:** a stick, a dirt block and any sapling (shapeless), still making 4.
- **Marker look and light:** markers now have a glowing shroomlight bulb on a moss collar and give off light level 12.
- **Expanding boundaries:** click a marker while holding a marker to select it; new markers are inserted after it. The edge being extended is drawn white.
- **Remote assignment:** right-click a golem while holding a marker to assign it to your selected (or just-closed) boundary, wherever the golem is.
- **Grass clearing:** the golem now clears short and tall grass, ferns and dead bushes from planting spots.
- **New operator commands:** `/rootgolem boundary expand` and `/rootgolem golem assign`.
- **Behaviour change:** clicking a marker while holding a marker no longer places a new marker beside it; it closes the boundary (first marker) or selects the marker.
- **Cleanup:** removed the client-side "is this the first marker" guess (the server now decides every click) and the old moss-cap marker texture layout. "Too far" and "too big" messages are reworded so they read correctly for commands too.

## 1.1.0 changes
- **Renamed** from Gold Golem to Root Golem, with mod ID `goldgolem` → `rootgolem`. Gold Golems in old worlds won't carry over.
- **New look:** a rooted-dirt treant at iron golem size and build (hitbox 1.4 × 2.7), with glowing eyes. It's built from four rooted dirt blocks in the iron golem T shape plus a pumpkin, and the build now also works from dispensers and commands.
- **Boundary Markers:** planting is limited to a marked-out area of any shape. The biome rule is gone.
- **Spacing:** the default gap between trees is now 8 blocks, and the check now also sees trees on slopes.
- **Healing and drops:** bone meal heals instead of gold ingots, and the golem drops rooted dirt.
- **Cleanup:** removed the gold texture and model, the gold ingot heal, the biome check, the `wanderRadius` option and the sneak-click "set home" (sneak-click now re-checks the boundary).

## License
MIT
