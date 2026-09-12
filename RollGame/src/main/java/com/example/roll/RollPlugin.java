package com.example.roll;

import org.bukkit.plugin.java.JavaPlugin;

public final class RollPlugin extends JavaPlugin {

    private static RollPlugin instance;

    private EconomyManager economyManager;
    private GameManager gameManager;
    private DuelManager duelManager;
    private MinesManager minesManager;
    private SlotsManager slotsManager;
    private WheelManager wheelManager;
    private StairsManager stairsManager;
    private PokerManager pokerManager;
    private PokerHistory pokerHistory;
    private RollData rollData;
    private PanelExporter panelExporter;
    private CrashManager crashManager;
    private UpgradeManager upgradeManager;

    private RollGUI rollGUI;
    private RollDuelGUI duelGUI;
    private MinesGUI minesGUI;
    private RollGamesGUI gamesGUI;
    private SlotsGUI slotsGUI;
    private WheelGUI wheelGUI;
    private StairsGUI stairsGUI;
    private PokerGUI pokerGUI;
    private PokerCardsGUI pokerCardsGUI;
    private PokerTablesGUI pokerTablesGUI;
    private BetInputGUI betInputGUI;
    private CrashGUI crashGUI;
    private UpgradeHubGUI upgradeHubGUI;
    private UpgradeChanceGUI upgradeChanceGUI;
    private UpgradeItemGUI upgradeItemGUI;
    private UpgradeRewardGUI upgradeRewardGUI;
    private UpgradeHistoryGUI upgradeHistoryGUI;
    private UpgradeTopGUI upgradeTopGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        rollData = new RollData(this);
        economyManager = new EconomyManager(this);
        gameManager = new GameManager(this);
        duelManager = new DuelManager(this);
        minesManager = new MinesManager(this);
        slotsManager = new SlotsManager(this);
        wheelManager = new WheelManager(this);
        stairsManager = new StairsManager(this);
        pokerHistory = new PokerHistory(this);
        pokerManager = new PokerManager(this);
        crashManager = new CrashManager(this);
        upgradeManager = new UpgradeManager(this);

        betInputGUI = new BetInputGUI(this);
        rollGUI = new RollGUI(this);
        duelGUI = new RollDuelGUI(this, duelManager);
        minesGUI = new MinesGUI(this, minesManager);
        gamesGUI = new RollGamesGUI(this);
        slotsGUI = new SlotsGUI(this, slotsManager);
        wheelGUI = new WheelGUI(this, wheelManager);
        stairsGUI = new StairsGUI(this, stairsManager);
        pokerGUI = new PokerGUI(this);
        pokerCardsGUI = new PokerCardsGUI(this);
        pokerTablesGUI = new PokerTablesGUI(this);

        crashGUI = new CrashGUI(this, crashManager);
        upgradeRewardGUI = new UpgradeRewardGUI(this);
        upgradeHistoryGUI = new UpgradeHistoryGUI(this);
        upgradeTopGUI = new UpgradeTopGUI(this);
        upgradeHubGUI = new UpgradeHubGUI(this);
        upgradeChanceGUI = new UpgradeChanceGUI(this, upgradeManager);
        upgradeItemGUI = new UpgradeItemGUI(this, upgradeManager, upgradeRewardGUI);

        panelExporter = new PanelExporter(this);

        RollCommand command = new RollCommand(this, rollGUI, duelGUI, minesGUI,
                gamesGUI, slotsGUI, wheelGUI, stairsGUI);
        getCommand("roll").setExecutor(command);
        getCommand("roll").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(rollGUI, this);
        getServer().getPluginManager().registerEvents(duelGUI, this);
        getServer().getPluginManager().registerEvents(minesGUI, this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (gameManager.isActive() && (gameManager.isSpinning() || gameManager.isFrozen()))
                gameManager.updateAnimation();
        }, 0L, Math.max(2, getConfig().getLong("animation-interval-ticks", 5)));

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (gameManager.isActive() && !gameManager.isFinished()) RollGUI.updateAllOpen();
        }, 20L, 20L);

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (duelManager.isActive() && (duelManager.isSpinning() || duelManager.isFrozen()))
                duelManager.updateAnimation();
        }, 0L, Math.max(2, getConfig().getLong("animation-interval-ticks", 5)));

        getLogger().info("RollGame v2.4 включён.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.saveGame();
        if (pokerManager != null) pokerManager.shutdown();
        if (crashManager != null) crashManager.shutdown();
        if (panelExporter != null) panelExporter.shutdown();
        if (rollData != null) rollData.save();
        getLogger().info("RollGame выключен.");
    }

    public static RollPlugin getInstance() { return instance; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public GameManager getGameManager() { return gameManager; }
    public DuelManager getDuelManager() { return duelManager; }
    public MinesManager getMinesManager() { return minesManager; }
    public SlotsManager getSlotsManager() { return slotsManager; }
    public WheelManager getWheelManager() { return wheelManager; }
    public StairsManager getStairsManager() { return stairsManager; }
    public PokerManager getPokerManager() { return pokerManager; }
    public PokerHistory getPokerHistory() { return pokerHistory; }
    public RollData getRollData() { return rollData; }
    public PanelExporter getPanelExporter() { return panelExporter; }
    public RollGUI getRollGUI() { return rollGUI; }
    public RollDuelGUI getDuelGUI() { return duelGUI; }
    public MinesGUI getMinesGUI() { return minesGUI; }
    public RollGamesGUI getGamesGUI() { return gamesGUI; }
    public SlotsGUI getSlotsGUI() { return slotsGUI; }
    public WheelGUI getWheelGUI() { return wheelGUI; }
    public StairsGUI getStairsGUI() { return stairsGUI; }
    public PokerGUI getPokerGUI() { return pokerGUI; }
    public PokerCardsGUI getPokerCardsGUI() { return pokerCardsGUI; }
    public PokerTablesGUI getPokerTablesGUI() { return pokerTablesGUI; }
    public BetInputGUI getBetInputGUI() { return betInputGUI; }
    public CrashManager getCrashManager() { return crashManager; }
    public UpgradeManager getUpgradeManager() { return upgradeManager; }
    public CrashGUI getCrashGUI() { return crashGUI; }
    public UpgradeHubGUI getUpgradeHubGUI() { return upgradeHubGUI; }
    public UpgradeChanceGUI getUpgradeChanceGUI() { return upgradeChanceGUI; }
    public UpgradeItemGUI getUpgradeItemGUI() { return upgradeItemGUI; }
    public UpgradeRewardGUI getUpgradeRewardGUI() { return upgradeRewardGUI; }
    public UpgradeHistoryGUI getUpgradeHistoryGUI() { return upgradeHistoryGUI; }
    public UpgradeTopGUI getUpgradeTopGUI() { return upgradeTopGUI; }
}
