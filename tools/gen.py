#!/usr/bin/env python3
"""Generates the Root Golem's data and asset files: lang, models, blockstate, loot, recipe, tags and textures.

Run from anywhere:  python3 tools/gen.py   (needs Python 3 and Pillow)
"""
import json
import os

import golem_texture
import icon_render
import marker_texture

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
MOD = 'rootgolem'


def write_json(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def save_png(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ==== lang ========================================================================================
write_json(f'assets/{MOD}/lang/en_us.json', {
    f'entity.{MOD}.root_golem': 'Root Golem',
    f'item.{MOD}.root_golem_spawn_egg': 'Root Golem Spawn Egg',
    f'block.{MOD}.boundary_marker': 'Boundary Marker',
    f'message.{MOD}.full': "The Root Golem's inventory is full",
    f'message.{MOD}.boundary_found': 'Root Golem now tends this boundary (%s markers)',
    f'message.{MOD}.boundary_no_room': 'Root Golem tends this boundary (%s markers) - no free planting spot found lately; it may be full',
    f'hud.{MOD}.info.closed': 'Marker %s of %s - closed - %s x %s blocks',
    f'hud.{MOD}.info.open': 'Marker %s of %s - open (golems only plant in closed boundaries)',
    f'hud.{MOD}.info.settings': 'Tree spacing: %s - Owner: %s',
    f'hud.{MOD}.info.spacing': '%s',
    f'hud.{MOD}.info.spacing_default': '%s (server default)',
    f'hud.{MOD}.info.nobody': 'nobody',
    f'hud.{MOD}.info.no_golems': 'No golems working here',
    f'hud.{MOD}.info.golems': 'Golems working here: %s',
    f'hud.{MOD}.info.golems_full': 'Golems working here: %s - %s found no room lately',
    f'message.{MOD}.build.needs_dirt': 'Root Golem: needs rooted dirt at %s, %s, %s (iron golem T shape)',
    f'message.{MOD}.build.blocked': 'Root Golem: %s is in the way at %s, %s, %s - the spaces beside the head and the leg must be empty',
    f'subtitles.{MOD}.root_golem.ambient': 'Root Golem creaks',
    f'subtitles.{MOD}.root_golem.hurt': 'Root Golem hurts',
    f'subtitles.{MOD}.root_golem.death': 'Root Golem dies',
    f'subtitles.{MOD}.root_golem.step': 'Root Golem walks',
    f'subtitles.{MOD}.root_golem.plant': 'Root Golem plants a sapling',
    f'message.{MOD}.not_owner': 'This boundary belongs to %s',
    f'message.{MOD}.spacing': 'Tree spacing for this boundary: %s',
    f'spacing.{MOD}.default': 'server default (%s blocks)',
    f'spacing.{MOD}.2': '%s blocks (dense forest)',
    f'spacing.{MOD}.4': '%s blocks (forest)',
    f'spacing.{MOD}.8': '%s blocks (spaced)',
    f'spacing.{MOD}.12': '%s blocks (orchard)',
    f'hud.{MOD}.to_marker': 'Marker %s: %s / %s blocks',
    f'hud.{MOD}.to_next': 'Next marker (%s): %s / %s blocks',
    f'hud.{MOD}.to_first': 'Back to marker %s: %s / %s blocks',
    f'message.{MOD}.no_boundary': 'Not inside a closed boundary - the Root Golem will not plant',
    f'message.{MOD}.marker.started': 'New boundary started (marker 1)',
    f'message.{MOD}.marker.started_because': '%s - started a new boundary',
    f'message.{MOD}.marker.added': 'Marker %s connected',
    f'message.{MOD}.marker.added_closable': 'Marker %s connected - click the first marker to close the boundary',
    f'message.{MOD}.marker.too_far': 'Too far from the neighbouring marker (max %s blocks)',
    f'message.{MOD}.marker.too_big': 'Boundary would be wider than %s blocks',
    f'message.{MOD}.marker.close_too_far': 'The last marker is too far from the first to close (max %s blocks)',
    f'message.{MOD}.marker.closed': 'Boundary closed with %s markers - right-click a Root Golem with a marker to assign it here',
    f'message.{MOD}.marker.info_open': 'Marker %s of %s - boundary still open',
    f'message.{MOD}.marker.info_closed': 'Marker %s of %s - boundary closed',
    f'message.{MOD}.marker.too_many': 'Boundary already has %s markers',
    f'message.{MOD}.marker.inserted': 'Marker inserted - boundary now has %s markers',
    f'message.{MOD}.marker.selected_closed': 'Selected marker %s of %s - new markers will be inserted after it. Right-click a Root Golem with a marker to assign it here',
    f'message.{MOD}.marker.selected_open': 'Selected marker %s of %s - new markers will be added after it',
    f'message.{MOD}.assign.none': 'Click a marker of a boundary while holding a marker first',
    f'message.{MOD}.assign.open': 'That boundary is not closed yet',
    f'message.{MOD}.assign.done': 'Root Golem assigned to the selected boundary (%s markers)',
})

# ==== sounds: the golem's own events, mapped onto pitched vanilla files (no audio is shipped) ======
def snd(name, pitch=1.0, volume=1.0, weight=1):
    e = {'name': f'minecraft:{name}'}
    if pitch != 1.0: e['pitch'] = pitch
    if volume != 1.0: e['volume'] = volume
    if weight != 1: e['weight'] = weight
    return e

write_json(f'assets/{MOD}/sounds.json', {
    'entity.root_golem.ambient': {'subtitle': f'subtitles.{MOD}.root_golem.ambient', 'sounds':
        [snd(f'block/mangrove_roots/step{i}', 0.55, 0.5) for i in range(1, 7)]
        + [snd(f'block/azalea_leaves/step{i}', 0.8, 0.5) for i in range(1, 6)]},
    'entity.root_golem.hurt': {'subtitle': f'subtitles.{MOD}.root_golem.hurt', 'sounds':
        [snd(f'block/mangrove_roots/break{i}', 0.8) for i in range(1, 7)]
        + [snd(f'dig/wood{i}', 0.7) for i in range(1, 5)]},
    'entity.root_golem.death': {'subtitle': f'subtitles.{MOD}.root_golem.death', 'sounds':
        [snd(f'block/mangrove_roots/break{i}', 0.5, 1.0) for i in range(1, 7)]},
    'entity.root_golem.step': {'subtitle': f'subtitles.{MOD}.root_golem.step', 'sounds':
        [snd(f'mob/irongolem/walk{i}', 0.8, 0.45) for i in range(1, 5)]
        + [snd(f'block/rooted_dirt/step{i}', 0.7, 1.0, 2) for i in range(1, 7)]},
    'entity.root_golem.plant': {'subtitle': f'subtitles.{MOD}.root_golem.plant', 'sounds':
        [snd(f'block/rooted_dirt/break{i}', 1.1, 0.8) for i in range(1, 5)]
        + [snd(f'block/azalea/step{i}', 1.0, 0.8) for i in range(1, 7)]},
})

# ==== item and block models =======================================================================
write_json(f'assets/{MOD}/models/item/root_golem_spawn_egg.json', {'parent': 'minecraft:item/template_spawn_egg'})
write_json(f'assets/{MOD}/models/item/boundary_marker.json', {
    'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MOD}:item/boundary_marker'}})
write_json(f'assets/{MOD}/blockstates/boundary_marker.json', {'variants': {'': {'model': f'{MOD}:block/boundary_marker'}}})

T = f'{MOD}:block/boundary_marker'
side_post = {'uv': [0, 3, 4, 13], 'texture': '#tex'}
side_collar = {'uv': [8, 0, 14, 2], 'texture': '#tex'}
side_bulb = {'uv': [4, 0, 8, 3], 'texture': '#tex'}
write_json(f'assets/{MOD}/models/block/boundary_marker.json', {
    'parent': 'minecraft:block/block',
    'textures': {'tex': T, 'particle': T},
    'elements': [
        # wooden post wrapped in a root
        {'from': [6, 0, 6], 'to': [10, 10, 10], 'faces': {
            'north': side_post, 'south': side_post, 'east': side_post, 'west': side_post,
            'down': {'uv': [4, 4, 8, 8], 'texture': '#tex', 'cullface': 'down'},
        }},
        # moss collar
        {'from': [5, 10, 5], 'to': [11, 12, 11], 'faces': {
            'north': side_collar, 'south': side_collar, 'east': side_collar, 'west': side_collar,
            'up': {'uv': [8, 4, 14, 10], 'texture': '#tex'},
            'down': {'uv': [8, 10, 14, 16], 'texture': '#tex'},
        }},
        # glowing shroomlight bulb: drawn full-bright (NeoForge face data), unshaded
        {'from': [6, 12, 6], 'to': [10, 15, 10], 'shade': False,
         'neoforge_data': {'block_light': 15, 'sky_light': 15}, 'faces': {
            'north': side_bulb, 'south': side_bulb, 'east': side_bulb, 'west': side_bulb,
            'up': {'uv': [4, 8, 8, 12], 'texture': '#tex'},
        }},
    ],
})

# ==== loot, recipe, tags ==========================================================================
write_json(f'data/{MOD}/loot_table/entities/root_golem.json', {
    'type': 'minecraft:entity',
    'pools': [{
        'rolls': 1,
        'entries': [{
            'type': 'minecraft:item', 'name': 'minecraft:rooted_dirt',
            'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 3}}],
        }],
    }],
})
write_json(f'data/{MOD}/loot_table/blocks/boundary_marker.json', {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{MOD}:boundary_marker'}],
               'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
})
write_json(f'data/{MOD}/recipe/boundary_marker.json', {
    'type': 'minecraft:crafting_shapeless',
    'category': 'misc',
    'ingredients': [{'item': 'minecraft:stick'}, {'item': 'minecraft:dirt'}, {'tag': 'minecraft:saplings'}],
    'result': {'id': f'{MOD}:boundary_marker', 'count': 4},
})
write_json('data/minecraft/tags/block/mineable/shovel.json', {'replace': False, 'values': [f'{MOD}:boundary_marker']})

write_json(f'data/{MOD}/tags/item/plantables.json', {'replace': False, 'values': [
    '#minecraft:saplings', 'minecraft:crimson_fungus', 'minecraft:warped_fungus',
]})
write_json(f'data/{MOD}/tags/item/excluded.json', {'replace': False, 'values': []})
write_json(f'data/{MOD}/tags/item/two_by_two.json', {'replace': False, 'values': []})
write_json(f'data/{MOD}/tags/item/two_by_two_only.json', {'replace': False, 'values': []})
write_json(f'data/{MOD}/tags/block/clearable_plants.json', {'replace': False, 'values': [
    'minecraft:short_grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern', 'minecraft:dead_bush',
]})
write_json(f'data/{MOD}/tags/block/unplantable_ground.json', {'replace': False, 'values': [
    'minecraft:farmland', 'minecraft:dirt_path', 'minecraft:gravel',
]})
write_json(f'data/{MOD}/tags/block/keep_clear.json', {'replace': False, 'values': ['minecraft:dirt_path', 'minecraft:gravel']})

# ==== textures (golem_texture.py, marker_texture.py) =============================================
# Root Golem skin and glow layer (see golem_texture.py).
tex, eyes = golem_texture.build()
save_png(tex, f'assets/{MOD}/textures/entity/root_golem.png')
save_png(eyes, f'assets/{MOD}/textures/entity/root_golem_eyes.png')

# Mod icon (shown in the mod list): the golem's head rendered from the textures above.
icon_render.render(os.path.join(ROOT, f'assets/{MOD}/textures/entity/root_golem.png'),
                   os.path.join(ROOT, f'assets/{MOD}/textures/entity/root_golem_eyes.png'),
                   os.path.join(ROOT, f'{MOD}_icon.png'))

# Boundary marker block texture and inventory icon (see marker_texture.py).
save_png(marker_texture.block(), f'assets/{MOD}/textures/block/boundary_marker.png')
save_png(marker_texture.icon(), f'assets/{MOD}/textures/item/boundary_marker.png')

print('generated resources in', os.path.normpath(ROOT))
