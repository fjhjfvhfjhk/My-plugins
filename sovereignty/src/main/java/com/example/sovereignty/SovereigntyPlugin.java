package com.example.sovereignty;

import com.example.sovereignty.web.PrerenderManager;
import com.example.sovereignty.web.TerrainRenderer;
import com.example.sovereignty.web.WebPanelUploader;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SovereigntyPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private CountryManager countryManager;
    private EnergyManager energyManager;
    private EconomyManager economyManager;
    private ChunkUpgradeManager chunkUpgradeManager;
    private IncomeManager incomeManager;
    private PactManager pactManager;
    private AchievementManager achievementManager;
    private MiningBoostManager miningBoostManager;
    private DefensiveManager defensiveManager;
    private PactRequestManager pactRequestManager;
    private PeaceRequestManager peaceRequestManager;
    private EventManager eventManager;
    private ScienceManager scienceManager;
    private CourtManager courtManager;
    private ChunkBorderManager chunkBorderManager;
    private ChunkBorderGUI chunkBorderGUI;
    private TechnologyGUI technologyGUI;
    private CourtGUI courtGUI;
    private TerritoryListener territoryListener;
    private UpgradeGUI upgradeGUI;
    private ProtectionListener protectionListener;
    private DiplomacyGUI diplomacyGUI;
    private AllianceRequestManager requestManager;
    private ChunkUpgradeGUI chunkUpgradeGUI;
    private TopGUI topGUI;
    private ClaimGUI claimGUI;
    private InviteManager inviteManager;
    private ManageCountryGUI manageCountryGUI;
    private ConfirmDeleteGUI confirmDeleteGUI;
    private WebPanelUploader webPanelUploader;
    private TerrainRenderer terrainRenderer;
    private PrerenderManager prerenderManager;

    private final Set<UUID> autoClaimPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("webpanel.yml", false);

        this.databaseManager = new DatabaseManager(this);
        this.energyManager = new EnergyManager(this, databaseManager);
        this.economyManager = new EconomyManager(this);
        this.countryManager = new CountryManager(this, databaseManager, energyManager, economyManager);
        this.chunkUpgradeManager = new ChunkUpgradeManager(this, databaseManager, countryManager, economyManager);
        this.pactManager = new PactManager(this, databaseManager, countryManager);
        this.achievementManager = new AchievementManager(this, databaseManager, countryManager, energyManager, chunkUpgradeManager);
        this.miningBoostManager = new MiningBoostManager(this, databaseManager, countryManager);
        this.defensiveManager = new DefensiveManager(this, databaseManager, countryManager);
        this.pactRequestManager = new PactRequestManager();
        this.peaceRequestManager = new PeaceRequestManager();
        this.eventManager = new EventManager(this);
        this.scienceManager = new ScienceManager(this, databaseManager, countryManager, chunkUpgradeManager);
        this.courtManager = new CourtManager(this, databaseManager, countryManager);
        this.chunkBorderManager = new ChunkBorderManager(this, countryManager);
        this.chunkBorderGUI = new ChunkBorderGUI(this, chunkBorderManager);
        this.requestManager = new AllianceRequestManager();
        this.inviteManager = new InviteManager();
        this.manageCountryGUI = new ManageCountryGUI(this);
        this.confirmDeleteGUI = new ConfirmDeleteGUI(this);
        this.terrainRenderer = new TerrainRenderer(this);
        this.webPanelUploader = new WebPanelUploader(this);
        this.prerenderManager = new PrerenderManager(this);

        if (!economyManager.isEnabled()) {
            getLogger().warning("Vault/экономика не найдены.");
        }

        CountryCommand countryCommand = new CountryCommand(this, countryManager, energyManager, economyManager);
        getCommand("country").setExecutor(countryCommand);
        getCommand("country").setTabCompleter(countryCommand);

        CountryGUI countryGUI = new CountryGUI(this, countryManager, energyManager, economyManager);
        getServer().getPluginManager().registerEvents(countryGUI, this);

        this.upgradeGUI = new UpgradeGUI(this, countryManager, energyManager, economyManager);
        getServer().getPluginManager().registerEvents(upgradeGUI, this);

        this.diplomacyGUI = new DiplomacyGUI(this, countryManager, requestManager, pactRequestManager, peaceRequestManager);
        getServer().getPluginManager().registerEvents(diplomacyGUI, this);

        this.chunkUpgradeGUI = new ChunkUpgradeGUI(this, countryManager, chunkUpgradeManager, defensiveManager);
        getServer().getPluginManager().registerEvents(chunkUpgradeGUI, this);

        this.topGUI = new TopGUI(this, countryManager, energyManager);
        getServer().getPluginManager().registerEvents(topGUI, this);

        this.claimGUI = new ClaimGUI(this, countryManager);
        getServer().getPluginManager().registerEvents(claimGUI, this);

        this.technologyGUI = new TechnologyGUI(this, scienceManager);
        getServer().getPluginManager().registerEvents(technologyGUI, this);

        this.courtGUI = new CourtGUI(this, courtManager);
        getServer().getPluginManager().registerEvents(courtGUI, this);

        getServer().getPluginManager().registerEvents(chunkBorderGUI, this);
        getServer().getPluginManager().registerEvents(manageCountryGUI, this);
        getServer().getPluginManager().registerEvents(confirmDeleteGUI, this);
        getServer().getPluginManager().registerEvents(terrainRenderer, this);

        this.protectionListener = new ProtectionListener(this, countryManager);
        getServer().getPluginManager().registerEvents(protectionListener, this);

        this.territoryListener = new TerritoryListener(this, countryManager);
        getServer().getPluginManager().registerEvents(territoryListener, this);

        getServer().getPluginManager().registerEvents(new DeathListener(this, energyManager), this);
        getServer().getPluginManager().registerEvents(new AutoclaimListener(this, countryManager), this);

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                scienceManager.loadPlayer(e.getPlayer().getUniqueId());
            }
        }, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CountryPlaceholder(this, countryManager).register();
            getLogger().info("Плейсхолдер %sovereignty_country% зарегистрирован.");
        }

        this.incomeManager = new IncomeManager(this, countryManager, chunkUpgradeManager);
        incomeManager.start();

        scienceManager.startGeneration();
        eventManager.start();
        courtManager.startAutoClose();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            energyManager.regenAllOnline();
            energyManager.tickBoosts();
        }, 1200L, 1200L);

        webPanelUploader.start();

        getLogger().info("Sovereignty v2.9 включён (с Xaero-style картой и персистентным кэшем).");
    }

    @Override
    public void onDisable() {
        if (prerenderManager != null) prerenderManager.cancel();
        if (webPanelUploader != null) webPanelUploader.stop();
        if (terrainRenderer != null) terrainRenderer.shutdown();
        if (energyManager != null) energyManager.saveAll();
        if (scienceManager != null) scienceManager.saveAll();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("Sovereignty выключен.");
    }

    public boolean isAutoClaimEnabled(UUID uuid) { return autoClaimPlayers.contains(uuid); }
    public void setAutoClaim(UUID uuid, boolean enabled) {
        if (enabled) autoClaimPlayers.add(uuid); else autoClaimPlayers.remove(uuid);
    }
    public void toggleAutoClaim(UUID uuid) {
        if (autoClaimPlayers.contains(uuid)) autoClaimPlayers.remove(uuid); else autoClaimPlayers.add(uuid);
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public CountryManager getCountryManager() { return countryManager; }
    public EnergyManager getEnergyManager() { return energyManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public ChunkUpgradeManager getChunkUpgradeManager() { return chunkUpgradeManager; }
    public IncomeManager getIncomeManager() { return incomeManager; }
    public PactManager getPactManager() { return pactManager; }
    public AchievementManager getAchievementManager() { return achievementManager; }
    public MiningBoostManager getMiningBoostManager() { return miningBoostManager; }
    public DefensiveManager getDefensiveManager() { return defensiveManager; }
    public PactRequestManager getPactRequestManager() { return pactRequestManager; }
    public PeaceRequestManager getPeaceRequestManager() { return peaceRequestManager; }
    public EventManager getEventManager() { return eventManager; }
    public ScienceManager getScienceManager() { return scienceManager; }
    public CourtManager getCourtManager() { return courtManager; }
    public ChunkBorderManager getChunkBorderManager() { return chunkBorderManager; }
    public ChunkBorderGUI getChunkBorderGUI() { return chunkBorderGUI; }
    public TechnologyGUI getTechnologyGUI() { return technologyGUI; }
    public CourtGUI getCourtGUI() { return courtGUI; }
    public UpgradeGUI getUpgradeGUI() { return upgradeGUI; }
    public DiplomacyGUI getDiplomacyGUI() { return diplomacyGUI; }
    public ChunkUpgradeGUI getChunkUpgradeGUI() { return chunkUpgradeGUI; }
    public TopGUI getTopGUI() { return topGUI; }
    public ClaimGUI getClaimGUI() { return claimGUI; }
    public InviteManager getInviteManager() { return inviteManager; }
    public ManageCountryGUI getManageCountryGUI() { return manageCountryGUI; }
    public ConfirmDeleteGUI getConfirmDeleteGUI() { return confirmDeleteGUI; }
    public WebPanelUploader getWebPanelUploader() { return webPanelUploader; }
    public TerrainRenderer getTerrainRenderer() { return terrainRenderer; }
    public PrerenderManager getPrerenderManager() { return prerenderManager; }
}
