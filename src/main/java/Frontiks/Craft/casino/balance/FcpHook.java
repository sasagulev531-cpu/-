package Frontiks.Craft.casino.balance;

import Frontiks.Craft.api.EconomyService;
import Frontiks.Craft.api.FcpServices;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Изолированная точка подключения FCP.
 *
 * <p>Класс ссылается на FCP-Core. Если FCP-Core/EconomyMarket нет на сервере,
 * его загрузка/вызов кидает {@link NoClassDefFoundError}; {@code CasinoPlugin}
 * ловит это и уходит на тестовый кошелёк.</p>
 */
public final class FcpHook {

    private FcpHook() {
    }

    /** @return провайдер FCP или {@code null}, если сервис не зарегистрирован */
    public static BalanceProvider create(JavaPlugin plugin) {
        EconomyService economy = FcpServices.find(EconomyService.class);
        if (economy == null) {
            return null;
        }
        FcpCurrency currency = FcpCurrency.fromConfig(
                plugin.getConfig().getString("fcp-currency", "coins"));
        plugin.getLogger().info("Валюта: монеты FCP (" + currency + ")");
        return new FcpBalanceProvider(economy, currency);
    }
}
