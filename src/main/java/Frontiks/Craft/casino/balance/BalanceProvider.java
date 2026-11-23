package Frontiks.Craft.casino.balance;

import java.util.UUID;

/**
 * Абстракция экономики.
 *
 * <p>Основная реализация — {@link FcpBalanceProvider} (монеты FCP через
 * {@code EconomyService}); {@link FileBalanceProvider} — тестовый fallback
 * для стендов без FCP-EconomyMarket.</p>
 *
 */
public interface BalanceProvider {

    long getBalance(UUID player);

    /** Пополнить баланс. */
    void deposit(UUID player, long amount);

    /**
     * Списать средства.
     *
     * @return true — средства списаны; false — недостаточно средств, баланс не изменён
     */
    boolean withdraw(UUID player, long amount);

    /** Сохранить данные (вызывается при выключении сервера). */
    void save();
}
