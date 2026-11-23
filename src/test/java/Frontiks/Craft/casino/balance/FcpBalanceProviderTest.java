package Frontiks.Craft.casino.balance;

import Frontiks.Craft.api.EconomyService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FcpBalanceProviderTest {

    /**
     * Заглушка с РЕАЛЬНОЙ семантикой FCP EconomyManager:
     * removeCoins/removeDCoins не проверяют достаточность и режут баланс в 0.
     */
    private static final class FakeEconomy implements EconomyService {
        private final Map<UUID, Integer> coins = new HashMap<>();
        private final Map<UUID, Integer> dcoins = new HashMap<>();

        @Override
        public int getCoins(UUID player) {
            return coins.getOrDefault(player, 0);
        }

        @Override
        public void addCoins(UUID player, int amount) {
            if (amount <= 0) {
                return;
            }
            coins.merge(player, amount, (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
        }

        @Override
        public void removeCoins(UUID player, int amount) {
            if (amount <= 0) {
                return;
            }
            coins.put(player, Math.max(0, getCoins(player) - amount));
        }

        @Override
        public int getDCoins(UUID player) {
            return dcoins.getOrDefault(player, 0);
        }

        @Override
        public void addDCoins(UUID player, int amount) {
            if (amount <= 0) {
                return;
            }
            dcoins.merge(player, amount, (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
        }

        @Override
        public void removeDCoins(UUID player, int amount) {
            if (amount <= 0) {
                return;
            }
            dcoins.put(player, Math.max(0, getDCoins(player) - amount));
        }

        @Override
        public Map<UUID, Integer> getCoinsMap() {
            return new HashMap<>(coins);
        }
    }

    @Test
    void withdrawInsufficientReturnsFalseAndKeepsBalance() {
        FakeEconomy economy = new FakeEconomy();
        UUID player = UUID.randomUUID();
        economy.addCoins(player, 50);

        FcpBalanceProvider provider = new FcpBalanceProvider(economy, FcpCurrency.COINS);

        assertFalse(provider.withdraw(player, 100));
        assertEquals(50, provider.getBalance(player));
    }

    @Test
    void withdrawSufficientReturnsTrueAndRemovesAmount() {
        FakeEconomy economy = new FakeEconomy();
        UUID player = UUID.randomUUID();
        economy.addCoins(player, 500);

        FcpBalanceProvider provider = new FcpBalanceProvider(economy, FcpCurrency.COINS);

        assertTrue(provider.withdraw(player, 200));
        assertEquals(300, provider.getBalance(player));
    }

    @Test
    void depositNonPositiveIsNoOp() {
        FakeEconomy economy = new FakeEconomy();
        UUID player = UUID.randomUUID();
        FcpBalanceProvider provider = new FcpBalanceProvider(economy, FcpCurrency.COINS);

        provider.deposit(player, 0);
        provider.deposit(player, -10);
        assertEquals(0, provider.getBalance(player));
    }

    @Test
    void dcoinsSelectsDcoinsMethods() {
        FakeEconomy economy = new FakeEconomy();
        UUID player = UUID.randomUUID();
        economy.addDCoins(player, 1000);

        FcpBalanceProvider provider = new FcpBalanceProvider(economy, FcpCurrency.DCOINS);

        assertEquals(1000, provider.getBalance(player));
        assertTrue(provider.withdraw(player, 250));
        assertEquals(750, provider.getBalance(player));
        assertEquals(0, economy.getCoins(player));
    }

    @Test
    void longValuesAreClampedToInt() {
        FakeEconomy economy = new FakeEconomy();
        UUID player = UUID.randomUUID();
        economy.addCoins(player, Integer.MAX_VALUE);

        FcpBalanceProvider provider = new FcpBalanceProvider(economy, FcpCurrency.COINS);

        assertFalse(provider.withdraw(player, (long) Integer.MAX_VALUE + 1));
        provider.deposit(player, Long.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, provider.getBalance(player));
    }

    @Test
    void saveIsNoOp() {
        FakeEconomy economy = new FakeEconomy();
        UUID player = UUID.randomUUID();
        economy.addCoins(player, 100);
        FcpBalanceProvider provider = new FcpBalanceProvider(economy, FcpCurrency.COINS);
        assertDoesNotThrow(provider::save);
        assertEquals(100, provider.getBalance(player));
    }
}
