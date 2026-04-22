package me.kwang18.noseweapons;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class NoseWeapons extends JavaPlugin {
    private final Map<NoseItem.NoseType, NamespacedKey> recipeKeys = new EnumMap<>(NoseItem.NoseType.class);
    private final Map<String, NamespacedKey> extraRecipeKeys = new HashMap<>();
    private final Set<Feature> enabledFeatures = EnumSet.allOf(Feature.class);
    private final Map<NoseItem.NoseType, Integer> cooldownOverrides = new EnumMap<>(NoseItem.NoseType.class);

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.loadFeatureStates();
        this.loadCooldownOverrides();

        WeaponCommands commandExecutor = new WeaponCommands(this);
        this.bindCommand("givenose", commandExecutor);
        this.bindCommand("givenoseitem", commandExecutor);
        this.bindCommand("nosecrafting", commandExecutor);
        this.bindCommand("nosetoggle", commandExecutor);
        this.bindCommand("nosecooldown", commandExecutor);
        this.bindCommand("nosehelp", commandExecutor);

        this.getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        this.getServer().getPluginManager().registerEvents(new ShearListener(this), this);

        if (this.isFeatureEnabled(Feature.CRAFTING)) {
            this.registerRecipes();
        }
        this.getLogger().info("NoseWeapons enabled!");
    }

    @Override
    public void onDisable() {
        this.unregisterRecipes();
        this.getLogger().info("NoseWeapons disabled.");
    }

    private void bindCommand(String name, WeaponCommands executor) {
        if (this.getCommand(name) != null) {
            this.getCommand(name).setExecutor(executor);
        } else {
            this.getLogger().warning("Command '" + name + "' not found in plugin.yml!");
        }
    }

    private void loadFeatureStates() {
        for (Feature feature : Feature.values()) {
            boolean enabled = this.getConfig().getBoolean("features." + feature.key(), true);
            if (enabled) {
                this.enabledFeatures.add(feature);
            } else {
                this.enabledFeatures.remove(feature);
            }
        }
    }

    public boolean isFeatureEnabled(Feature feature) {
        return this.enabledFeatures.contains(feature);
    }

    /**
     * Toggles a feature. Returns the new state. Persists to {@code config.yml}
     * and updates recipe registration side-effects for CRAFTING.
     */
    public boolean setFeatureEnabled(Feature feature, boolean enabled) {
        boolean previous = this.isFeatureEnabled(feature);
        if (enabled) {
            this.enabledFeatures.add(feature);
        } else {
            this.enabledFeatures.remove(feature);
        }
        this.getConfig().set("features." + feature.key(), enabled);
        this.saveConfig();

        if (feature == Feature.CRAFTING && enabled != previous) {
            if (enabled) {
                this.registerRecipes();
            } else {
                this.unregisterRecipes();
            }
        }
        return enabled;
    }

    /** Convenience for the legacy {@code /nosecrafting} command. */
    public void setCraftingEnabled(boolean enabled) {
        this.setFeatureEnabled(Feature.CRAFTING, enabled);
    }

    public boolean isCraftingEnabled() {
        return this.isFeatureEnabled(Feature.CRAFTING);
    }

    // --- Cooldown overrides --------------------------------------------------

    private void loadCooldownOverrides() {
        this.cooldownOverrides.clear();
        if (!this.getConfig().isConfigurationSection("cooldowns")) {
            return;
        }
        for (String key : this.getConfig().getConfigurationSection("cooldowns").getKeys(false)) {
            NoseItem.NoseType type = NoseItem.resolve(key);
            if (type == null) {
                this.getLogger().warning("Unknown nose in cooldown override: " + key);
                continue;
            }
            int seconds = this.getConfig().getInt("cooldowns." + key);
            if (seconds >= 0) {
                this.cooldownOverrides.put(type, seconds);
            }
        }
    }

    /**
     * Look up the current cooldown (in seconds) for a nose type, honouring
     * any runtime override set via {@code /nosecooldown}.
     */
    public int getCooldownFor(NoseItem.NoseType type) {
        Integer override = this.cooldownOverrides.get(type);
        return override != null ? override : type.cooldownSeconds;
    }

    public Integer getCooldownOverride(NoseItem.NoseType type) {
        return this.cooldownOverrides.get(type);
    }

    public void setCooldownOverride(NoseItem.NoseType type, int seconds) {
        this.cooldownOverrides.put(type, seconds);
        this.getConfig().set("cooldowns." + type.configKey(), seconds);
        this.saveConfig();
    }

    public void resetCooldownOverride(NoseItem.NoseType type) {
        this.cooldownOverrides.remove(type);
        this.getConfig().set("cooldowns." + type.configKey(), null);
        this.saveConfig();
    }

    private void registerRecipes() {
        this.addRecipe(NoseItem.NoseType.SNOTTY, "snotty_sword", new String[]{"HCH", "ENE", "BDS"}, recipe -> {
            recipe.setIngredient('H', Material.HONEY_BLOCK);
            recipe.setIngredient('C', Material.CRACKED_STONE_BRICKS);
            recipe.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
            recipe.setIngredient('N', NoseIngredient.create(NoseIngredient.DEFS.get("spider"), this));
            recipe.setIngredient('B', Material.MILK_BUCKET);
            recipe.setIngredient('D', Material.DIAMOND_SWORD);
            recipe.setIngredient('S', Material.SLIME_BLOCK);
        });
        this.addRecipe(NoseItem.NoseType.SNOT_BUBBLE, "snot_bubble_sword", new String[]{"MSF", "ENG", "CDT"}, recipe -> {
            recipe.setIngredient('M', Material.MAGMA_BLOCK);
            recipe.setIngredient('S', Material.SEA_LANTERN);
            recipe.setIngredient('F', Material.FROGSPAWN);
            recipe.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
            recipe.setIngredient('N', NoseIngredient.create(NoseIngredient.DEFS.get("elderguardian"), this));
            recipe.setIngredient('G', Material.LIGHT_BLUE_STAINED_GLASS);
            recipe.setIngredient('C', Material.CONDUIT);
            recipe.setIngredient('D', Material.DIAMOND_SWORD);
            recipe.setIngredient('T', Material.TURTLE_EGG);
        });
        this.addRecipe(NoseItem.NoseType.SNORING, "snoring_sword", new String[]{"PGP", "ENE", "SRD"}, recipe -> {
            recipe.setIngredient('P', Material.PHANTOM_MEMBRANE);
            recipe.setIngredient('G', Material.GHAST_TEAR);
            recipe.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
            recipe.setIngredient('N', NoseIngredient.create(NoseIngredient.DEFS.get("sniffer"), this));
            recipe.setIngredient('S', Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE);
            recipe.setIngredient('R', Material.RED_DYE);
            recipe.setIngredient('D', Material.DIAMOND_SWORD);
        });
        this.addRecipe(NoseItem.NoseType.SNEEZING, "sneezing_sword", new String[]{"CSC", "ENR", "FDF"}, recipe -> {
            recipe.setIngredient('C', Material.COPPER_BLOCK);
            recipe.setIngredient('S', Material.SPORE_BLOSSOM);
            recipe.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
            recipe.setIngredient('N', NoseIngredient.create(NoseIngredient.DEFS.get("panda"), this));
            recipe.setIngredient('R', Material.CARROT_ON_A_STICK);
            recipe.setIngredient('F', Material.GLOWSTONE_DUST);
            recipe.setIngredient('D', Material.DIAMOND_SWORD);
        });
        this.addRecipe(NoseItem.NoseType.BLOODY, "bloody_sword", new String[]{"ESE", "CNC", "BDP"}, recipe -> {
            recipe.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
            recipe.setIngredient('S', Material.NETHERITE_SCRAP);
            recipe.setIngredient('C', Material.END_CRYSTAL);
            recipe.setIngredient('N', NoseIngredient.create(NoseIngredient.DEFS.get("warden"), this));
            recipe.setIngredient('B', Material.BEETROOT_SOUP);
            recipe.setIngredient('D', Material.DIAMOND_SWORD);
            recipe.setIngredient('P', Material.PIGLIN_HEAD);
        });
        this.addExtraRecipe(NoseItem.NoseType.BLOODY, "bloody_sword_wither", new String[]{"ESE", "CNC", "BDP"}, recipe -> {
            recipe.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
            recipe.setIngredient('S', Material.NETHERITE_SCRAP);
            recipe.setIngredient('C', Material.END_CRYSTAL);
            recipe.setIngredient('N', NoseIngredient.create(NoseIngredient.DEFS.get("wither"), this));
            recipe.setIngredient('B', Material.BEETROOT_SOUP);
            recipe.setIngredient('D', Material.DIAMOND_SWORD);
            recipe.setIngredient('P', Material.PIGLIN_HEAD);
        });
        this.addRecipe(NoseItem.NoseType.ENDER_DRAGON, "ender_dragon_sword", new String[]{"DBD", "ENE", "CSC"}, recipe -> {
            recipe.setIngredient('D', Material.DRAGON_BREATH);
            recipe.setIngredient('B', Material.DRAGON_EGG);
            recipe.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
            recipe.setIngredient('N', NoseIngredient.create(NoseIngredient.DEFS.get("enderdragon"), this));
            recipe.setIngredient('C', Material.END_CRYSTAL);
            recipe.setIngredient('S', Material.DIAMOND_SWORD);
        });

        // Booger Sniper Nose -- recipe specified by the user:
        //   A O A   A = Amethyst Shard,     O = Totem of Undying
        //   T N T   T = TNT,                N = Warden Nose
        //   S C H   S = Sculk Shrieker,     C = Crossbow,   H = Heavy Core
        this.addRecipe(NoseItem.NoseType.BOOGER_SNIPER, "booger_sniper_sword", new String[]{"AOA", "TNT", "SCH"}, recipe -> {
            recipe.setIngredient('A', Material.AMETHYST_SHARD);
            recipe.setIngredient('O', Material.TOTEM_OF_UNDYING);
            recipe.setIngredient('T', Material.TNT);
            recipe.setIngredient('N', new RecipeChoice.ExactChoice(
                NoseIngredient.create(NoseIngredient.DEFS.get("warden"), this)));
            recipe.setIngredient('S', Material.SCULK_SHRIEKER);
            recipe.setIngredient('C', Material.CROSSBOW);
            recipe.setIngredient('H', Material.HEAVY_CORE);
        });
    }

    @FunctionalInterface
    private interface RecipeConfigurer {
        void configure(ShapedRecipe recipe);
    }

    private void addRecipe(NoseItem.NoseType type, String key, String[] shape, RecipeConfigurer configurer) {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, key), NoseItem.createNose(type, this));
        recipe.shape(shape);
        configurer.configure(recipe);
        Bukkit.addRecipe((Recipe) recipe);
        this.recipeKeys.put(type, recipe.getKey());
    }

    private void addExtraRecipe(NoseItem.NoseType type, String key, String[] shape, RecipeConfigurer configurer) {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, key), NoseItem.createNose(type, this));
        recipe.shape(shape);
        configurer.configure(recipe);
        Bukkit.addRecipe((Recipe) recipe);
        this.extraRecipeKeys.put(key, recipe.getKey());
    }

    private void unregisterRecipes() {
        Iterator<NamespacedKey> it = this.recipeKeys.values().iterator();
        while (it.hasNext()) {
            Bukkit.removeRecipe(it.next());
            it.remove();
        }
        Iterator<NamespacedKey> it2 = this.extraRecipeKeys.values().iterator();
        while (it2.hasNext()) {
            Bukkit.removeRecipe(it2.next());
            it2.remove();
        }
    }

    /** Banner-style header used across commands so they feel unified. */
    public String bannerPrefix() {
        return ChatColor.DARK_GREEN + "[" + ChatColor.GREEN + "NoseWeapons" + ChatColor.DARK_GREEN + "] " + ChatColor.RESET;
    }
}
