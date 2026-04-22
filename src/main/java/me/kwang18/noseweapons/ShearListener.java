package me.kwang18.noseweapons;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sniffer;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Crouch + shear harvesting. The original NoseShear plugin had two bugs that
 * prevented it from loading at all:
 *
 *   1. Its {@code plugin.yml} pointed at {@code me.kwang18.noseshear.NoseShear}
 *      while the compiled class lived in {@code io.kwang18.noseshear}, so
 *      Paper refused to load the plugin.
 *   2. It only handled villagers and awarded weapon-model nose paper items
 *      (Bloody/Snotty/Snoring with CustomModelData 1001-1003), none of which
 *      are accepted by the NoseWeapons crafting recipes.
 *
 * This listener replaces that behaviour and lives inside NoseWeapons directly
 * so the two plugins are one cohesive install. Species-specific ingredients
 * (CustomModelData 2001-2008) are dropped on the correct mobs, and players
 * under 50% HP can also be "sheared" for a random ingredient.
 */
public class ShearListener implements Listener {
    private final NoseWeapons plugin;
    private final Set<UUID> shearedEntities = new HashSet<>();
    private final Map<Class<? extends LivingEntity>, String> mobToNose = new HashMap<>();
    private final Random random = new Random();

    public ShearListener(NoseWeapons plugin) {
        this.plugin = plugin;
        this.mobToNose.put(Villager.class, "villager");
        this.mobToNose.put(Warden.class, "warden");
        this.mobToNose.put(Wither.class, "wither");
        this.mobToNose.put(Panda.class, "panda");
        this.mobToNose.put(Spider.class, "spider");
        this.mobToNose.put(ElderGuardian.class, "elderguardian");
        this.mobToNose.put(Sniffer.class, "sniffer");
        this.mobToNose.put(EnderDragon.class, "enderdragon");
    }

    @EventHandler
    public void onShear(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!this.plugin.isFeatureEnabled(Feature.SHEARING)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.SHEARS) {
            return;
        }
        if (!player.isSneaking()) {
            return;
        }
        Entity raw = event.getRightClicked();
        if (!(raw instanceof LivingEntity target)) {
            return;
        }

        if (target instanceof Player playerTarget) {
            if (!this.plugin.isFeatureEnabled(Feature.PLAYER_SHEAR)) {
                player.sendMessage(this.plugin.bannerPrefix() + ChatColor.GRAY + "Player shearing is disabled.");
                event.setCancelled(true);
                return;
            }
            this.tryShearPlayer(player, playerTarget, hand, event);
            return;
        }

        this.tryShearMob(player, target, hand, event);
    }

    /**
     * If a species match exists for the mob and it is below 50% max health,
     * consume a point of shear durability and drop the species-specific nose
     * ingredient. Each mob can only be sheared once.
     */
    private void tryShearMob(Player player, LivingEntity target, ItemStack shears, PlayerInteractEntityEvent event) {
        String noseKey = this.matchMobType(target);
        if (noseKey == null) {
            return; // Not a supported mob; let the vanilla interaction proceed.
        }
        event.setCancelled(true);

        if (this.shearedEntities.contains(target.getUniqueId())) {
            player.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                + "This " + target.getType().name().toLowerCase().replace('_', ' ')
                + " has already lost its nose!");
            return;
        }
        if (!this.isBelowHalfHealth(target)) {
            player.sendMessage(this.plugin.bannerPrefix() + ChatColor.YELLOW
                + "You need to soften it up first -- shear only works below 50% health.");
            return;
        }

        this.shearedEntities.add(target.getUniqueId());
        this.damageShears(player, shears);

        NoseIngredient.Def def = NoseIngredient.DEFS.get(noseKey);
        player.getInventory().addItem(NoseIngredient.create(def, this.plugin));

        player.sendMessage(this.plugin.bannerPrefix() + def.color + "You sheared a " + def.displayName + "!");
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.0f, 1.0f);
        if (this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0, target.getHeight() * 0.75, 0.0),
                20, 0.3, 0.3, 0.3, 0.1);
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0.0, target.getHeight() * 0.75, 0.0),
                15, 0.2, 0.2, 0.2,
                new Particle.DustOptions(rgbFromChatColor(def.color), 1.2f));
        }
    }

    /**
     * Shear a player: requires target under 50% HP. Drops a random ingredient
     * from the roster, applies a brief nausea/wither burst, and damages the
     * shears. The target is added to the sheared-entity set for the rest of
     * their life so you can't stack harvests on the same player without them
     * respawning.
     */
    private void tryShearPlayer(Player shearer, Player target, ItemStack shears, PlayerInteractEntityEvent event) {
        event.setCancelled(true);
        if (target == shearer) {
            shearer.sendMessage(this.plugin.bannerPrefix() + ChatColor.GRAY + "You can't shear your own nose!");
            return;
        }
        if (this.shearedEntities.contains(target.getUniqueId())) {
            shearer.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + target.getName() + " has no nose left to harvest.");
            return;
        }
        if (!this.isBelowHalfHealth(target)) {
            shearer.sendMessage(this.plugin.bannerPrefix() + ChatColor.YELLOW
                + target.getName() + " is above 50% health -- soften them up first.");
            return;
        }

        this.shearedEntities.add(target.getUniqueId());
        this.damageShears(shearer, shears);

        // Randomise ingredient harvest from the full roster so player-shears
        // feel punchy and valuable.
        String[] pool = NoseIngredient.DEFS.keySet().toArray(new String[0]);
        NoseIngredient.Def def = NoseIngredient.DEFS.get(pool[this.random.nextInt(pool.length)]);
        shearer.getInventory().addItem(NoseIngredient.create(def, this.plugin));
        target.damage(2.0);

        shearer.sendMessage(this.plugin.bannerPrefix() + def.color
            + "You sheared " + target.getName() + "'s nose! (" + def.displayName + ")");
        target.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
            + shearer.getName() + " sheared off your nose! You feel much lighter.");
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.0f, 0.6f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.3f);
        if (this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0.0, 1.6, 0.0), 10);
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0.0, 1.6, 0.0), 25, 0.25, 0.25, 0.25,
                new Particle.DustOptions(Color.fromRGB(220, 30, 30), 1.3f));
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // Clean up the sheared-id set when entities die so the HashSet cannot
        // grow unboundedly on long-running servers.
        this.shearedEntities.remove(event.getEntity().getUniqueId());
    }

    /** Most specific LivingEntity class match against the registered mob map. */
    private String matchMobType(LivingEntity target) {
        for (Map.Entry<Class<? extends LivingEntity>, String> entry : this.mobToNose.entrySet()) {
            if (entry.getKey().isInstance(target)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isBelowHalfHealth(LivingEntity target) {
        var attr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = attr != null ? attr.getValue() : target.getHealth();
        if (max <= 0) {
            return true;
        }
        return target.getHealth() / max < 0.5;
    }

    private void damageShears(Player player, ItemStack shears) {
        if (!(shears.getItemMeta() instanceof Damageable meta)) {
            return;
        }
        meta.setDamage(Math.min(shears.getType().getMaxDurability() - 1, meta.getDamage() + 1));
        shears.setItemMeta(meta);
    }

    private static Color rgbFromChatColor(ChatColor chatColor) {
        return switch (chatColor) {
            case RED, DARK_RED -> Color.fromRGB(200, 30, 30);
            case GREEN, DARK_GREEN -> Color.fromRGB(90, 200, 80);
            case YELLOW -> Color.fromRGB(230, 210, 90);
            case AQUA, DARK_AQUA -> Color.fromRGB(120, 210, 220);
            case BLUE, DARK_BLUE -> Color.fromRGB(80, 120, 220);
            case GOLD -> Color.fromRGB(230, 170, 70);
            case WHITE -> Color.WHITE;
            case BLACK -> Color.fromRGB(40, 40, 40);
            case DARK_PURPLE, LIGHT_PURPLE -> Color.fromRGB(150, 60, 200);
            default -> Color.fromRGB(200, 200, 200);
        };
    }
}
