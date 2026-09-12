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

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.rollData = new RollData(this);
        this.economyManager = new EconomyManager(this);
        this.gameManager = new GameManager(this);
        this.duelManager = new DuelManager(this);
        this.minesManager = new MinesManager(this);
        this.slotsManager = new SlotsManager(this);
        this.wheelManager = new WheelManager(this);
        this.stairsManager = new StairsManager(this);
        this.pokerHistory = new PokerHistory(this);
        this.pokerManager = new PokerManager(this);

        this.betInputGUI = new BetInputGUI(this);
        this.rollGUI = new RollGUI(this);
        this.duelGUI = new RollDuelGUI(this, duelManager);
        this.minesGUI = new MinesGUI(this, minesManager);
        this.gamesGUI = new RollGamesGUI(this);
        this.slotsGUI = new SlotsGUI(this, slotsManager);
        this.wheelGUI = new WheelGUI(this, wheelManager);
        this.stairsGUI = new StairsGUI(this, stairsManager);
        this.pokerGUI = new PokerGUI(this);
        this.pokerCardsGUI = new PokerCardsGUI(this);
        this.pokerTablesGUI = new PokerTablesGUI(this);

        // Экспорт на веб-панель Sovereignty
        this.panelExporter = new PanelExporter(this);

        RollCommand command = new RollCommand(this, rollGUI, duelGUI, minesGUI,
                gamesGUI, slotsGUI, wheelGUI, stairsGUI);
        getCommand("roll").setExecutor(command);
        getCommand("roll").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(rollGUI, this);
        getServer().getPluginManager().registerEvents(duelGUI, this);
        getServer().getPluginManager().registerEvents(minesGUI, this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (gameManager.isActive() && (gameManager.isSpinning() || gameManager.isFrozen())) {
                gameManager.updateAnimation();
            }
        }, 0L, Math.max(2, getConfig().getLong("animation-interval-ticks", 5)));

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (gameManager.isActive() && !gameManager.isFinished()) RollGUI.updateAllOpen();
        }, 20L, 20L);

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (duelManager.isActive() && (duelManager.isSpinning() || duelManager.isFrozen())) {
                duelManager.updateAnimation();
            }
        }, 0L, Math.max(2, getConfig().getLong("animation-interval-ticks", 5)));

        getLogger().info("RollGame включён: рулетка, дуэль, мины, слоты, колесо, лестница, покер.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.saveGame();
        if (pokerManager != null) pokerManager.shutdown();
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
}
