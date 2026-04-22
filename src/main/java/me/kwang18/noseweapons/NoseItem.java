package me.kwang18.noseweapons;

import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Factory and metadata registry for all nose-weapon ItemStacks. Weapon identity
 * is stored in a PersistentDataContainer entry and surfaced visually through
 * {@code CustomModelData} so resource packs can skin each nose individually.
 */
public class NoseItem {
    public static final String NOSE_KEY = "nose_type";

    public static ItemStack createNose(NoseType type, JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.color + type.displayName);
        meta.setLore(Arrays.asList(type.lore));
        meta.setCustomModelData(type.modelData);
        meta.setUnbreakable(true);
        NamespacedKey key = new NamespacedKey((Plugin) plugin, NOSE_KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public static NoseType getNoseType(ItemStack item, JavaPlugin plugin) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String val = item.getItemMeta()
            .getPersistentDataContainer()
            .get(new NamespacedKey((Plugin) plugin, NOSE_KEY), PersistentDataType.STRING);
        if (val == null) {
            return null;
        }
        try {
            return NoseType.valueOf(val);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isNoseItem(ItemStack item, JavaPlugin plugin) {
        return getNoseType(item, plugin) != null;
    }

    /**
     * Parse a user-supplied identifier (command arg, config key, etc.) into a
     * {@link NoseType}. Accepts lowercase names, underscore or dash separated,
     * plus a couple of common short aliases.
     */
    public static NoseType resolve(String raw) {
        if (raw == null) {
            return null;
        }
        String normalised = raw.toLowerCase().replace('-', '_').trim();
        switch (normalised) {
            case "booger":
            case "sniper":
            case "boogersniper":
                return NoseType.BOOGER_SNIPER;
            case "enderdragon":
            case "dragon":
            case "ender":
                return NoseType.ENDER_DRAGON;
            case "snotbubble":
            case "bubble":
                return NoseType.SNOT_BUBBLE;
            default:
                break;
        }
        try {
            return NoseType.valueOf(normalised.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public enum NoseType {
        BLOODY("Bloody Nose", ChatColor.RED, 1001, 60,
            ChatColor.GRAY + "Shift + Right-Click: Costs 2 hearts",
            ChatColor.GRAY + "Gain Strength III for 7 seconds",
            ChatColor.GRAY + "then Wither for 14 seconds",
            ChatColor.DARK_GRAY + "Cooldown: 60 seconds"),
        SNOTTY("Snotty Nose", ChatColor.GREEN, 1002, 45,
            ChatColor.GRAY + "Shift + Right-Click: Trap target in cobwebs",
            ChatColor.GRAY + "and apply Slowness III",
            ChatColor.DARK_GRAY + "Cooldown: 45 seconds"),
        SNORING("Snoring Nose", ChatColor.YELLOW, 1003, 45,
            ChatColor.GRAY + "Shift + Right-Click: Suck nearby players",
            ChatColor.GRAY + "directly towards you",
            ChatColor.DARK_GRAY + "Cooldown: 45 seconds"),
        SNEEZING("Sneezing Nose", ChatColor.AQUA, 1004, 40,
            ChatColor.GRAY + "Shift + Right-Click: Launch forward",
            ChatColor.GRAY + "in the direction you're facing",
            ChatColor.DARK_GRAY + "Cooldown: 40 seconds"),
        SNOT_BUBBLE("Snot Bubble", ChatColor.BLUE, 1005, 60,
            ChatColor.GRAY + "Shift + Right-Click: Gain Resistance II",
            ChatColor.GRAY + "for 7 seconds",
            ChatColor.DARK_GRAY + "Cooldown: 60 seconds"),
        ENDER_DRAGON("Ender Dragon Nose", ChatColor.DARK_PURPLE, 1006, 30,
            ChatColor.GRAY + "Shift + Right-Click: Shoot Dragon Fireball",
            ChatColor.GRAY + "No self-damage, can fireball jump",
            ChatColor.GRAY + "Passive: Strength II & Speed II while held",
            ChatColor.DARK_GRAY + "Cooldown: 30 seconds"),
        BOOGER_SNIPER("Booger Sniper Nose", ChatColor.GREEN, 1007, 40,
            ChatColor.GRAY + "Ability: Pressurized Mucous Ejection",
            ChatColor.GRAY + "Shift + Right-Click to begin charging (1-5s)",
            ChatColor.GRAY + "Right-Click again to fire",
            ChatColor.GRAY + "Max charge (5s) fires a shotgun blast",
            ChatColor.DARK_GRAY + "Max charge cooldown: 40 seconds");

        public final String displayName;
        public final ChatColor color;
        public final int modelData;
        public final int cooldownSeconds;
        public final String[] lore;

        NoseType(String name, ChatColor color, int modelData, int cooldown, String... lore) {
            this.displayName = name;
            this.color = color;
            this.modelData = modelData;
            this.cooldownSeconds = cooldown;
            this.lore = lore;
        }

        /** Stable lowercase key used for config.yml and command arguments. */
        public String configKey() {
            return this.name().toLowerCase();
        }
    }
}
