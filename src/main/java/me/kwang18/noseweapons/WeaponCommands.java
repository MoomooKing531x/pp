package me.kwang18.noseweapons;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WeaponCommands implements CommandExecutor {
    private final NoseWeapons plugin;

    public WeaponCommands(NoseWeapons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        return switch (name) {
            case "givenose"     -> this.giveWeapon(sender, args);
            case "givenoseitem" -> this.giveNoseItem(sender, args);
            case "nosecrafting" -> this.toggleCrafting(sender, args);
            case "nosetoggle"   -> this.toggleFeature(sender, args);
            case "nosecooldown" -> this.manageCooldown(sender, args);
            case "nosehelp"     -> this.showHelp(sender);
            default -> false;
        };
    }

    private boolean manageCooldown(CommandSender sender, String[] args) {
        if (!sender.hasPermission("noseweapons.admin")) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            this.listCooldowns(sender);
            return true;
        }
        String first = args[0].toLowerCase();

        // /nosecooldown reset                -> reset every nose to default
        // /nosecooldown reset <type>         -> reset one nose
        if (first.equals("reset")) {
            if (args.length == 1) {
                for (NoseItem.NoseType t : NoseItem.NoseType.values()) {
                    this.plugin.resetCooldownOverride(t);
                }
                sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN
                    + "Reset every nose cooldown to default.");
                this.listCooldowns(sender);
                return true;
            }
            NoseItem.NoseType resetType = NoseItem.resolve(args[1]);
            if (resetType == null) {
                sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                    + "Unknown nose: " + args[1]);
                this.listCooldowns(sender);
                return true;
            }
            this.plugin.resetCooldownOverride(resetType);
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN
                + "Reset " + resetType.configKey() + " cooldown to "
                + resetType.cooldownSeconds + "s (default).");
            return true;
        }

        // /nosecooldown list
        if (first.equals("list")) {
            this.listCooldowns(sender);
            return true;
        }

        // /nosecooldown <type> <seconds|reset>
        NoseItem.NoseType type = NoseItem.resolve(first);
        if (type == null) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                + "Unknown nose: " + args[0]);
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GRAY
                + "Usage: /nosecooldown <type> <seconds|reset>  |  /nosecooldown reset [type]  |  /nosecooldown list");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                + "Missing argument. Usage: /nosecooldown " + type.configKey() + " <seconds|reset>");
            return true;
        }
        if (args[1].equalsIgnoreCase("reset")) {
            this.plugin.resetCooldownOverride(type);
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN
                + "Reset " + type.configKey() + " cooldown to "
                + type.cooldownSeconds + "s (default).");
            return true;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                + "Seconds must be a non-negative integer (or 'reset'). Got: " + args[1]);
            return true;
        }
        if (seconds < 0) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                + "Seconds must be >= 0.");
            return true;
        }
        this.plugin.setCooldownOverride(type, seconds);
        sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN
            + "Set " + type.configKey() + " cooldown to " + seconds + "s (was "
            + type.cooldownSeconds + "s default).");
        if (type == NoseItem.NoseType.BOOGER_SNIPER) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GRAY
                + "Booger Sniper per-level cooldowns scale from this max charge value.");
        }
        return true;
    }

    private void listCooldowns(CommandSender sender) {
        sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GOLD + "Nose cooldowns (seconds):");
        for (NoseItem.NoseType t : NoseItem.NoseType.values()) {
            Integer override = this.plugin.getCooldownOverride(t);
            int current = this.plugin.getCooldownFor(t);
            String tag = override == null ? ChatColor.GREEN + "[default]"
                                          : ChatColor.AQUA + "[override]";
            sender.sendMessage(" " + tag + ChatColor.RESET + " "
                + ChatColor.YELLOW + t.configKey()
                + ChatColor.GRAY + " -> "
                + ChatColor.WHITE + current + "s"
                + ChatColor.DARK_GRAY + " (default " + t.cooldownSeconds + "s)");
        }
        sender.sendMessage(ChatColor.GRAY + "Usage: /nosecooldown <type> <seconds|reset>, /nosecooldown reset [type], /nosecooldown list");
    }

    private boolean giveWeapon(CommandSender sender, String[] args) {
        if (!sender.hasPermission("noseweapons.give")) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                + "Usage: /givenose <bloody|snotty|snoring|sneezing|snot_bubble|ender_dragon|booger_sniper> [player]");
            return true;
        }
        NoseItem.NoseType type;
        try {
            type = NoseItem.NoseType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Invalid type.");
            return true;
        }
        Player target = this.resolveTarget(sender, args, 1);
        if (target == null) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Player not found.");
            return true;
        }
        target.getInventory().addItem(NoseItem.createNose(type, this.plugin));
        target.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN + "You received a "
            + type.displayName + ChatColor.GREEN + "!");
        if (!target.equals(sender)) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN + "Gave "
                + type.displayName + " to " + target.getName());
        }
        return true;
    }

    private boolean giveNoseItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("noseweapons.give")) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                + "Usage: /givenoseitem <villager|warden|wither|panda|spider|elderguardian|sniffer|enderdragon> [player]");
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GRAY
                + "Aliases: elder, sniff, dragon, ender");
            return true;
        }
        NoseIngredient.Def def = NoseIngredient.resolve(args[0]);
        if (def == null) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                + "Invalid nose type. Available: "
                + String.join(", ", NoseIngredient.DEFS.keySet()));
            return true;
        }
        Player target = this.resolveTarget(sender, args, 1);
        if (target == null) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Player not found.");
            return true;
        }
        ItemStack stack = NoseIngredient.create(def, this.plugin);
        target.getInventory().addItem(stack);
        target.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN
            + "You received " + stack.getItemMeta().getDisplayName() + ChatColor.GREEN + "!");
        if (!target.equals(sender)) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN + "Gave "
                + stack.getItemMeta().getDisplayName() + " to " + target.getName());
        }
        return true;
    }

    private boolean toggleCrafting(CommandSender sender, String[] args) {
        if (!sender.hasPermission("noseweapons.admin")) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "You don't have permission!");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Usage: /nosecrafting <true|false>");
            return true;
        }
        String raw = args[0].toLowerCase();
        if (raw.equals("true") || raw.equals("on") || raw.equals("enable")) {
            this.plugin.setCraftingEnabled(true);
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GREEN + "Nose crafting enabled.");
            return true;
        }
        if (raw.equals("false") || raw.equals("off") || raw.equals("disable")) {
            this.plugin.setCraftingEnabled(false);
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Nose crafting disabled.");
            return true;
        }
        sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Invalid argument. Use true or false.");
        return true;
    }

    private boolean toggleFeature(CommandSender sender, String[] args) {
        if (!sender.hasPermission("noseweapons.admin")) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 1) {
            this.describeFeatures(sender);
            return true;
        }
        Feature feature = Feature.fromKey(args[0]);
        if (feature == null) {
            sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED + "Unknown feature: " + args[0]);
            this.describeFeatures(sender);
            return true;
        }

        boolean newState;
        if (args.length >= 2) {
            String raw = args[1].toLowerCase();
            if (raw.equals("on") || raw.equals("true") || raw.equals("enable")) {
                newState = true;
            } else if (raw.equals("off") || raw.equals("false") || raw.equals("disable")) {
                newState = false;
            } else {
                sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.RED
                    + "Use on/off (or true/false). Got: " + args[1]);
                return true;
            }
        } else {
            newState = !this.plugin.isFeatureEnabled(feature);
        }

        this.plugin.setFeatureEnabled(feature, newState);
        sender.sendMessage(this.plugin.bannerPrefix()
            + (newState ? ChatColor.GREEN + "Enabled " : ChatColor.RED + "Disabled ")
            + ChatColor.RESET + feature.key()
            + ChatColor.GRAY + " -- " + feature.description());
        return true;
    }

    private void describeFeatures(CommandSender sender) {
        sender.sendMessage(this.plugin.bannerPrefix() + ChatColor.GOLD + "Feature flags:");
        for (Feature f : Feature.values()) {
            boolean on = this.plugin.isFeatureEnabled(f);
            sender.sendMessage(" " + (on ? ChatColor.GREEN + "[ON] " : ChatColor.RED + "[OFF] ")
                + ChatColor.YELLOW + f.key() + ChatColor.GRAY + " -- " + f.description());
        }
        sender.sendMessage(ChatColor.GRAY + "Usage: /nosetoggle <feature> [on|off]");
    }

    private boolean showHelp(CommandSender sender) {
        List<String> lines = new ArrayList<>();
        lines.add(this.plugin.bannerPrefix() + ChatColor.GOLD + "NoseWeapons commands:");
        lines.add(ChatColor.YELLOW + "/givenose <type> [player]"
            + ChatColor.GRAY + " -- give a nose weapon (types: bloody, snotty, snoring, sneezing, snot_bubble, ender_dragon, booger_sniper)");
        lines.add(ChatColor.YELLOW + "/givenoseitem <type> [player]"
            + ChatColor.GRAY + " -- give a shear ingredient (types: villager, warden, wither, panda, spider, elderguardian, sniffer, enderdragon)");
        lines.add(ChatColor.YELLOW + "/nosecrafting <true|false>"
            + ChatColor.GRAY + " -- toggle shaped recipes");
        lines.add(ChatColor.YELLOW + "/nosetoggle <feature> [on|off]"
            + ChatColor.GRAY + " -- toggle a named feature (list with no args)");
        lines.add(ChatColor.YELLOW + "/nosecooldown <type> <seconds|reset>"
            + ChatColor.GRAY + " -- override per-weapon cooldown; 'reset' or '/nosecooldown reset' restores defaults");
        lines.add(ChatColor.YELLOW + "/nosehelp"
            + ChatColor.GRAY + " -- show this list");
        lines.add(ChatColor.GOLD + "Gameplay tips:");
        lines.add(ChatColor.GRAY + " - Shift + Right-click a nose weapon to activate its ability.");
        lines.add(ChatColor.GRAY + " - Booger Sniper: Shift + Right-click to begin charging, Right-click again to fire (1-5s).");
        lines.add(ChatColor.GRAY + " - Crouch + Shear a supported mob under 50% HP to harvest its nose.");
        lines.forEach(sender::sendMessage);
        return true;
    }

    private Player resolveTarget(CommandSender sender, String[] args, int nameIndex) {
        if (args.length > nameIndex) {
            return Bukkit.getPlayer(args[nameIndex]);
        }
        return sender instanceof Player player ? player : null;
    }
}
