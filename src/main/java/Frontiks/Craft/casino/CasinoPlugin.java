package Frontiks.Craft.casino;

import org.bukkit.plugin.java.JavaPlugin;
import Frontiks.Craft.casino.balance.BalanceProvider;
import Frontiks.Craft.casino.balance.FcpHook;
import Frontiks.Craft.casino.balance.FileBalanceProvider;
import Frontiks.Craft.casino.commands.RouletteCommand;
import Frontiks.Craft.casino.game.RouletteGame;
import Frontiks.Craft.casino.gui.CasinoGui;
import Frontiks.Craft.casino.gui.CasinoGuiListener;
import Frontiks.Craft.casino.util.ChipManager;
import Frontiks.Craft.casino.util.Messages;

/**
 * fcp-CasinoRoulette — европейская рулетка для Paper 1.21.x в GUI.
 *
 * <p>Игра идёт в меню (54 слота): числа 0–36, внешние ставки, выбор фишки.
 * Раунд общий для всего сервера — ставки всех игроков сходятся в один спин.</p>
 *
 * <p>Валюта: монеты FCP (Coins) через {@code EconomyService}; при отсутствии
 * FCP-EconomyMarket — тестовый кошелёк {@link FileBalanceProvider}.</p>
 */
public class CasinoPlugin extends JavaPlugin {

    private static CasinoPlugin instance;

    private BalanceProvider balanceProvider;
    private ChipManager chipManager;
    private RouletteGame game;
    private CasinoGui gui;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Messages.init(this);
        this.chipManager = new ChipManager(this);

        // === ВАЛЮТА: монеты FCP (Coins), fallback — тестовый balances.yml ===
        BalanceProvider fcpProvider = null;
        try {
            fcpProvider = FcpHook.create(this);
        } catch (NoClassDefFoundError | IllegalStateException e) {
            fcpProvider = null;
        }
        if (fcpProvider != null) {
            this.balanceProvider = fcpProvider;
        } else {
            this.balanceProvider = new FileBalanceProvider(this);
            getLogger().warning("FCP Economy не найден — казино работает на тестовой валюте (balances.yml)");
        }

        // === ИГРА и GUI ===
        this.gui = new CasinoGui(this);
        this.game = new RouletteGame(this);

        getServer().getPluginManager().registerEvents(new CasinoGuiListener(this), this);
        RouletteCommand executor = new RouletteCommand(this);
        var command = getCommand("roulette");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        game.start();
        getLogger().info("fcp-CasinoRoulette включён. Раунд общий, ставки принимаются в /roulette");
    }

    @Override
    public void onDisable() {
        if (gui != null) {
            gui.closeAll();
        }
        if (game != null) {
            game.stop();
        }
        if (balanceProvider != null) {
            balanceProvider.save();
        }
        instance = null;
    }

    public static CasinoPlugin get() {
        return instance;
    }

    public BalanceProvider getBalanceProvider() {
        return balanceProvider;
    }

    public ChipManager getChipManager() {
        return chipManager;
    }

    public RouletteGame getGame() {
        return game;
    }

    public CasinoGui getGui() {
        return gui;
    }
}
