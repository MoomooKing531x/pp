package me.kwang18.noseweapons;

/**
 * Runtime-toggleable feature flags. Managed by {@link NoseWeapons} and surfaced
 * to admins through the {@code /nosetoggle} command.
 */
public enum Feature {
    CRAFTING("crafting", "Shaped nose-weapon recipes"),
    ABILITIES("abilities", "Shift + right-click ability activations"),
    PASSIVES("passives", "Passive effects while holding a nose (e.g. Ender Dragon Nose buffs)"),
    PARTICLES("particles", "Cosmetic particle effects on abilities and projectiles"),
    SHEARING("shearing", "Crouch + shear mob to harvest a species-specific nose"),
    PLAYER_SHEAR("playershear", "Allow crouch-shearing players below 50% health");

    private final String key;
    private final String description;

    Feature(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() {
        return this.key;
    }

    public String description() {
        return this.description;
    }

    public static Feature fromKey(String raw) {
        if (raw == null) {
            return null;
        }
        String needle = raw.toLowerCase().replace("_", "").replace("-", "");
        for (Feature f : values()) {
            if (f.key.replace("_", "").equals(needle)) {
                return f;
            }
        }
        return null;
    }
}
