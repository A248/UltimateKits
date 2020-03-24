package com.songoda.ultimatekits;

import com.songoda.core.SongodaCore;
import com.songoda.core.SongodaPlugin;
import com.songoda.core.commands.CommandManager;
import com.songoda.core.compatibility.CompatibleMaterial;
import com.songoda.core.configuration.Config;
import com.songoda.core.database.DataMigrationManager;
import com.songoda.core.database.DatabaseConnector;
import com.songoda.core.database.MySQLConnector;
import com.songoda.core.database.SQLiteConnector;
import com.songoda.core.gui.GuiManager;
import com.songoda.core.hooks.EconomyManager;
import com.songoda.core.hooks.HologramManager;
import com.songoda.core.utils.TextUtils;
import com.songoda.ultimatekits.category.Category;
import com.songoda.ultimatekits.category.CategoryManager;
import com.songoda.ultimatekits.commands.*;
import com.songoda.ultimatekits.conversion.Convert;
import com.songoda.ultimatekits.crate.Crate;
import com.songoda.ultimatekits.crate.CrateManager;
import com.songoda.ultimatekits.database.DataManager;
import com.songoda.ultimatekits.database.migrations._1_InitialMigration;
import com.songoda.ultimatekits.database.migrations._2_DuplicateMigration;
import com.songoda.ultimatekits.handlers.DisplayItemHandler;
import com.songoda.ultimatekits.handlers.ParticleHandler;
import com.songoda.ultimatekits.key.Key;
import com.songoda.ultimatekits.key.KeyManager;
import com.songoda.ultimatekits.kit.*;
import com.songoda.ultimatekits.listeners.*;
import com.songoda.ultimatekits.settings.Settings;
import com.songoda.ultimatekits.utils.ItemSerializer;
import com.songoda.ultimatekits.utils.Methods;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.PluginManager;

import java.util.*;
import java.util.stream.Collectors;

public class UltimateKits extends SongodaPlugin {

    private static UltimateKits INSTANCE;

    private final Config kitConfig = new Config(this, "kit.yml");
    private final Config categoryConfig = new Config(this, "category.yml");
    private final Config dataFile = new Config(this, "data.yml");
    private final Config keyFile = new Config(this, "keys.yml");
    private final Config crateFile = new Config(this, "crates.yml");

    private final GuiManager guiManager = new GuiManager(this);
    private final ParticleHandler particleHandler = new ParticleHandler(this);
    private final DisplayItemHandler displayItemHandler = new DisplayItemHandler(this);

    private KitManager kitManager;
    private CommandManager commandManager;
    private KeyManager keyManager;
    private CrateManager crateManager;
    private CategoryManager categoryManager;

    private ItemSerializer itemSerializer;

    private DatabaseConnector databaseConnector;
    private DataMigrationManager dataMigrationManager;
    private DataManager dataManager;

    private boolean loaded = false;

    /**
     * Grab instance of UltimateKits
     *
     * @return instance of UltimateKits
     */
    public static UltimateKits getInstance() {
        return INSTANCE;
    }

    @Override
    public void onPluginLoad() {
        INSTANCE = this;
        try {
            this.itemSerializer = new ItemSerializer();
        } catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            console.sendMessage(ChatColor.RED + "Could not load the serialization class! Please report this error.");
            e.printStackTrace();
        }
    }

    @Override
    public void onPluginEnable() {
        SongodaCore.registerPlugin(this, 14, CompatibleMaterial.BEACON);

        // Load Economy
        EconomyManager.load();
        // Register Hologram Plugin
        HologramManager.load(this);

        // Setup Config
        Settings.setupConfig();
        this.setLocale(Settings.LANGUGE_MODE.getString(), false);

        // Set economy preference
        EconomyManager.getManager().setPreferredHook(Settings.ECONOMY_PLUGIN.getString());

        this.kitManager = new KitManager();
        this.keyManager = new KeyManager();
        this.crateManager = new CrateManager();
        this.categoryManager = new CategoryManager(this);

        kitConfig.load();
        Convert.runKitConversions();

        categoryConfig.load();

        // load kits
        dataFile.load();
        keyFile.load();
        crateFile.load();
        checkKeyDefaults();
        checkCrateDefaults();
        loadKits();
        keyFile.saveChanges();
        crateFile.saveChanges();

        // setup commands
        this.commandManager = new CommandManager(this);
        this.commandManager.addCommand(new CommandKit(guiManager));
        this.commandManager.addCommand(new CommandPreviewKit(guiManager));
        this.commandManager.addCommand(new CommandUltimateKits())
                .addSubCommand(new CommandReload())
                .addSubCommand(new CommandSettings(guiManager))
                .addSubCommand(new CommandCreatekit(guiManager))
                .addSubCommand(new CommandCategories(guiManager))
                .addSubCommand(new CommandEdit(guiManager))
                .addSubCommand(new CommandKey())
                .addSubCommand(new CommandSet())
                .addSubCommand(new CommandRemove())

                .addSubCommand(new CommandCrate());


        // Event registration
        PluginManager pluginManager = getServer().getPluginManager();
        guiManager.init();
        pluginManager.registerEvents(new BlockListeners(this), this);
        pluginManager.registerEvents(new ChatListeners(this), this);
        pluginManager.registerEvents(new EntityListeners(this), this);
        pluginManager.registerEvents(new InteractListeners(this, guiManager), this);
        pluginManager.registerEvents(new PlayerListeners(), this);

        try {
            if (Settings.MYSQL_ENABLED.getBoolean()) {
                String hostname = Settings.MYSQL_HOSTNAME.getString();
                int port = Settings.MYSQL_PORT.getInt();
                String database = Settings.MYSQL_DATABASE.getString();
                String username = Settings.MYSQL_USERNAME.getString();
                String password = Settings.MYSQL_PASSWORD.getString();
                boolean useSSL = Settings.MYSQL_USE_SSL.getBoolean();

                this.databaseConnector = new MySQLConnector(this, hostname, port, database, username, password, useSSL);
                this.getLogger().info("Data handler connected using MySQL.");
            } else {
                this.databaseConnector = new SQLiteConnector(this);
                this.getLogger().info("Data handler connected using SQLite.");
            }

            this.dataManager = new DataManager(this.databaseConnector, this);
            this.dataMigrationManager = new DataMigrationManager(this.databaseConnector, this.dataManager,
                    new _1_InitialMigration(),
                    new _2_DuplicateMigration(this.databaseConnector instanceof SQLiteConnector));
            this.dataMigrationManager.runMigrations();

        } catch (Exception ex) {
            this.getLogger().severe("Fatal error trying to connect to database. " +
                    "Please make sure all your connection settings are correct and try again. Plugin has been disabled.");
            emergencyStop();
            return;
        }

        displayItemHandler.start();
        particleHandler.start();
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> saveKits(false), 6000, 6000);
    }

    @Override
    public void onPluginDisable() {
        saveKits(false);
        dataFile.save();
        this.dataManager.bulkUpdateBlockData(this.getKitManager().getKitLocations());
        kitManager.clearKits();
        HologramManager.removeAllHolograms();
    }

    @Override
    public List<Config> getExtraConfig() {
        return Arrays.asList(kitConfig, keyFile, categoryConfig, crateFile);
    }

    @Override
    public void onConfigReload() {
        this.setLocale(Settings.LANGUGE_MODE.getString(), true);

        this.dataManager.bulkUpdateBlockData(this.getKitManager().getKitLocations());
        kitConfig.load();
        categoryConfig.load();
        keyFile.load();
        crateFile.load();
        loadKits();
    }

    void loadKits() {
        Bukkit.getScheduler().runTaskLater(this, () -> {

            //Empty categories from manager.
            categoryManager.clearCategories();

            /*
             * Register categories into CategoryManager from Configuration.
             */
            if (categoryConfig.getConfigurationSection("Categories") != null)
                for (String key : categoryConfig.getConfigurationSection("Categories").getKeys(false)) {
                    ConfigurationSection section = categoryConfig.getConfigurationSection("Categories." + key);
                    if (section == null) continue;

                    Category category = categoryManager.addCategory(key, section.getString("name"));
                    if (section.contains("material"))
                        category.setMaterial(CompatibleMaterial.getMaterial(section.getString("material")).getMaterial());
                }

            //Empty kits from manager.
            kitManager.clearKits();

            /*
             * Register kits into KitManager from Configuration.
             */
            if (kitConfig.getConfigurationSection("Kits") != null)
                for (String kitName : kitConfig.getConfigurationSection("Kits").getKeys(false)) {
                    ConfigurationSection section = kitConfig.getConfigurationSection("Kits." + kitName);
                    if (section == null) continue;

                    kitManager.addKit(new Kit(kitName)
                            .setTitle(section.getString("title"))
                            .setDelay(section.getLong("delay"))
                            .setLink(section.getString("link"))
                            .setDisplayItem(section.contains("displayItem")
                                    ? CompatibleMaterial.getMaterial(section.getString("displayItem"), CompatibleMaterial.DIAMOND_HELMET)
                                    : null)
                            .setCategory(categoryManager.getCategory(section.getString("category")))
                            .setHidden(section.getBoolean("hidden"))
                            .setPrice(section.getDouble("price"))
                            .setContents(section.getStringList("items").stream().map(KitItem::new).collect(Collectors.toList()))
                            .setKitAnimation(KitAnimation.valueOf(section.getString("animation", KitAnimation.NONE.name())))
                    );
                }

            /*
             * Register legacy kit locations into KitManager from Configuration.
             */
            if (dataFile.contains("BlockData")) {
                for (String key : dataFile.getConfigurationSection("BlockData").getKeys(false)) {
                    Location location = Methods.unserializeLocation(key);
                    Kit kit = kitManager.getKit(dataFile.getString("BlockData." + key + ".kit"));
                    KitType type = KitType.valueOf(dataFile.getString("BlockData." + key + ".type", "PREVIEW"));
                    boolean holograms = dataFile.getBoolean("BlockData." + key + ".holograms");
                    boolean displayItems = dataFile.getBoolean("BlockData." + key + ".displayItems");
                    boolean particles = dataFile.getBoolean("BlockData." + key + ".particles");
                    boolean itemOverride = dataFile.getBoolean("BlockData." + key + ".itemOverride");

                    if (kit == null) dataFile.set("BlockData." + key, null);
                    else {
                        updateHologram(kitManager.addKitToLocation(kit, location, type, holograms, particles, displayItems, itemOverride));
                    }
                }
            }

            /*
             * Register kit locations into KitManager from Configuration.
             */
            Bukkit.getScheduler().runTaskLater(this, () ->
                    this.dataManager.getBlockData((blockData) -> {
                        this.kitManager.setKitLocations(blockData);
                        if (HologramManager.isEnabled()) {
                            loadHolograms();
                        }
                    }), 20L);

            //Apply default keys.
            checkKeyDefaults();
            checkCrateDefaults();

            //Empty keys from manager.
            keyManager.clear();
            crateManager.clear();

            /*
             * Register keys into KitManager from Configuration.
             */
            if (keyFile.contains("Keys")) {
                for (String keyName : keyFile.getConfigurationSection("Keys").getKeys(false)) {
                    int amt = keyFile.getInt("Keys." + keyName + ".Item Amount");
                    int kitAmount = keyFile.getInt("Keys." + keyName + ".Amount of kit received");

                    Key key = new Key(keyName, amt, kitAmount);
                    keyManager.addKey(key);
                }
            }

            /*
             * Register Crates
             * */
            if (crateFile.contains("Crates")) {
                for (String crateName : crateFile.getConfigurationSection("Crates").getKeys(false)) {
                    int amt = crateFile.getInt("Crates." + crateName + ".Item Amount");
                    int kitAmount = crateFile.getInt("Crates." + crateName + ".Amount of kit received");

                    Crate crate = new Crate(crateName, amt, kitAmount);
                    crateManager.addCrate(crate);
                }
            }
            this.loaded = true;

        }, 10);
    }

    public void removeHologram(KitBlockData data) {
        if (HologramManager.isEnabled()) {
            List<String> lines = formatHologram(data);
            Location location = getKitLocation(data, lines.size());
            HologramManager.removeHologram(location);
        }
    }

    public void updateHologram(Kit kit) {
        for (KitBlockData data : getKitManager().getKitLocations().values()) {
            if (data.getKit() != kit) continue;
            updateHologram(data);
        }
    }

    public void updateHologram(KitBlockData data) {
        if (data != null && data.isInLoadedChunk() && HologramManager.isEnabled()) {
            List<String> lines = formatHologram(data);

            if (!lines.isEmpty()) {
                Location location = getKitLocation(data, lines.size());
                if (!data.showHologram()) {
                    HologramManager.removeHologram(location);
                } else {
                    HologramManager.updateHologram(location, lines);
                }
            }
        }
    }

    private void loadHolograms() {
        Collection<KitBlockData> kitBlocks = getKitManager().getKitLocations().values();
        if (kitBlocks.isEmpty()) return;

        for (KitBlockData data : kitBlocks) {
            updateHologram(data);
        }
    }

    private Location getKitLocation(KitBlockData data, int lines) {
        Location location = data.getLocation();
        double multi = .1 * lines;
        if (data.isDisplayingItems()) {
            multi += .25;
        }
        Material type = location.getBlock().getType();
        if (type == Material.TRAPPED_CHEST
                || type == Material.CHEST
                || type.name().contains("SIGN")
                || type == Material.ENDER_CHEST) {
            multi -= .10;
        }
        location.add(0, multi, 0);
        return location;
    }

    private List<String> formatHologram(KitBlockData data) {
        getDataManager().updateBlockData(data);
        KitType kitType = data.getType();

        ArrayList<String> lines = new ArrayList<>();

        List<String> order = Settings.HOLOGRAM_LAYOUT.getStringList();

        Kit kit = data.getKit();

        for (String o : order) {
            switch (o.toUpperCase()) {
                case "{TITLE}":
                    String title = kit.getTitle();
                    if (title == null) {
                        lines.add(ChatColor.DARK_PURPLE + TextUtils.formatText(kit.getKey(), true));
                    } else {
                        lines.add(ChatColor.DARK_PURPLE + TextUtils.formatText(title));
                    }
                    break;
                case "{RIGHT-CLICK}":
                    if (kitType == KitType.CRATE) {
                        lines.add(getLocale().getMessage("interface.hologram.crate").getMessage());
                        break;
                    }
                    if (kit.getLink() != null) {
                        lines.add(getLocale().getMessage("interface.hologram.buylink").getMessage());
                        break;
                    }
                    if (kit.getPrice() != 0) {
                        lines.add(getLocale().getMessage("interface.hologram.buyeco")
                                .processPlaceholder("price", kit.getPrice() != 0
                                        ? Methods.formatEconomy(kit.getPrice())
                                        : getLocale().getMessage("general.type.free").getMessage())
                                .getMessage());
                    }
                    break;
                case "{LEFT-CLICK}":
                    if (kitType == KitType.CLAIM) {
                        lines.add(getLocale().getMessage("interface.hologram.daily").getMessage());
                        break;
                    }
                    if (kit.getLink() == null && kit.getPrice() == 0) {
                        lines.add(getLocale().getMessage("interface.hologram.previewonly").getMessage());
                    } else {
                        lines.add(getLocale().getMessage("interface.hologram.preview").getMessage());
                    }
                    break;
                default:
                    lines.add(ChatColor.translateAlternateColorCodes('&', o));
                    break;
            }
        }

        return lines;
    }

    /*
     * Saves registered kits to file.
     */
    public void saveKits(boolean force) {
        if (!loaded && !force) return;

        // Hot fix for kit file resets.
        if (kitConfig.contains("Kits"))
            for (String kitName : kitConfig.getConfigurationSection("Kits").getKeys(false)) {
                if (kitManager.getKits().stream().noneMatch(kit -> kit.getKey().equals(kitName)))
                    kitConfig.set("Kits." + kitName, null);
            }

        // Hot fix for category file resets.
        if (categoryConfig.contains("Categories"))
            for (String key : categoryConfig.getConfigurationSection("Categories").getKeys(false)) {
                if (categoryManager.getCategories().stream().noneMatch(category -> category.getKey().equals(key)))
                    categoryConfig.set("Categories." + key, null);
            }

        /*
         * Save kits from KitManager to Configuration.
         */
        for (Kit kit : kitManager.getKits()) {
            kitConfig.set("Kits." + kit.getKey() + ".delay", kit.getDelay());
            kitConfig.set("Kits." + kit.getKey() + ".title", kit.getTitle());
            kitConfig.set("Kits." + kit.getKey() + ".link", kit.getLink());
            kitConfig.set("Kits." + kit.getKey() + ".price", kit.getPrice());
            kitConfig.set("Kits." + kit.getKey() + ".hidden", kit.isHidden());
            kitConfig.set("Kits." + kit.getKey() + ".animation", kit.getKitAnimation().name());
            if (kit.getCategory() != null)
                kitConfig.set("Kits." + kit.getKey() + ".category", kit.getCategory().getKey());
            if (kit.getDisplayItem() != null)
                kitConfig.set("Kits." + kit.getKey() + ".displayItem", kit.getDisplayItem().toString());
            else
                kitConfig.set("Kits." + kit.getKey() + ".displayItem", null);

            List<KitItem> contents = kit.getContents();
            List<String> strContents = new ArrayList<>();

            for (KitItem item : contents) strContents.add(item.getSerialized());

            kitConfig.set("Kits." + kit.getKey() + ".items", strContents);
        }

        /*
         * Save categories from CategoryManager to Configuration.
         */
        for (Category category : categoryManager.getCategories()) {
            categoryConfig.set("Categories." + category.getKey() + ".name", category.getName());
            categoryConfig.set("Categories." + category.getKey() + ".material", category.getMaterial().name());
        }

        // Save to file
        kitConfig.saveChanges();
        categoryConfig.saveChanges();
    }

    /*
     * Insert default key list into config.
     */
    private void checkKeyDefaults() {
        if (keyFile.contains("Keys")) return;
        keyFile.set("Keys.Regular.Item Amount", 3);
        keyFile.set("Keys.Regular.Amount overrides", Collections.singletonList("Tools:2"));
        keyFile.set("Keys.Regular.Amount of kit received", 1);
        keyFile.set("Keys.Ultra.Item Amount", -1);
        keyFile.set("Keys.Ultra.Amount of kit received", 1);
        keyFile.set("Keys.Insane.Item Amount", -1);
        keyFile.set("Keys.Insane.Amount of kit received", 2);
    }

    private void checkCrateDefaults() {
        if (crateFile.contains("Crates")) return;
        crateFile.set("Crates.Regular.Item Amount", 3);
        crateFile.set("Crates.Regular.Amount overrides", Collections.singletonList("Tools:2"));
        crateFile.set("Crates.Regular.Amount of kit received", 1);
        crateFile.set("Crates.Ultra.Item Amount", -1);
        crateFile.set("Crates.Ultra.Amount of kit received", 1);
        crateFile.set("Crates.Insane.Item Amount", -1);
        crateFile.set("Crates.Insane.Amount of kit received", 2);
    }

    /**
     * Get instance of KitManager
     *
     * @return instance of KitManager
     */
    public KitManager getKitManager() {
        return kitManager;
    }

    /**
     * Get instance of KeyManager
     *
     * @return instance of KeyManager
     */
    public KeyManager getKeyManager() {
        return keyManager;
    }

    public CrateManager getCrateManager() {
        return crateManager;
    }

    /**
     * Grab instance of Kit File Configuration Wrapper
     *
     * @return instance of KitFile
     */
    public Config getKitConfig() {
        return kitConfig;
    }

    /**
     * Grab instance of Data File Configuration Wrapper
     *
     * @return instance of DataFile
     */
    public Config getDataFile() {
        return dataFile;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    /**
     * Grab instance of the item serializer
     *
     * @return instance of ItemSerializer
     */
    public ItemSerializer getItemSerializer() {
        return this.itemSerializer;
    }

    public DisplayItemHandler getDisplayItemHandler() {
        return displayItemHandler;
    }

    public DatabaseConnector getDatabaseConnector() {
        return databaseConnector;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public CategoryManager getCategoryManager() {
        return categoryManager;
    }
}
