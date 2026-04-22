# NoseWeapons

Paper 1.21 plugin that adds seven custom "nose" melee weapons, a mob-shearing
flow for crafting ingredients, and the charged **Booger Sniper Nose**.

This repository merges two previously separate plugins:

- The original **NoseWeapons** plugin (weapon items, abilities, recipes).
- The **NoseShear** plugin (harvesting nose ingredients from mobs). NoseShear
  was broken in the supplied build — its `plugin.yml` pointed at a class that
  didn't exist because the class lived in `io.kwang18.noseshear` but the
  manifest referenced `me.kwang18.noseshear`. It also only handled villagers
  and handed out weapon-model items (1001-1003) that no recipe accepts. Both
  bugs are fixed in this repo and the shearing logic lives directly inside
  NoseWeapons so there is only one plugin jar to install.

## Quick build

```bash
mvn -DskipTests package
# drop target/NoseWeapons-1.1-SNAPSHOT.jar into your server's plugins/ folder
```

Requires Java 21 and Paper 1.21.1 at runtime.

## Nose weapons

All weapon abilities fire on **Shift + Right-click** while holding the weapon.
All particle effects can be toggled globally at runtime via
`/nosetoggle particles off`.

| Weapon                  | Ability                                                                                        | Cooldown |
|-------------------------|------------------------------------------------------------------------------------------------|----------|
| Bloody Nose             | Costs 2 hearts, grants Strength III (7s), then Wither (14s)                                    | 60s      |
| Snotty Nose             | Traps nearby players in cobwebs and applies Slowness III                                       | 45s      |
| Snoring Nose            | Sucks nearby players toward you                                                                | 45s      |
| Sneezing Nose           | Launches you forward in a puff of **white particles**                                          | 40s      |
| Snot Bubble             | Grants Resistance II for 7s with a bubble aura                                                 | 60s      |
| Ender Dragon Nose       | Shoots a non-incendiary Dragon Fireball; passive Strength II & Speed II while held             | 30s      |
| **Booger Sniper Nose**  | Charge-and-release "Pressurized Mucous Ejection" (see below)                                   | 20-100s  |

### Booger Sniper Nose charge mechanic

Hold **Shift + Right-click** to charge. The action bar displays a 5-segment
charge bar; release sneak (or switch off the weapon) to fire at the current
level. At max charge the shot auto-fires.

| Charge time | Level | Payload                                   | Cooldown |
|-------------|-------|-------------------------------------------|----------|
| 1s          | 1     | Small booger (6 dmg)                       | 20s      |
| 2s          | 2     | Medium booger (12 dmg, crit)               | 30s      |
| 3s          | 3     | 3-pellet scatter (10 dmg each)             | 50s      |
| 4s          | 4     | Big booger (20 dmg, heavy knockback)       | 70s      |
| 5s (MAX)    | 5     | 5-pellet shotgun blast (16 dmg each)       | 100s     |

Every projectile leaves a slime/dust trail; the pellet spread widens at levels
3 and 5 to match the concept art.

## Crafting

All seven weapons have shaped recipes keyed off a species-specific "nose"
ingredient harvested via shearing. Toggle with `/nosecrafting <true|false>`
or `/nosetoggle crafting <on|off>`.

The Booger Sniper Nose uses the recipe from the concept art:

```
E N E     E = Ender Pearl          N = Warden Nose
T C T     T = TNT                  C = Conduit
S P B     S = Smithing Template    P = Netherite Pickaxe    B = Netherite Block
```

Full recipes are defined in [`NoseWeapons.java`](src/main/java/me/kwang18/noseweapons/NoseWeapons.java).

## Shearing (the fixed NoseShear flow)

Crouch and right-click a mob (or player) with shears. The target must be
**below 50% max health** or shearing fails.

| Target                   | Drop                           |
|--------------------------|--------------------------------|
| Villager                 | Villager Nose (CMD 2008)       |
| Warden                   | Warden Nose (CMD 2001)         |
| Wither                   | Wither Nose (CMD 2002)         |
| Panda                    | Panda Nose (CMD 2003)          |
| Spider                   | Spider Nose (CMD 2004)         |
| Elder Guardian           | Elder Guardian Nose (CMD 2005) |
| Sniffer                  | Sniffer Nose (CMD 2006)        |
| Ender Dragon             | Ender Dragon Nose (CMD 2007)   |
| Player (below 50% HP)    | Random nose from the roster    |

Each entity can only be sheared once per life. Player shearing can be
disabled with `/nosetoggle playershear off`.

## Commands

Run `/nosehelp` in-game for a live list. Summary:

| Command                                    | Permission           | Notes                                              |
|--------------------------------------------|----------------------|----------------------------------------------------|
| `/givenose <type> [player]`                | `noseweapons.give`   | Give a weapon. Types: bloody, snotty, snoring, sneezing, snot_bubble, ender_dragon, booger_sniper |
| `/givenoseitem <type> [player]`            | `noseweapons.give`   | Give a shear ingredient. Types: villager, warden, wither, panda, spider, elderguardian, sniffer, enderdragon |
| `/nosecrafting <true\|false>`              | `noseweapons.admin`  | Legacy crafting toggle                             |
| `/nosetoggle <feature> [on\|off]`          | `noseweapons.admin`  | Toggle any feature (see list below)                |
| `/nosehelp`                                | `noseweapons.help`   | Prints command reference. Default `true`.          |

`/nosetoggle` manages these features and persists them to `config.yml`:

- `crafting` — shaped nose-weapon recipes.
- `abilities` — Shift + right-click ability activations.
- `passives` — Ender Dragon passive buffs while held.
- `particles` — all cosmetic particle effects on abilities and projectiles.
- `shearing` — crouch + shear mob harvesting.
- `playershear` — allow crouch-shearing players below 50% health.

## Textures & resource pack

Every nose item ships with a unique `CustomModelData` value so a client-side
resource pack can skin them without modifying code. The repo includes a
scaffold with all the model JSONs pre-wired — see
[`resource-pack/README.md`](resource-pack/README.md) for the per-item PNG
filenames to drop in.

## Repository layout

```
pom.xml
src/main/
  java/me/kwang18/noseweapons/
    Feature.java           # runtime feature-flag enum
    NoseItem.java          # weapon enum + item factory
    NoseIngredient.java    # sheared-ingredient registry
    NoseWeapons.java       # plugin main + recipes + config
    WeaponListener.java    # abilities + Booger Sniper charge mechanic
    ShearListener.java     # fixed + extended shearing flow (mobs + players)
    WeaponCommands.java    # /givenose, /givenoseitem, /nosetoggle, /nosehelp
  resources/
    plugin.yml
    config.yml
resource-pack/
  pack.mcmeta
  assets/minecraft/models/item/*.json
```
