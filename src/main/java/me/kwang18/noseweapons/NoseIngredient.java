package me.kwang18.noseweapons;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Registry of species-specific nose ingredients (the "sheared" items). Each
 * ingredient is a Paper ItemStack with:
 *   - Material PAPER (so it stacks and is visually neutral by default)
 *   - A localized display name with chat color
 *   - A unique CustomModelData value so resource packs can retexture each nose
 *   - A PDC string tag so shear output can be identified and matched against
 *     the canonical set (used by /givenoseitem, the shearing flow, and the
 *     future-proof nose-matching utilities).
 */
public final class NoseIngredient {
    public static final String INGREDIENT_KEY = "nose_ingredient";

    /** Canonical ingredient metadata. Keyed by lowercase id (e.g. {@code warden}). */
    public static final Map<String, Def> DEFS = new HashMap<>();

    static {
        register("villager",     "Villager Nose",        ChatColor.YELLOW,      2008);
        register("warden",       "Warden Nose",          ChatColor.DARK_RED,    2001);
        register("wither",       "Wither Nose",          ChatColor.BLACK,       2002);
        register("panda",        "Panda Nose",           ChatColor.WHITE,       2003);
        register("spider",       "Spider Nose",          ChatColor.DARK_GREEN,  2004);
        register("elderguardian","Elder Guardian Nose",  ChatColor.AQUA,        2005);
        register("sniffer",      "Sniffer Nose",         ChatColor.GOLD,        2006);
        register("enderdragon",  "Ender Dragon Nose",    ChatColor.DARK_PURPLE, 2007);
    }

    private NoseIngredient() {
    }

    private static void register(String id, String name, ChatColor color, int modelData) {
        DEFS.put(id, new Def(id, name, color, modelData));
    }

    /** Resolve common aliases players type at the command line. */
    public static Def resolve(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        String key = rawInput.toLowerCase(Locale.ROOT).trim();
        switch (key) {
            case "elder":
            case "guardian":
                key = "elderguardian";
                break;
            case "sniff":
                key = "sniffer";
                break;
            case "dragon":
            case "ender":
                key = "enderdragon";
                break;
            default:
                break;
        }
        return DEFS.get(key);
    }

    public static ItemStack create(Def def, Plugin plugin) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(def.color + def.displayName);
        meta.setCustomModelData(def.modelData);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, INGREDIENT_KEY),
            PersistentDataType.STRING,
            def.id);
        item.setItemMeta(meta);
        return item;
    }

    public static final class Def {
        public final String id;
        public final String displayName;
        public final ChatColor color;
        public final int modelData;

        Def(String id, String displayName, ChatColor color, int modelData) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.modelData = modelData;
        }
    }
}
