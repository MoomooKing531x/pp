# NoseWeapons Resource Pack

A Paper plugin can't change block/item textures on its own — every nose item
is served as a regular `diamond_sword` (weapons) or `paper` (sheared
ingredients), stamped with a unique `CustomModelData` value. To skin them you
need a client-side **resource pack** that remaps those model data values to
custom models and textures.

This folder is that resource pack, minus the PNG textures themselves.

## Installing the pack

1. Zip this entire `resource-pack/` directory so that `pack.mcmeta` lives at
   the root of the zip.
2. Drop the zip into your Minecraft `resourcepacks/` folder (or host it on a
   server for auto-download).
3. Enable it in-game (Options → Resource Packs → move it to Selected).

## Adding your own textures

Every nose already has a matching `item/<name>.json` model file that points at
a texture at the same name. To replace a texture, drop a 16×16 (or larger
power-of-two) PNG into `assets/minecraft/textures/item/` with the matching
filename:

### Weapon textures (stored as `diamond_sword` variants)

| File to create                                        | Nose                 | CustomModelData |
|-------------------------------------------------------|----------------------|-----------------|
| `textures/item/nose_bloody.png`                       | Bloody Nose          | 1001            |
| `textures/item/nose_snotty.png`                       | Snotty Nose          | 1002            |
| `textures/item/nose_snoring.png`                      | Snoring Nose         | 1003            |
| `textures/item/nose_sneezing.png`                     | Sneezing Nose        | 1004            |
| `textures/item/nose_snot_bubble.png`                  | Snot Bubble          | 1005            |
| `textures/item/nose_ender_dragon.png`                 | Ender Dragon Nose    | 1006            |
| `textures/item/nose_booger_sniper.png`                | Booger Sniper Nose   | 1007            |

### Sheared ingredient textures (stored as `paper` variants)

| File to create                                                      | Nose ingredient       | CustomModelData |
|---------------------------------------------------------------------|-----------------------|-----------------|
| `textures/item/nose_ingredient_warden.png`                          | Warden Nose           | 2001            |
| `textures/item/nose_ingredient_wither.png`                          | Wither Nose           | 2002            |
| `textures/item/nose_ingredient_panda.png`                           | Panda Nose            | 2003            |
| `textures/item/nose_ingredient_spider.png`                          | Spider Nose           | 2004            |
| `textures/item/nose_ingredient_elderguardian.png`                   | Elder Guardian Nose   | 2005            |
| `textures/item/nose_ingredient_sniffer.png`                         | Sniffer Nose          | 2006            |
| `textures/item/nose_ingredient_enderdragon.png`                     | Ender Dragon Nose     | 2007            |
| `textures/item/nose_ingredient_villager.png`                        | Villager Nose         | 2008            |

No code or JSON changes are required — drop the PNGs in place, re-zip the
pack, and reload the resource pack in-game.

## Using a 3D model instead

If you want a full 3D model (e.g. the sniper rifle from the concept art), open
the corresponding `models/item/nose_booger_sniper.json` in a tool like
[Blockbench](https://www.blockbench.net/), export a Minecraft JSON model, and
replace the file. Keep the filename so the `diamond_sword.json` override still
finds it.
