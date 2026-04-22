package me.kwang18.noseweapons;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WeaponListener implements Listener {
    private final NoseWeapons plugin;
    private final Map<UUID, Map<NoseItem.NoseType, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, BoogerCharge> activeCharges = new HashMap<>();
    private final Random random = new Random();

    public WeaponListener(NoseWeapons plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    WeaponListener.this.updatePassiveEffects(p);
                }
            }
        }.runTaskTimer((Plugin) plugin, 0L, 20L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!NoseItem.isNoseItem(item, this.plugin)) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        NoseItem.NoseType type = NoseItem.getNoseType(item, this.plugin);

        // Booger Sniper has a tap-to-start / tap-to-fire lifecycle independent
        // of the sneak-gated abilities. If the player is already mid-charge,
        // any right-click releases the shot.
        if (type == NoseItem.NoseType.BOOGER_SNIPER) {
            BoogerCharge existing = this.activeCharges.get(player.getUniqueId());
            if (existing != null) {
                event.setCancelled(true);
                existing.release(ReleaseReason.TAP_FIRE);
                return;
            }
            if (!player.isSneaking()) {
                return;
            }
            event.setCancelled(true);
            if (!this.plugin.isFeatureEnabled(Feature.ABILITIES)) {
                player.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Nose abilities are currently disabled.");
                return;
            }
            if (this.isOnCooldown(player, type)) {
                player.sendMessage(this.plugin.bannerPrefix() + ChatColor.YELLOW
                    + "Booger Sniper on cooldown! " + this.getCooldownRemaining(player, type) + "s remaining.");
                return;
            }
            this.beginBoogerCharge(player);
            return;
        }

        if (!player.isSneaking()) {
            return;
        }
        event.setCancelled(true);

        if (!this.plugin.isFeatureEnabled(Feature.ABILITIES)) {
            player.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Nose abilities are currently disabled.");
            return;
        }
        if (this.isOnCooldown(player, type)) {
            long seconds = this.getCooldownRemaining(player, type);
            player.sendMessage(this.plugin.bannerPrefix() + ChatColor.YELLOW + "Ability on cooldown! " + seconds + "s remaining.");
            return;
        }
        switch (type) {
            case BLOODY -> this.activateBloody(player);
            case SNOTTY -> this.activateSnotty(player);
            case SNORING -> this.activateSnoring(player);
            case SNEEZING -> this.activateSneezing(player);
            case SNOT_BUBBLE -> this.activateSnotBubble(player);
            case ENDER_DRAGON -> this.activateEnderDragon(player);
            default -> {
                // Unhandled types fall through silently.
            }
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        this.updatePassiveEffects(event.getPlayer());
        this.cancelBoogerChargeIfItemChanged(event.getPlayer());
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.updatePassiveEffects(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        // Booger Sniper fires on a second right-click (see TAP_FIRE). Releasing
        // sneak does NOT fire -- the player can toggle sneak freely while
        // aiming. The charge is only auto-released if they switch away from
        // the weapon or reach max charge.
    }

    private void updatePassiveEffects(Player p) {
        if (!this.plugin.isFeatureEnabled(Feature.PASSIVES)) {
            return;
        }
        boolean hasMainDragon = NoseItem.isNoseItem(p.getInventory().getItemInMainHand(), this.plugin)
            && NoseItem.getNoseType(p.getInventory().getItemInMainHand(), this.plugin) == NoseItem.NoseType.ENDER_DRAGON;
        boolean hasOffDragon = NoseItem.isNoseItem(p.getInventory().getItemInOffHand(), this.plugin)
            && NoseItem.getNoseType(p.getInventory().getItemInOffHand(), this.plugin) == NoseItem.NoseType.ENDER_DRAGON;
        boolean has = hasMainDragon || hasOffDragon;
        if (has) {
            boolean hasStrength = p.hasPotionEffect(PotionEffectType.STRENGTH);
            boolean hasSpeed = p.hasPotionEffect(PotionEffectType.SPEED);
            if (!hasStrength) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, -1, 1, false, false, true));
            }
            if (!hasSpeed) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 1, false, false, true));
            }
        } else {
            p.getActivePotionEffects().forEach(e -> {
                if (e.getType() != PotionEffectType.STRENGTH && e.getType() != PotionEffectType.SPEED) {
                    return;
                }
                // Only clean up the infinite-duration passive we added; leave
                // finite-duration buffs (e.g. from /effect, potions) alone.
                if (e.getAmplifier() == 1 && (e.getDuration() == -1 || e.getDuration() > 1200)) {
                    p.removePotionEffect(e.getType());
                }
            });
        }
    }

    // --- Existing abilities --------------------------------------------------

    private void activateBloody(Player p) {
        if (p.getHealth() <= 5.0) {
            p.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Not enough health!");
            return;
        }
        p.setHealth(p.getHealth() - 4.0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 140, 2));
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (p.isOnline()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 280, 0));
            }
        }, 140L);
        p.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
            + "Bloody Nose activated! -2 hearts, +Strength III (7s) then Wither (14s)");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
        if (this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            Location head = p.getLocation().add(0.0, 1.6, 0.0);
            p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, head, 12);
            p.getWorld().spawnParticle(Particle.DUST, head, 40, 0.35, 0.35, 0.35,
                new Particle.DustOptions(Color.fromRGB(180, 20, 20), 1.6f));
            // Blood-drip trail lingering on the ground for a beat.
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!p.isOnline() || this.ticks++ >= 20) {
                        this.cancel();
                        return;
                    }
                    p.getWorld().spawnParticle(Particle.DUST,
                        p.getLocation().add(0.0, 0.8, 0.0),
                        3, 0.2, 0.2, 0.2,
                        new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.1f));
                }
            }.runTaskTimer(this.plugin, 0L, 2L);
        }
        this.setCooldown(p, NoseItem.NoseType.BLOODY, this.plugin.getCooldownFor(NoseItem.NoseType.BLOODY));
    }

    private void activateSnotty(Player p) {
        boolean hit = false;
        for (Entity e : p.getNearbyEntities(5.0, 5.0, 5.0)) {
            if (!(e instanceof Player t) || t == p) {
                continue;
            }
            Location loc = t.getLocation();
            for (int x = -1; x <= 1; ++x) {
                for (int z = -1; z <= 1; ++z) {
                    Block b = loc.clone().add(x, 0.0, z).getBlock();
                    if (b.getType() != Material.AIR) {
                        continue;
                    }
                    b.setType(Material.COBWEB);
                    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                        if (b.getType() == Material.COBWEB) {
                            b.setType(Material.AIR);
                        }
                    }, 100L);
                }
            }
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 2));
            hit = true;
        }
        if (!hit) {
            p.sendMessage(this.plugin.bannerPrefix() + ChatColor.GRAY + "No players nearby to snot!");
            return;
        }
        this.setCooldown(p, NoseItem.NoseType.SNOTTY, this.plugin.getCooldownFor(NoseItem.NoseType.SNOTTY));
        p.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN + "Snotty Nose activated!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1.0f, 1.0f);
        if (this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation(), 60, 0.6, 0.5, 0.6, 0.1);
            p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation().add(0.0, 1.0, 0.0), 20, 0.8, 0.5, 0.8, 0.05);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0.0, 1.0, 0.0), 25, 0.6, 0.4, 0.6,
                new Particle.DustOptions(Color.fromRGB(90, 200, 80), 1.2f));
        }
    }

    private void activateSnoring(final Player p) {
        boolean hit = false;
        for (Entity e : p.getNearbyEntities(10.0, 10.0, 10.0)) {
            if (!(e instanceof Player t) || t == p) {
                continue;
            }
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!t.isOnline() || !p.isOnline()
                        || t.getLocation().distanceSquared(p.getLocation()) < 1.5
                        || this.ticks++ >= 10) {
                        this.cancel();
                        return;
                    }
                    Vector dir = p.getLocation().toVector()
                        .subtract(t.getLocation().toVector())
                        .normalize().multiply(0.8);
                    t.setVelocity(dir);
                }
            }.runTaskTimer(this.plugin, 0L, 2L);
            hit = true;
        }
        if (!hit) {
            p.sendMessage(this.plugin.bannerPrefix() + ChatColor.GRAY + "No players nearby to suck!");
            return;
        }
        this.setCooldown(p, NoseItem.NoseType.SNORING, this.plugin.getCooldownFor(NoseItem.NoseType.SNORING));
        p.sendMessage(this.plugin.bannerPrefix() + ChatColor.YELLOW + "Snoring Nose activated!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
        if (this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            p.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, p.getLocation(), 50, 0.8, 1.2, 0.8, 0.05);
            p.getWorld().spawnParticle(Particle.COMPOSTER, p.getLocation(), 30, 0.5, 0.5, 0.5, 0.02);
            // A sleepy "Z" swirl above the player: enchant particles in a ring.
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!p.isOnline() || this.ticks++ >= 30) {
                        this.cancel();
                        return;
                    }
                    double angle = (this.ticks / 30.0) * Math.PI * 4;
                    Location c = p.getLocation().add(Math.cos(angle) * 0.8, 2.2, Math.sin(angle) * 0.8);
                    p.getWorld().spawnParticle(Particle.ENCHANT, c, 4, 0.05, 0.05, 0.05, 0.0);
                }
            }.runTaskTimer(this.plugin, 0L, 1L);
        }
    }

    private void activateSneezing(Player p) {
        Vector dir = p.getLocation().getDirection().normalize().multiply(2.5).setY(0.5);
        p.setVelocity(dir);
        this.setCooldown(p, NoseItem.NoseType.SNEEZING, this.plugin.getCooldownFor(NoseItem.NoseType.SNEEZING));
        p.sendMessage(this.plugin.bannerPrefix() + ChatColor.AQUA + "Sneezing Nose activated!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PANDA_SNEEZE, 1.0f, 1.0f);
        if (this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            // White, cloudy sneeze cone -- requested explicitly by the author.
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 60, 0.6, 0.5, 0.6, 0.1);
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 40, 0.6, 0.6, 0.6, 0.05);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0.0, 1.2, 0.0), 40, 0.5, 0.3, 0.5,
                new Particle.DustOptions(Color.WHITE, 1.4f));
            // Forward-cast puff trail that fades as the player launches.
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!p.isOnline() || this.ticks++ >= 10) {
                        this.cancel();
                        return;
                    }
                    p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0.0, 0.8, 0.0),
                        6, 0.3, 0.3, 0.3, 0.02);
                }
            }.runTaskTimer(this.plugin, 0L, 1L);
        }
    }

    private void activateSnotBubble(final Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 140, 1));
        this.setCooldown(p, NoseItem.NoseType.SNOT_BUBBLE, this.plugin.getCooldownFor(NoseItem.NoseType.SNOT_BUBBLE));
        p.sendMessage(this.plugin.bannerPrefix() + ChatColor.BLUE + "Snot Bubble activated! Resistance II for 7 seconds.");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.0f, 1.0f);
        if (!this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            return;
        }
        // Realistic shield burst: for the first second, paint a dense, visible
        // bubble dome around the player (fixed lat/long grid of pale-blue dust
        // + bubble-pop particles), accompanied by a shield-up sound.
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.8f);
        final Particle.DustOptions shellDust =
            new Particle.DustOptions(Color.fromRGB(180, 230, 255), 1.4f);
        final Particle.DustOptions rimDust =
            new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.1f);
        final double shieldRadius = 1.6;
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!p.isOnline() || this.ticks++ >= 20) {
                    if (p.isOnline()) {
                        // Pop sound as the shield dissolves into the ongoing bubble.
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.4f);
                        p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 1.0, 0), 1);
                    }
                    this.cancel();
                    return;
                }
                Location centre = p.getLocation().add(0.0, 1.0, 0.0);
                // lat/long grid for a clearly visible dome.
                int latSteps = 9;
                int lonSteps = 18;
                for (int lat = 0; lat < latSteps; lat++) {
                    double phi = Math.PI * (lat + 0.5) / latSteps;
                    double sinPhi = Math.sin(phi);
                    double cosPhi = Math.cos(phi);
                    for (int lon = 0; lon < lonSteps; lon++) {
                        double theta = 2 * Math.PI * lon / lonSteps;
                        double x = shieldRadius * sinPhi * Math.cos(theta);
                        double y = shieldRadius * cosPhi;
                        double z = shieldRadius * sinPhi * Math.sin(theta);
                        centre.getWorld().spawnParticle(Particle.DUST,
                            centre.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0, shellDust);
                    }
                }
                // Extra equatorial rim for a hard-edged "shield ring" look.
                int ringSteps = 32;
                double ringY = 0.0;
                for (int i = 0; i < ringSteps; i++) {
                    double theta = 2 * Math.PI * i / ringSteps;
                    centre.getWorld().spawnParticle(Particle.DUST,
                        centre.clone().add(
                            shieldRadius * Math.cos(theta), ringY, shieldRadius * Math.sin(theta)),
                        1, 0.0, 0.0, 0.0, 0.0, rimDust);
                }
                // Scatter bubble pops on the shell surface for depth.
                for (int i = 0; i < 8; i++) {
                    double th = 2 * Math.PI * WeaponListener.this.random.nextDouble();
                    double ph = Math.acos(2.0 * WeaponListener.this.random.nextDouble() - 1.0);
                    centre.getWorld().spawnParticle(Particle.BUBBLE_POP,
                        centre.clone().add(
                            shieldRadius * Math.sin(ph) * Math.cos(th),
                            shieldRadius * Math.cos(ph),
                            shieldRadius * Math.sin(ph) * Math.sin(th)),
                        1);
                }
            }
        }.runTaskTimer(this.plugin, 0L, 1L);

        // After the 1-second shield burst, the original ongoing bubble effect
        // continues for the remaining 6 seconds of Resistance II.
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.runSnotBubbleAmbient(p), 20L);
    }

    private void runSnotBubbleAmbient(final Player p) {
        if (!p.isOnline() || !this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            return;
        }
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!p.isOnline() || this.ticks++ >= 120) {
                    this.cancel();
                    return;
                }
                Location c = p.getLocation().add(0.0, 1.5, 0.0);
                for (int i = 0; i < 15; ++i) {
                    double th = Math.PI * 2 * WeaponListener.this.random.nextDouble();
                    double ph = Math.acos(2.0 * WeaponListener.this.random.nextDouble() - 1.0);
                    c.getWorld().spawnParticle(Particle.BUBBLE_POP,
                        c.clone().add(
                            1.2 * Math.sin(ph) * Math.cos(th),
                            1.2 * Math.sin(ph) * Math.sin(th),
                            1.2 * Math.cos(ph)),
                        1);
                }
                if (this.ticks % 3 == 0) {
                    p.getWorld().spawnParticle(Particle.BUBBLE, p.getLocation(), 2, 0.5, 0.2, 0.5, 0.02);
                    p.getWorld().spawnParticle(Particle.DUST, c, 2, 0.6, 0.6, 0.6,
                        new Particle.DustOptions(Color.fromRGB(80, 160, 255), 1.0f));
                }
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    private void activateEnderDragon(Player p) {
        this.setCooldown(p, NoseItem.NoseType.ENDER_DRAGON, this.plugin.getCooldownFor(NoseItem.NoseType.ENDER_DRAGON));
        Fireball fb = p.launchProjectile(Fireball.class);
        fb.setShooter((ProjectileSource) p);
        fb.setYield(4.5f);
        fb.setIsIncendiary(false);
        p.sendMessage(this.plugin.bannerPrefix() + ChatColor.DARK_PURPLE + "Ender Dragon fireball launched!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 1.2f);
        if (this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0.0, 1.0, 0.0),
                30, 0.25, 0.25, 0.25, 0.01);
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0.0, 1.0, 0.0),
                40, 0.5, 0.6, 0.5, 0.2);
            // Ongoing purple trail on the fireball itself.
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (fb.isDead() || this.ticks++ >= 60) {
                        this.cancel();
                        return;
                    }
                    fb.getWorld().spawnParticle(Particle.DRAGON_BREATH, fb.getLocation(),
                        3, 0.1, 0.1, 0.1, 0.0);
                    fb.getWorld().spawnParticle(Particle.DUST, fb.getLocation(), 2, 0.1, 0.1, 0.1,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 200), 1.2f));
                }
            }.runTaskTimer(this.plugin, 0L, 1L);
        }
    }

    // --- Booger Sniper charge lifecycle --------------------------------------

    private static final int TICKS_PER_SECOND = 20;
    private static final int MAX_CHARGE_TICKS = 5 * TICKS_PER_SECOND;

    /**
     * Per-level tuning: {damage, projectileCount, cooldownSeconds}. The default
     * max-charge cooldown is 40s; scaled down from there for lower charge
     * levels. Runtime overrides set via {@code /nosecooldown booger_sniper}
     * apply a proportional scale to each level.
     */
    private static final int[][] LEVEL_TUNING = {
        // Level 0 is "not charged enough" -- no fire, no cooldown.
        {0, 0, 0},
        {6, 1, 8},    // 1s: single small booger
        {12, 1, 14},  // 2s: medium booger
        {10, 3, 22},  // 3s: 3-pellet spread
        {20, 1, 30},  // 4s: big booger
        {16, 5, 40},  // 5s: shotgun blast of 5 pellets (max charge)
    };
    private static final int DEFAULT_MAX_COOLDOWN = 40;

    private enum ReleaseReason {
        TAP_FIRE, ITEM_SWAPPED, MAX_REACHED, CANCELLED
    }

    private final class BoogerCharge {
        final Player player;
        int ticks;
        BukkitRunnable task;
        boolean fired;

        BoogerCharge(Player player) {
            this.player = player;
        }

        void release(ReleaseReason reason) {
            if (this.fired) {
                return;
            }
            this.fired = true;
            if (this.task != null) {
                this.task.cancel();
            }
            WeaponListener.this.activeCharges.remove(this.player.getUniqueId());
            int level = Math.min(5, this.ticks / TICKS_PER_SECOND);
            WeaponListener.this.fireBooger(this.player, level, reason);
        }
    }

    private void beginBoogerCharge(Player player) {
        UUID id = player.getUniqueId();
        if (this.activeCharges.containsKey(id)) {
            return;
        }
        BoogerCharge charge = new BoogerCharge(player);
        this.activeCharges.put(id, charge);

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);

        charge.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    charge.release(ReleaseReason.CANCELLED);
                    return;
                }
                if (!WeaponListener.this.isHoldingBoogerSniper(player)) {
                    charge.release(ReleaseReason.ITEM_SWAPPED);
                    return;
                }
                charge.ticks++;
                WeaponListener.this.renderChargeActionBar(player, charge.ticks);
                WeaponListener.this.renderChargeParticles(player, charge.ticks);
                if (charge.ticks >= MAX_CHARGE_TICKS) {
                    charge.release(ReleaseReason.MAX_REACHED);
                }
            }
        };
        charge.task.runTaskTimer(this.plugin, 1L, 1L);
    }

    private void cancelBoogerChargeIfItemChanged(Player player) {
        BoogerCharge charge = this.activeCharges.get(player.getUniqueId());
        if (charge == null) {
            return;
        }
        // Defer one tick so the held-slot change is visible.
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!this.isHoldingBoogerSniper(player)) {
                charge.release(ReleaseReason.ITEM_SWAPPED);
            }
        }, 1L);
    }

    private boolean isHoldingBoogerSniper(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return NoseItem.isNoseItem(item, this.plugin)
            && NoseItem.getNoseType(item, this.plugin) == NoseItem.NoseType.BOOGER_SNIPER;
    }

    private void renderChargeActionBar(Player player, int ticks) {
        int level = Math.min(5, ticks / TICKS_PER_SECOND);
        int filled = Math.min(5, (ticks * 5) / MAX_CHARGE_TICKS);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            bar.append(i < filled ? "\u25B0" : "\u25B1"); // ▰ ▱
        }
        String label;
        NamedTextColor color;
        switch (level) {
            case 0 -> { label = "charging..."; color = NamedTextColor.GRAY; }
            case 1 -> { label = "small booger (1s)"; color = NamedTextColor.GREEN; }
            case 2 -> { label = "medium booger (2s)"; color = NamedTextColor.YELLOW; }
            case 3 -> { label = "scatter (3s)"; color = NamedTextColor.GOLD; }
            case 4 -> { label = "big booger (4s)"; color = NamedTextColor.RED; }
            default -> { label = "SHOTGUN BLAST (MAX)"; color = NamedTextColor.DARK_RED; }
        }
        player.sendActionBar(Component.text("Booger Sniper " + bar + " " + label).color(color));
    }

    private void renderChargeParticles(Player player, int ticks) {
        if (!this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            return;
        }
        Location at = player.getLocation().add(player.getLocation().getDirection().multiply(0.6)).add(0.0, 1.6, 0.0);
        int intensity = Math.min(5, ticks / TICKS_PER_SECOND) + 1;
        player.getWorld().spawnParticle(Particle.ITEM_SLIME, at,
            intensity, 0.12, 0.12, 0.12, 0.01);
        if (ticks % 5 == 0) {
            player.getWorld().spawnParticle(Particle.DUST, at, intensity, 0.15, 0.15, 0.15,
                new Particle.DustOptions(Color.fromRGB(90, 200, 80), 0.9f));
        }
    }

    private void fireBooger(Player player, int level, ReleaseReason reason) {
        if (level <= 0) {
            player.sendActionBar(Component.text("Charge released too early.").color(NamedTextColor.GRAY));
            return;
        }
        int[] tuning = LEVEL_TUNING[level];
        int damage = tuning[0];
        int count = tuning[1];
        int maxCooldown = this.plugin.getCooldownFor(NoseItem.NoseType.BOOGER_SNIPER);
        int cooldown = Math.max(1, Math.round(tuning[2] * (float) maxCooldown / DEFAULT_MAX_COOLDOWN));

        // Spread widens with level 3 (scatter) and level 5 (shotgun).
        double spread = switch (level) {
            case 3 -> 0.18;
            case 5 -> 0.25;
            default -> 0.02;
        };
        Vector baseDir = player.getLocation().getDirection().normalize();

        for (int i = 0; i < count; i++) {
            Vector dir = baseDir.clone();
            if (count > 1) {
                dir.add(new Vector(
                    (this.random.nextDouble() - 0.5) * spread * 2,
                    (this.random.nextDouble() - 0.5) * spread,
                    (this.random.nextDouble() - 0.5) * spread * 2
                )).normalize();
            }
            final Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setShooter((ProjectileSource) player);
            arrow.setDamage(damage);
            arrow.setKnockbackStrength(level >= 4 ? 2 : 1);
            arrow.setCritical(level >= 2);
            arrow.setVelocity(dir.multiply(level >= 5 ? 2.6 : 3.0));
            if (this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
                this.trailProjectile(arrow);
            }
        }

        this.setCooldown(player, NoseItem.NoseType.BOOGER_SNIPER, cooldown);

        String msg;
        Sound sound;
        float pitch;
        if (level >= 5) {
            msg = ChatColor.DARK_RED + "SHOTGUN BLAST! " + count + " boogers fired.";
            sound = Sound.ENTITY_GENERIC_EXPLODE;
            pitch = 1.4f;
        } else if (level == 4) {
            msg = ChatColor.RED + "Big Booger launched!";
            sound = Sound.ENTITY_SNOWBALL_THROW;
            pitch = 0.8f;
        } else if (level == 3) {
            msg = ChatColor.GOLD + "Booger scatter fired!";
            sound = Sound.ENTITY_SNOWBALL_THROW;
            pitch = 1.0f;
        } else if (level == 2) {
            msg = ChatColor.YELLOW + "Medium booger fired!";
            sound = Sound.ENTITY_SNOWBALL_THROW;
            pitch = 1.1f;
        } else {
            msg = ChatColor.GREEN + "Small booger fired!";
            sound = Sound.ENTITY_SNOWBALL_THROW;
            pitch = 1.3f;
        }
        player.sendMessage(this.plugin.bannerPrefix() + msg + ChatColor.DARK_GRAY
            + " (cooldown: " + cooldown + "s)");
        player.getWorld().playSound(player.getLocation(), sound, 1.4f, pitch);
        if (this.plugin.isFeatureEnabled(Feature.PARTICLES)) {
            player.getWorld().spawnParticle(Particle.ITEM_SLIME, player.getLocation().add(0.0, 1.2, 0.0),
                20 + level * 8, 0.4, 0.4, 0.4, 0.1);
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0.0, 1.4, 0.0),
                20 + level * 6, 0.5, 0.5, 0.5,
                new Particle.DustOptions(Color.fromRGB(90, 200, 80), 1.3f));
        }
    }

    private void trailProjectile(final Arrow arrow) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround() || this.ticks++ >= 60) {
                    this.cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(Particle.ITEM_SLIME, arrow.getLocation(),
                    3, 0.1, 0.1, 0.1, 0.02);
                arrow.getWorld().spawnParticle(Particle.COMPOSTER, arrow.getLocation(),
                    2, 0.1, 0.1, 0.1, 0.01);
                arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(), 2, 0.05, 0.05, 0.05,
                    new Particle.DustOptions(Color.fromRGB(90, 200, 80), 0.9f));
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    // --- Cooldown helpers ----------------------------------------------------

    public boolean isOnCooldown(Player p, NoseItem.NoseType t) {
        return this.cooldowns.getOrDefault(p.getUniqueId(), Map.of())
            .getOrDefault(t, 0L) > System.currentTimeMillis();
    }

    public long getCooldownRemaining(Player p, NoseItem.NoseType t) {
        return Math.max(0L,
            (this.cooldowns.getOrDefault(p.getUniqueId(), Map.of()).getOrDefault(t, 0L)
                - System.currentTimeMillis()) / 1000L);
    }

    private void setCooldown(Player p, NoseItem.NoseType t, int seconds) {
        this.cooldowns
            .computeIfAbsent(p.getUniqueId(), u -> new EnumMap<>(NoseItem.NoseType.class))
            .put(t, System.currentTimeMillis() + seconds * 1000L);
    }

    /** Exposed for administrative callers that need to clear a player's cooldowns. */
    public void clearCooldowns(Player p) {
        this.cooldowns.remove(p.getUniqueId());
    }
}
