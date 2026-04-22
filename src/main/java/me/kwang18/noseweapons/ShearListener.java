package me.kwang18.noseweapons;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ComplexEntityPart;
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
 * (CustomModelData 2001-2008) are dropped on the correct mobs when the player
 * crouch-shears them below 50% HP. There is no "player nose" -- players
 * cannot be sheared.
 */
public class ShearListener implements Listener {
    private final NoseWeapons plugin;
    private final Set<UUID> shearedEntities = new HashSet<>();
    private final Map<Class<? extends LivingEntity>, String> mobToNose = new HashMap<>();

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
        // The Ender Dragon is made of multiple ComplexEntityParts (head, body,
        // wings, tail); right-clicks land on those parts, not on the dragon
        // itself, so we have to redirect to the parent.
        if (raw instanceof ComplexEntityPart part) {
            raw = part.getParent();
        }
        if (!(raw instanceof LivingEntity target)) {
            return;
        }
        if (target instanceof Player) {
            return; // No player nose -- players are never sheared.
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
