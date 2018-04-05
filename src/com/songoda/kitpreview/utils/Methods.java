package com.songoda.kitpreview.utils;

import com.songoda.arconix.Arconix;
import com.songoda.kitpreview.KitPreview;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;

/**
 * Created by songoda on 2/24/2017.
 */
public class Methods {
    
    public static ItemStack getGlass() {
        KitPreview plugin = KitPreview.getInstance();
        return Arconix.pl().getGUI().getGlass(plugin.getConfig().getBoolean("Interfaces.Replace Glass Type 1 With Rainbow Glass"), plugin.getConfig().getInt("Interfaces.Glass Type 1"));
    }

    public static ItemStack getBackgroundGlass(boolean type) {
        KitPreview plugin = KitPreview.getInstance();
        if (type)
            return Arconix.pl().getGUI().getGlass(false, plugin.getConfig().getInt("Interfaces.Glass Type 2"));
        else
            return Arconix.pl().getGUI().getGlass(false, plugin.getConfig().getInt("Interfaces.Glass Type 3"));
    }

    public static void fillGlass(Inventory i) {
        int nu = 0;
        while (nu != 27) {
            ItemStack glass = getGlass();
            i.setItem(nu, glass);
            nu++;
        }
    }

    public static boolean canGiveKit(Player player) {
        try {
            if (player.hasPermission("ultimatekits.cangive")) return true;

            if (player.hasPermission("essentials.kit.others")) return true;
        } catch (Exception e) {
            Debugger.runReport(e);
        }
        return false;
    }

    public static boolean doesKitExist(String kit) {
        return KitPreview.getInstance().getKitFile().getConfig().contains("Kits." + kit);
    }

    public static List<String> getKits() {
        List<String> kits = new ArrayList<>();

        for (String key : KitPreview.getInstance().getKitFile().getConfig().getConfigurationSection("Kits").getKeys(false)) {
            kits.add(key.replace("Kits.", "").trim());
        }
        return kits;
    }

    public static Collection<Entity> getNearbyEntities(Location location, double x, double y, double z) {
        if (!KitPreview.getInstance().v1_7) return location.getWorld().getNearbyEntities(location,x,y,z);

        if (location == null) return Collections.emptyList();

        World world = location.getWorld();
        net.minecraft.server.v1_7_R4.AxisAlignedBB aabb = net.minecraft.server.v1_7_R4.AxisAlignedBB
                .a(location.getX() - x, location.getY() - y, location.getZ() - z, location.getX() + x, location.getY() + y, location.getZ() + z);
        List<net.minecraft.server.v1_7_R4.Entity> entityList = ((org.bukkit.craftbukkit.v1_7_R4.CraftWorld) world).getHandle().getEntities(null, aabb, null);
        List<Entity> bukkitEntityList = new ArrayList<>();

        for (Object entity : entityList) {
            bukkitEntityList.add(((net.minecraft.server.v1_7_R4.Entity) entity).getBukkitEntity());
        }

        return bukkitEntityList;
    }

    public static boolean pay(Player p, double amount) {
        if (KitPreview.getInstance().getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = KitPreview.getInstance().getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);

        net.milkbowl.vault.economy.Economy econ = rsp.getProvider();

        econ.depositPlayer(p, amount);
        return true;
    }

    public static String serializeItemStack(ItemStack item) {
        String str = item.getType().name();
        if (item.getDurability() != 0)
            str += ":" + item.getDurability();

        str += " " + item.getAmount();

        if (item.hasItemMeta()) {
            if (item.getItemMeta().hasDisplayName())
                str += " name:" + fixLine(item.getItemMeta().getDisplayName());
            if (item.getItemMeta().hasLore()) {
                str += " lore:";
                int num = 0;
                for (String line : item.getItemMeta().getLore()) {
                    num ++;
                    str += fixLine(line);
                    if (item.getItemMeta().getLore().size() != num)
                        str += "|";
                }
            }
        }

        if (item.getType() == Material.SKULL_ITEM) {
            str += " owner:" + ((SkullMeta)item.getItemMeta()).getOwner();
        }

        if (item.getType() == Material.WRITTEN_BOOK) {

        }

        for (Enchantment ench : item.getEnchantments().keySet()) {
            str += " " + ench.getName() + ":" + item.getEnchantmentLevel(ench);
        }
        return str.replace("§", "&");
    }

    public static ItemStack deserializeItemStack(String string) {
        string = string.replace("&", "§");
        String[] splited = string.split("\\s+");

        String[] val = splited[0].split(":");
        ItemStack item;
        if (Arconix.pl().doMath().isNumeric(val[0])) {
            item = new ItemStack(Integer.parseInt(val[0]));
        } else {
            item = new ItemStack(Material.valueOf(val[0]));
        }

        ItemMeta meta = item.getItemMeta();

        if (val.length == 2) {
            item.setDurability(Short.parseShort(val[1]));
        }
        if (splited.length >= 2) {
            if (Arconix.pl().doMath().isNumeric(splited[1])) {
                item.setAmount(Integer.parseInt(splited[1]));
            }

            for (String str : splited) {
                str = unfixLine(str);
                if (str.contains(":")) {
                    if (str.substring(0, 5).equalsIgnoreCase("name:")) {
                        meta.setDisplayName(str.substring(5));
                    } else if (str.substring(0, 5).equalsIgnoreCase("lore:")) {
                        String[] parts = str.substring(5).split("\\|");
                        ArrayList<String> lore = new ArrayList<>();
                        for (String line : parts)
                            lore.add(Arconix.pl().format().formatText(line));
                        meta.setLore(lore);
                    } else if (str.substring(0, 6).equalsIgnoreCase("owner:")) {
                        if (item.getType() == Material.SKULL_ITEM)
                            ((SkullMeta)meta).setOwner(str.substring(6));
                    }
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    public static String fixLine(String line) {
        line = line.replace(" ", "_");
        return line;
    }

    public static String unfixLine(String line) {
        line = line.replace("_", " ");
        return line;
    }

}
