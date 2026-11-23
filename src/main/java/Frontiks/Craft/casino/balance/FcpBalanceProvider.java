package Frontiks.Craft.casino.balance;

import Frontiks.Craft.api.EconomyService;

import java.util.UUID;

/**
 * Провайдер валюты поверх FCP {@link EconomyService}.
 *
 * <p><b>ВАЖНО:</b> {@code removeCoins}/{@code removeDCoins} в FCP <b>не проверяют
 * достаточность</b> и молча сносят баланс в {@code 0}
 * ({@code Math.max(0, balance - amount)}). Поэтому {@link #withdraw} делает
 * <b>check-then-remove</b>: сначала проверяет баланс, только потом списывает.
 * Наивный {@code remove*} без проверки = слив чужих денег.</p>
 *
 * <p>Лимиты казино ({@code max-bet} = 100 000, {@code max-total-per-spin} = 500 000)
 * укладываются в {@code int}; на всякий случай long → int обрезается.</p>
 */
public class FcpBalanceProvider implements BalanceProvider {

    private final EconomyService economy;
    private final FcpCurrency currency;

    public FcpBalanceProvider(EconomyService economy, FcpCurrency currency) {
        this.economy = economy;
        this.currency = currency;
    }

    public FcpCurrency getCurrency() {
        return currency;
    }

    @Override
    public long getBalance(UUID player) {
        return currency == FcpCurrency.COINS
                ? economy.getCoins(player)
                : economy.getDCoins(player);
    }

    @Override
    public void deposit(UUID player, long amount) {
        if (amount <= 0) {
            return;
        }
        // казино-лимиты ≤ 500 000, переполнения int нет; на всякий случай режем
        int value = (int) Math.min(amount, Integer.MAX_VALUE);
        if (currency == FcpCurrency.COINS) {
            economy.addCoins(player, value);
        } else {
            economy.addDCoins(player, value);
        }
    }

    @Override
    public boolean withdraw(UUID player, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (amount > Integer.MAX_VALUE) {
            return false;
        }
        // check-then-remove: remove* не проверяет достаточность и режет баланс в 0
        if (getBalance(player) < amount) {
            return false;
        }
        if (currency == FcpCurrency.COINS) {
            economy.removeCoins(player, (int) amount);
        } else {
            economy.removeDCoins(player, (int) amount);
        }
        return true;
    }

    @Override
    public void save() {
        // no-op: FCP сам персистит свои балансы
    }
}
