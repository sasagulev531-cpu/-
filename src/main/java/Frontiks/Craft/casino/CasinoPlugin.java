package Frontiks.Craft.casino;

import org.bukkit.plugin.java.JavaPlugin;
import Frontiks.Craft.casino.balance.BalanceProvider;
import Frontiks.Craft.casino.balance.FcpHook;
import Frontiks.Craft.casino.balance.FileBalanceProvider;
import Frontiks.Craft.casino.commands.RouletteCommand;
import Frontiks.Craft.casino.listeners.TableListener;
import Frontiks.Craft.casino.table.TableManager;
import Frontiks.Craft.casino.util.ChipManager;
import Frontiks.Craft.casino.util.Messages;

/**
 * fcp-CasinoRoulette — европейская рулетка для Paper 1.21.x.
 *
 * Валюта: монеты FCP (Coins) через {@code Frontiks.Craft.api.EconomyService};
 * при отсутствии FCP-EconomyMarket — тестовый кошелёк {@link FileBalanceProvider}.
 */
public class CasinoPlugin extends JavaPlugin {

    private static CasinoPlugin instance;

    private BalanceProvider balanceProvider;
    private TableManager tableManager;
    private ChipManager chipManager;

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
            // FCP-Core/EconomyMarket нет на сервере — работаем на тестовом кошельке
            fcpProvider = null;
        }
        if (fcpProvider != null) {
            this.balanceProvider = fcpProvider;
        } else {
            this.balanceProvider = new FileBalanceProvider(this);
            getLogger().warning("FCP Economy не найден — казино работает на тестовой валюте (balances.yml)");
        }

        this.tableManager = new TableManager(this);
        tableManager.load();
        getServer().getPluginManager().registerEvents(new TableListener(this), this);
        RouletteCommand executor = new RouletteCommand(this);
        var command = getCommand("roulette");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getLogger().info("fcp-CasinoRoulette включён. Столов: " + tableManager.all().size());
    }

    @Override
    public void onDisable() {
        if (tableManager != null) {
            tableManager.shutdown();
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

    public TableManager getTableManager() {
        return tableManager;
    }

    public ChipManager getChipManager() {
        return chipManager;
    }
}
