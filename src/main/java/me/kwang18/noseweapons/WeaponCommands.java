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
            case "nosehelp"     -> this.showHelp(sender);
            default -> false;
        };
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
        lines.add(ChatColor.YELLOW + "/nosehelp"
            + ChatColor.GRAY + " -- show this list");
        lines.add(ChatColor.GOLD + "Gameplay tips:");
        lines.add(ChatColor.GRAY + " - Shift + Right-click a nose weapon to activate its ability.");
        lines.add(ChatColor.GRAY + " - Booger Sniper: hold Shift + Right-click to charge, release to fire (1-5s).");
        lines.add(ChatColor.GRAY + " - Crouch + Shear a mob (or player) under 50% HP to harvest their nose.");
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
