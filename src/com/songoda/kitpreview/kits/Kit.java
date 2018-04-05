package com.songoda.kitpreview.kits;

import com.songoda.arconix.Arconix;
import com.songoda.arconix.method.formatting.TextComponent;
import com.songoda.kitpreview.KitPreview;
import com.songoda.kitpreview.Lang;
import com.songoda.kitpreview.utils.ConfigWrapper;
import com.songoda.kitpreview.utils.Debugger;
import com.songoda.kitpreview.utils.Methods;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by songoda on 2/24/2017.
 */
public class Kit {
    Location location = null;
    String locationStr = null;
    private String name = null;
    private String showableName = null;

    public Kit(String name) {
        this.name = name;
        this.showableName = TextComponent.formatText(name, true);
    }

    /*
    Doesn't Exist
     */
    public Kit(Location location) {
        extractKit(location);
    }

    public Kit(Block b) {
        extractKit(b.getLocation());
    }

    private void extractKit(Location location) {
        this.locationStr = Arconix.pl().serialize().serializeLocation(location);
        this.location = location;
        name = KitPreview.getInstance().getConfig().getString("data.block." + locationStr);
        showableName = TextComponent.formatText(name, true);
    }
    /*
    End Doesn't Exist (Remove this trash ^)
     */

    public void removeKitFromBlock(Player p) {
        try {
            removeDisplayItems();

            String kit = KitPreview.getInstance().getConfig().getString("data.block." + locationStr);
            KitPreview.getInstance().getConfig().set("data.holo." + locationStr, null);
            KitPreview.getInstance().getConfig().set("data.particles." + locationStr, null);
            KitPreview.getInstance().getConfig().set("data.displayitems." + locationStr, null);
            KitPreview.getInstance().saveConfig();
            KitPreview.getInstance().holo.updateHolograms();
            KitPreview.getInstance().getConfig().set("data.block." + locationStr, null);
            KitPreview.getInstance().saveConfig();
            p.sendMessage(TextComponent.formatText(KitPreview.getInstance().references.getPrefix() + "&8Kit &9" + kit + " &8unassigned from: &a" + location.getBlock().getType().toString() + "&8."));
        } catch (Exception ex) {
            Debugger.runReport(ex);
        }
    }

    public void buy(Player p) {
        try {
            if (hasPermission(p) && KitPreview.getInstance().getConfig().getBoolean("Main.Allow Players To Receive Kits For Free If They Have Permission")) {
                give(p, false, false, false);
                return;
            }
            if (KitPreview.getInstance().getConfig().getString("data.kit." + showableName + ".link") != null) {
                String lin = KitPreview.getInstance().getConfig().getString("data.kit." + name + ".link");
                p.sendMessage("");
                p.sendMessage(KitPreview.getInstance().references.getPrefix() + TextComponent.formatText("&a" + lin));
                p.sendMessage("");
                p.closeInventory();
            } else if (KitPreview.getInstance().getConfig().getString("data.kit." + name + ".eco") != null) {
                Buy.confirmBuy(name, p);
            } else {
                p.sendMessage(Lang.NO_PERM.getConfigValue());
            }
        } catch (Exception ex) {
            Debugger.runReport(ex);
        }
    }

    public void give(Player p, boolean key, boolean economy, boolean console) {
        try {
            if (KitPreview.getInstance().getConfig().getBoolean("Main.Sounds Enabled")) {
                if (!KitPreview.getInstance().v1_8 && !KitPreview.getInstance().v1_7) {
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 15.0F);
                } else {
                    p.playSound(p.getLocation(), Sound.valueOf("LEVEL_UP"), 2F, 15.0F);
                }
            }
            if (key) {
                if (p.getItemInHand().getType() != Material.TRIPWIRE_HOOK || !p.getItemInHand().hasItemMeta()) {
                    return;
                }
                if (!p.getItemInHand().getItemMeta().getDisplayName().equals(Lang.KEY_TITLE.getConfigValue(showableName))) {
                    p.sendMessage(TextComponent.formatText(KitPreview.getInstance().references.getPrefix() + Lang.WRONG_KEY.getConfigValue()));
                    return;
                }
                if (ChatColor.stripColor(p.getItemInHand().getItemMeta().getLore().get(0)).equals("Regular Key")) {
                    givePartKit(p, keyReward());
                } else {
                    giveKit(p);
                }
                p.sendMessage(KitPreview.getInstance().references.getPrefix() + TextComponent.formatText(Lang.KEY_SUCCESS.getConfigValue(showableName)));
                if (p.getInventory().getItemInHand().getAmount() != 1) {
                    ItemStack is = p.getItemInHand();
                    is.setAmount(is.getAmount() - 1);
                    p.setItemInHand(is);
                } else {
                    p.setItemInHand(null);
                }
            } else {
                long delay = getNextUse(p) - System.currentTimeMillis(); // gets delay

                if (getNextUse(p) == -1 && !economy && !console) {
                    p.sendMessage(KitPreview.getInstance().references.getPrefix() + TextComponent.formatText(Lang.NOT_TWICE.getConfigValue(showableName)));
                } else if (delay <= 0 || economy || console) {
                    giveKit(p);
                    if (economy) {
                        p.sendMessage(KitPreview.getInstance().references.getPrefix() + TextComponent.formatText(Lang.PURCHASE_SUCCESS.getConfigValue(showableName)));
                    } else {
                        updateDelay(p);
                        p.sendMessage(KitPreview.getInstance().references.getPrefix() + TextComponent.formatText(Lang.GIVE_SUCCESS.getConfigValue(showableName)));
                    }
                } else {
                    p.sendMessage(KitPreview.getInstance().references.getPrefix() + TextComponent.formatText(Lang.DELAY.getConfigValue(Arconix.pl().format().readableTime(delay))));
                }
            }
        } catch (Exception ex) {
            Debugger.runReport(ex);
        }

    }

    public void display(Player p, boolean back) {
        try {
            if (!p.hasPermission("previewkit.use")
                    && !p.hasPermission("previewkit." + name)
                    && !p.hasPermission("kitpreview.use")
                    && !p.hasPermission("kitpreview." + name)) {
                p.sendMessage(KitPreview.getInstance().references.getPrefix() + Lang.NO_PERM.getConfigValue());
                return;
            }
            if (name == null) {
                p.sendMessage(KitPreview.getInstance().references.getPrefix() + Lang.KIT_DOESNT_EXIST.getConfigValue(showableName));
                return;
            }
            KitPreview.getInstance().inKit.put(p.getUniqueId(), this);
            p.sendMessage(KitPreview.getInstance().references.getPrefix() + Lang.PREVIEWING_KIT.getConfigValue(showableName));
            String guititle = Arconix.pl().format().formatTitle(Lang.PREVIEW_TITLE.getConfigValue(showableName));
            if (KitPreview.getInstance().getConfig().getString("data.kit." + name + ".title") != null) {
                String kitTitle = KitPreview.getInstance().getConfig().getString("data.kit." + name + ".title");
                guititle = Lang.PREVIEW_TITLE.getConfigValue(TextComponent.formatText(kitTitle, true));
            }

            guititle = TextComponent.formatText(guititle);

            List<ItemStack> list = getReadableContents(p, true);

            int amt = 0;
            for (ItemStack is : list) {
                if (is.getAmount() > 64) {
                    int overflow = is.getAmount() % 64;
                    int stackamt = is.getAmount() / 64;
                    int num3 = 0;
                    while (num3 != stackamt) {
                        amt++;
                        num3++;
                    }
                    if (overflow != 0) {
                        amt++;
                    }

                } else {
                    amt++;
                }
            }
            boolean buyable = false;
            if (KitPreview.getInstance().getConfig().getString("data.kit." + name + ".link") != null || KitPreview.getInstance().getConfig().getString("data.kit." + name + ".eco") != null) {
                buyable = true;
            }
            int min = 0;
            if (KitPreview.getInstance().getConfig().getBoolean("Interfaces.Do Not Use Glass Borders")) {
                min = 9;
                if (!buyable) {
                    min = min + 9;
                }
            }
            Inventory i = Bukkit.createInventory(null, 54 - min, Arconix.pl().format().formatTitle(guititle));
            int max = 54 - min;
            if (amt <= 7) {
                i = Bukkit.createInventory(null, 27 - min, Arconix.pl().format().formatTitle(guititle));
                max = 27 - min;
            } else if (amt <= 15) {
                i = Bukkit.createInventory(null, 36 - min, Arconix.pl().format().formatTitle(guititle));
                max = 36 - min;
            } else if (amt <= 25) {
                i = Bukkit.createInventory(null, 45 - min, Arconix.pl().format().formatTitle(guititle));
                max = 45 - min;
            }


            int num = 0;
            if (!KitPreview.getInstance().getConfig().getBoolean("Interfaces.Do Not Use Glass Borders")) {
                ItemStack exit = new ItemStack(Material.valueOf(KitPreview.getInstance().getConfig().getString("Interfaces.Exit Icon")), 1);
                ItemMeta exitmeta = exit.getItemMeta();
                exitmeta.setDisplayName(Lang.EXIT.getConfigValue());
                exit.setItemMeta(exitmeta);
                while (num != 10) {
                    i.setItem(num, Methods.getGlass());
                    num++;
                }
                int num2 = max - 10;
                while (num2 != max) {
                    i.setItem(num2, Methods.getGlass());
                    num2++;
                }
                i.setItem(8, exit);


                i.setItem(0, Methods.getBackgroundGlass(true));
                i.setItem(1, Methods.getBackgroundGlass(true));
                i.setItem(9, Methods.getBackgroundGlass(true));

                i.setItem(7, Methods.getBackgroundGlass(true));
                i.setItem(17, Methods.getBackgroundGlass(true));

                i.setItem(max - 18, Methods.getBackgroundGlass(true));
                i.setItem(max - 9, Methods.getBackgroundGlass(true));
                i.setItem(max - 8, Methods.getBackgroundGlass(true));

                i.setItem(max - 10, Methods.getBackgroundGlass(true));
                i.setItem(max - 2, Methods.getBackgroundGlass(true));
                i.setItem(max - 1, Methods.getBackgroundGlass(true));

                i.setItem(2, Methods.getBackgroundGlass(false));
                i.setItem(6, Methods.getBackgroundGlass(false));
                i.setItem(max - 7, Methods.getBackgroundGlass(false));
                i.setItem(max - 3, Methods.getBackgroundGlass(false));
            }

            if (buyable) {
                ItemStack link = new ItemStack(Material.valueOf(KitPreview.getInstance().getConfig().getString("Interfaces.Buy Icon")), 1);
                ItemMeta linkmeta = link.getItemMeta();
                linkmeta.setDisplayName(Lang.BUYNOW.getConfigValue());
                ArrayList<String> lore = new ArrayList<>();
                if (KitPreview.getInstance().getConfig().getString("data.kit." + name + ".link") != null) {
                    lore.add(Lang.CLICKLINK.getConfigValue());
                } else {
                    Double cost = KitPreview.getInstance().getConfig().getDouble("data.kit." + name + ".eco");
                    if (hasPermission(p) && KitPreview.getInstance().getConfig().getBoolean("Main.Allow Players To Receive Kits For Free If They Have Permission")) {
                        lore.add(Lang.CLICKECO.getConfigValue("0"));
                        if (p.isOp()) {
                            lore.add("");
                            lore.add(TextComponent.formatText("&7This is free because"));
                            lore.add(TextComponent.formatText("&7you have perms for it."));
                            lore.add(TextComponent.formatText("&7Everyone else buys"));
                            lore.add(TextComponent.formatText("&7this for &a$" + Arconix.pl().format().formatEconomy(cost) + "&7."));
                        }
                    } else {
                        lore.add(Lang.CLICKECO.getConfigValue(Arconix.pl().format().formatEconomy(cost)));
                    }
                    if (KitPreview.getInstance().getConfig().getString("data.kit." + name + ".delay") != null && p.isOp()) {
                        lore.add("");
                        lore.add(TextComponent.formatText("&7You do not have a delay"));
                        lore.add(TextComponent.formatText("&7because you have perms"));
                        lore.add(TextComponent.formatText("&7to bypass the delay."));
                    }
                }
                linkmeta.setLore(lore);
                link.setItemMeta(linkmeta);
                i.setItem(max - 5, link);
            }

            for (ItemStack is : list) {
                if (!KitPreview.getInstance().getConfig().getBoolean("Interfaces.Do Not Use Glass Borders")) {
                    if (num == 17)
                        num++;
                    if (num == (max - 18))
                        num++;
                }
                if (is.getAmount() > 64) {
                    if (is.hasItemMeta() && is.getItemMeta().hasLore()) {
                        ArrayList<String> lore = new ArrayList<>();
                        for (String str : is.getItemMeta().getLore()) {
                            str = str.replace("{PLAYER}", p.getName());
                            str = str.replace("<PLAYER>", p.getName());
                            lore.add(str);
                        }
                        is.getItemMeta().setLore(lore);

                    }
                    int overflow = is.getAmount() % 64;
                    int stackamt = is.getAmount() / 64;
                    int num3 = 0;
                    while (num3 != stackamt) {
                        ItemStack is2 = is;
                        is2.setAmount(64);
                        i.setItem(num, is2);
                        num++;
                        num3++;
                    }
                    if (overflow != 0) {
                        ItemStack is2 = is;
                        is2.setAmount(overflow);
                        i.setItem(num, is2);
                        num++;
                    }
                    continue;
                }
                if (!KitPreview.getInstance().getConfig().getBoolean("Main.Dont Preview Commands In Kits") || is.getType() != Material.PAPER || !is.getItemMeta().hasDisplayName() || !is.getItemMeta().getDisplayName().equals(Lang.COMMAND.getConfigValue())) {
                    i.setItem(num, is);
                    num++;
                }
            }

            if (back && !KitPreview.getInstance().getConfig().getBoolean("Interfaces.Do Not Use Glass Borders")) {

                ItemStack head2 = new ItemStack(Material.SKULL_ITEM, 1, (byte) 3);
                ItemStack skull2 = head2;
                if (!KitPreview.getInstance().v1_7)
                    skull2 = Arconix.pl().getGUI().addTexture(head2, "http://textures.minecraft.net/texture/3ebf907494a935e955bfcadab81beafb90fb9be49c7026ba97d798d5f1a23");
                SkullMeta skull2Meta = (SkullMeta) skull2.getItemMeta();
                if (KitPreview.getInstance().v1_7)
                    skull2Meta.setOwner("MHF_ArrowLeft");
                skull2.setDurability((short) 3);
                skull2Meta.setDisplayName(Lang.BACK.getConfigValue());
                skull2.setItemMeta(skull2Meta);
                i.setItem(0, skull2);
            }

            p.openInventory(i);
            KitPreview.getInstance().whereAt.remove(p.getUniqueId());
            KitPreview.getInstance().whereAt.put(p.getUniqueId(), "display");
        } catch (Exception ex) {
            Debugger.runReport(ex);
        }

    }

    public void saveKit(List<ItemStack> items) {
        try {
            List<String> list = new ArrayList<>();
            for (ItemStack is : items) {
                if (is != null && is.getType() != null && is.getType() != Material.AIR) {
                    if (is.getType() == Material.PAPER && ChatColor.stripColor(is.getItemMeta().getDisplayName()).equals("Command")) {
                        String command = "";
                        for (String line : is.getItemMeta().getLore()) {
                            command += line;
                        }
                        list.add(ChatColor.stripColor(command));
                    } else {
                        String serialized = Methods.serializeItemStack(is);
                        list.add(serialized);
                    }
                }
            }


            KitPreview.getInstance().getKitFile().getConfig().set("Kits." + name + ".items", list);
            KitPreview.getInstance().getKitFile().saveConfig();
        } catch (Exception e) {
            Debugger.runReport(e);
        }
    }


    public List<ItemStack> getReadableContents(Player player, boolean commands) {
        List<ItemStack> stacks = new ArrayList();
        try {
            for (String str : getContents()) {
                if (!str.startsWith("/") || commands) {
                    ItemStack parseStack;
                    if (str.startsWith("/")) {
                        parseStack = new ItemStack(Material.PAPER, 1);
                        ItemMeta meta = parseStack.getItemMeta();

                        ArrayList<String> lore = new ArrayList<>();

                        int index = 0;
                        while (index < str.length()) {
                            lore.add("§a" + str.substring(index, Math.min(index + 30, str.length())));
                            index += 30;
                        }
                        meta.setLore(lore);
                        meta.setDisplayName(Lang.COMMAND.getConfigValue());
                        parseStack.setItemMeta(meta);
                    } else {
                        parseStack = Methods.deserializeItemStack(str);
                    }
                    ItemStack fin = parseStack;
                    if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && parseStack.getItemMeta().getLore() != null) {
                        ArrayList<String> lore2 = new ArrayList<String>();
                        ItemMeta meta2 = parseStack.getItemMeta();
                        for (String lor : parseStack.getItemMeta().getLore()) {
                            lor = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, lor.replace(" ", "_")).replace("_", " ");
                            lore2.add(lor);
                        }
                        meta2.setLore(lore2);
                        fin.setItemMeta(meta2);
                    }
                    stacks.add(fin);
                }
            }
        } catch (Exception e) {
            Debugger.runReport(e);
        }
        return stacks;
    }

    public void giveKit(Player player) {
        try {
            givePartKit(player, getContents().size());
        } catch (Exception e) {
            Debugger.runReport(e);
        }
    }

    public void givePartKit(Player player, int amt) {
        try {

            List<String> innerContents = new ArrayList<>(getContents());

            amt = innerContents.size() - amt;

            while (amt != 0) {
                int num = ThreadLocalRandom.current().nextInt(0, innerContents.size());
                innerContents.remove(num);
                amt--;
            }

            for (String line : getContents()) {
                if (line.startsWith("$")) {
                    try {
                        Methods.pay(player, Double.parseDouble(line.substring("$".length()).trim()));
                    } catch (NumberFormatException ex) {
                        Debugger.runReport(ex);
                    }
                    continue;
                } else if (line.startsWith("/")) {
                    String parsed = line.substring(1);
                    parsed = parsed.replace("{player}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed); // Not proud of this.
                    continue;
                }

                ItemStack parseStack = Methods.deserializeItemStack(line);
                if (parseStack.getType() == Material.AIR) continue;

                Map<Integer, ItemStack> overfilled = player.getInventory().addItem(parseStack);


                for (ItemStack item : overfilled.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }

            player.updateInventory();
        } catch (Exception e) {
            Debugger.runReport(e);
        }
    }
    
    public void updateDelay(Player player) {
        KitPreview.getInstance().getDataFile().getConfig().set("Kits."+name+".delays."+player.getUniqueId().toString(), System.currentTimeMillis());
    }

    public Long getNextUse(Player player) {
        String configSectionPlayer = "Kits."+name+".delays."+player.getUniqueId().toString();
        FileConfiguration config = KitPreview.getInstance().getDataFile().getConfig();

        if (!config.contains(configSectionPlayer)) {
            return -1L;
        }

        Long last = config.getLong(configSectionPlayer);

        Long delay = KitPreview.getInstance().getKitFile().getConfig().getLong("Kits." + name + ".delay") * 1000;
        
        return  last + delay >= System.currentTimeMillis() ? (last + delay) - System.currentTimeMillis() : -1L;
    }

    public int keyReward() {
        int result = 0;
        try {
            int reward = KitPreview.getInstance().getConfig().getInt("data.kit." + name + ".keyReward");
            int size = getContents().size();

            result = reward;
            if (reward >= size) {
                result = size;
            } else if (reward == 0) {
                result = size;
            }

        } catch (Exception ex) {
            Debugger.runReport(ex);
        }

        return result;
    }

    public void setKeyReward(int amt) {
        try {
            int size = getContents().size();

            if (amt >= size) {
                amt = size;
            } else if (amt <= 1) {
                amt = 1;
            }

            KitPreview.getInstance().getConfig().set("data.kit." + name + ".keyReward", amt);
            KitPreview.getInstance().saveConfig();
        } catch (Exception ex) {
            Debugger.runReport(ex);
        }

    }

    public void buyWithEconomy(Player p) {
        try {
            if (KitPreview.getInstance().getServer().getPluginManager().getPlugin("Vault") == null) return;
            RegisteredServiceProvider<Economy> rsp = KitPreview.getInstance().getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);

            net.milkbowl.vault.economy.Economy econ = rsp.getProvider();
            Double cost = KitPreview.getInstance().getConfig().getDouble("data.kit." + name + ".eco");
            if (!econ.has(p, cost) && !hasPermission(p)) {
                p.sendMessage(KitPreview.getInstance().references.getPrefix() + TextComponent.formatText(Lang.CANNOT_AFFORD.getConfigValue(showableName)));
                return;
            }
            if (KitPreview.getInstance().getConfig().getString("data.kit." + name + ".delay") != null) {
                long delay = getNextUse(p);
                if (delay != 0) delay -= System.currentTimeMillis();

                if (getNextUse(p) == -1) {
                    p.sendMessage(KitPreview.getInstance().references.getPrefix() + TextComponent.formatText(Lang.NOT_TWICE.getConfigValue(showableName)));
                } else if (delay != 0) {
                    p.sendMessage(KitPreview.getInstance().references.getPrefix() + TextComponent.formatText(Lang.DELAY.getConfigValue(Arconix.pl().format().readableTime(delay))));
                    return;
                }
            }
            if (KitPreview.getInstance().getConfig().getString("data.kit." + showableName + ".delay") != null) {
                updateDelay(p); //updates delay on buy
            }
            econ.withdrawPlayer(p, cost);
            give(p, false, true, false);

        } catch (Exception ex) {
            Debugger.runReport(ex);
        }

    }

    public boolean hasPermission(Player player) {
        try {
            if (player.hasPermission("uc.kit." + name.toLowerCase())) return true;
            if (player.hasPermission("essentials.kits." + name.toLowerCase())) return true;
            if (player.hasPermission("ultimatekits.kits." + name.toLowerCase())) return true;
        } catch (Exception e) {
            Debugger.runReport(e);
        }
        return false;
    }

    public void removeDisplayItems() {
        try {
            for (Entity e : location.getChunk().getEntities()) {
                if (e.getType() != EntityType.DROPPED_ITEM && e.getLocation().getX() != location.getX() && e.getLocation().getZ() != location.getZ()) {
                    continue;
                }
                Item i = (Item) e;
                i.remove();
            }
        } catch (Exception ex) {
            Debugger.runReport(ex);
        }

    }

    public List<String> getContents() {
        return KitPreview.getInstance().getKitFile().getConfig().getStringList("Kits." + name + ".items");
    }

    public String getName() {
        return name;
    }

    public String getShowableName() {
        return showableName;
    }

}
