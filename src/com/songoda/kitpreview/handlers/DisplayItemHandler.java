package com.songoda.kitpreview.handlers;

import com.songoda.arconix.Arconix;
import com.songoda.kitpreview.KitPreview;
import com.songoda.kitpreview.kits.Kit;
import com.songoda.kitpreview.utils.Debugger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by songoda on 2/24/2017.
 */
public class DisplayItemHandler {

    private final KitPreview instance;

    public DisplayItemHandler(KitPreview instance) {
        this.instance = instance;
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(KitPreview.getInstance(), () -> displayItems(), 30L, 30L);
    }

    public Map<String, Kit> cache = new HashMap<>();

    private void displayItems() {
        try {
            if (instance.getConfig().getString("data.block") == null) return;
            ConfigurationSection section = instance.getConfig().getConfigurationSection("data.block");
            loop:
            for (String loc : section.getKeys(false)) {
                if (instance.getConfig().getString("data.displayitems." + loc) == null) continue;
                String kit = instance.getConfig().getString("data.block." + loc);

                Location location = Arconix.pl().serialize().unserializeLocation(loc);
                location.add(0.5, 0, 0.5);

                cache.putIfAbsent(kit, new Kit(kit));

                List<ItemStack> list = cache.get(kit).getReadableContents(null, false);
                for (Entity e : location.getChunk().getEntities()) {
                    if (e.getType() != EntityType.DROPPED_ITEM
                            || e.getLocation().getX() != location.getX()
                            || e.getLocation().getZ() != location.getZ()) {
                        continue;
                    }
                    Item i = (Item) e;
                    if (i.getItemStack().getItemMeta().getDisplayName() == null) {
                        i.remove();
                        continue loop;
                    }
                    int inum = Integer.parseInt(i.getItemStack().getItemMeta().getDisplayName()) + 1;
                    if (inum > list.size()) inum = 1;

                    ItemStack is = list.get(inum - 1);
                    if (instance.getConfig().getItemStack("data.kit." + kit + ".displayitem") != null) {
                        is = instance.getConfig().getItemStack("data.kit." + kit + ".displayitem").clone();
                    }
                    ItemMeta meta = is.getItemMeta();
                    is.setAmount(1);
                    meta.setDisplayName(Integer.toString(inum));
                    is.setItemMeta(meta);
                    i.setItemStack(is);
                    i.setPickupDelay(9999);
                    continue loop;
                }
                ItemStack is = list.get(0);
                is.setAmount(1);
                ItemMeta meta = is.getItemMeta();
                meta.setDisplayName("0");
                is.setItemMeta(meta);
                Item i = location.getWorld().dropItem(location.add(0, 1, 0), list.get(0));
                Vector vec = new Vector(0, 0, 0);
                i.setVelocity(vec);
                i.setPickupDelay(9999);
                i.setMetadata("displayItem", new FixedMetadataValue(KitPreview.getInstance(), true));
                i.setMetadata("betterdrops_ignore", new FixedMetadataValue(KitPreview.getInstance(), true));
            }
        } catch (Exception ex) {
            Debugger.runReport(ex);
        }
    }
}
