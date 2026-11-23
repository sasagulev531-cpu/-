package Frontiks.Craft.casino.bets;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Реестр ставок за одну прокрутку.
 *
 * <p>Ключ = {@code UUID игрока + ":" + Bet.key()}. Именно поэтому две ставки
 * разных игроков на одной позиции остаются двумя независимыми ставками:
 * первая часть ключа различается. Повторный клик того же игрока по той же
 * позиции склеивается с его собственной ставкой.</p>
 */
public final class BetBook {

    /** Результат проверки лимитов перед добавлением ставки. */
    public enum Status {
        OK,
        BELOW_MIN,
        ABOVE_MAX,
        ABOVE_TOTAL
    }

    private final Map<String, Bet> bets = new LinkedHashMap<>();

    /** Полный ключ реестра: {@code UUID:positionKey}. */
    public static String key(UUID player, Bet bet) {
        return player + ":" + bet.key();
    }

    public Collection<Bet> all() {
        return Collections.unmodifiableCollection(bets.values());
    }

    public boolean isEmpty() {
        return bets.isEmpty();
    }

    public int size() {
        return bets.size();
    }

    public void clear() {
        bets.clear();
    }

    public Bet get(UUID player, Bet bet) {
        return bets.get(key(player, bet));
    }

    /** Ставка игрока на конкретной позиции (тип + числа) или null. */
    public Bet get(UUID player, BetType type, java.util.Set<Integer> numbers) {
        return bets.get(player + ":" + Bet.positionKey(type, numbers));
    }

    /** Суммарная ставка игрока за прокрутку. */
    public long playerTotal(UUID player) {
        return bets.values().stream()
                .filter(b -> b.getPlayer().equals(player))
                .mapToLong(Bet::getAmount)
                .sum();
    }

    /** Суммарный банк всех игроков за прокрутку. */
    public long total() {
        return bets.values().stream().mapToLong(Bet::getAmount).sum();
    }

    /**
     * Снимает все ставки игрока (для кнопки «сбросить мои ставки»).
     *
     * @return сумма снятых ставок — её нужно вернуть игроку
     */
    public long removePlayer(UUID player) {
        long refunded = 0;
        java.util.Iterator<Bet> it = bets.values().iterator();
        while (it.hasNext()) {
            Bet bet = it.next();
            if (bet.getPlayer().equals(player)) {
                refunded += bet.getAmount();
                it.remove();
            }
        }
        return refunded;
    }

    /**
     * Проверяет лимиты, не изменяя реестр.
     *
     * <ul>
     *   <li>{@code BELOW_MIN} — ставка меньше {@code minBet};</li>
     *   <li>{@code ABOVE_MAX} — суммарная ставка всех игроков на позиции
     *       достигла {@code maxBet} (порог считается достигнутым при
     *       {@code positionTotal >= maxBet});</li>
     *   <li>{@code ABOVE_TOTAL} — суммарная ставка этого игрока за прокрутку
     *       превысила {@code maxTotal}.</li>
     * </ul>
     */
    public Status validate(Bet bet, long minBet, long maxBet, long maxTotal) {
        long amount = bet.getAmount();
        if (amount < minBet) {
            return Status.BELOW_MIN;
        }

        // Лимит позиции — суммарный по ВСЕМ игрокам на этой позиции.
        long positionTotal = amount + bets.values().stream()
                .filter(b -> b.key().equals(bet.key()))
                .mapToLong(Bet::getAmount)
                .sum();
        if (positionTotal >= maxBet) {
            return Status.ABOVE_MAX;
        }

        // Лимит прокрутки — только для этого игрока.
        long playerTotal = amount + bets.values().stream()
                .filter(b -> b.getPlayer().equals(bet.getPlayer()))
                .mapToLong(Bet::getAmount)
                .sum();
        if (playerTotal > maxTotal) {
            return Status.ABOVE_TOTAL;
        }
        return Status.OK;
    }

    /**
     * Добавляет ставку или склеивает с существующей ставкой того же игрока
     * на той же позиции. Лимиты не проверяет — сначала вызвать {@link #validate}.
     *
     * @return {@code true}, если ставка склеена с существующей
     */
    public boolean put(Bet bet) {
        String key = key(bet.getPlayer(), bet);
        Bet existing = bets.get(key);
        if (existing != null) {
            existing.addAmount(bet.getAmount());
            return true;
        }
        bets.put(key, bet);
        return false;
    }
}
