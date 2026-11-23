package Frontiks.Craft.casino.bets;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ставка игрока. Позиционный ключ ставки — тип + набор чисел
 * ({@link #key()}). Полный ключ в реестре ставок —
 * {@code UUID игрока + ":" + positionKey} (см. {@code BetBook}),
 * поэтому ставки разных игроков на одной позиции не склеиваются.
 *
 * <p>Повторный клик того же игрока по той же позиции увеличивает сумму
 * его собственной ставки (склейка внутри игрока).</p>
 */
public class Bet {

    private final UUID player;
    private final String playerName;
    private final BetType type;
    private final Set<Integer> numbers;
    private long amount;

    public Bet(UUID player, String playerName, BetType type, Set<Integer> numbers, long amount) {
        this.player = player;
        this.playerName = playerName;
        this.type = type;
        this.numbers = new TreeSet<>(numbers);
        this.amount = amount;
    }

    public UUID getPlayer() {
        return player;
    }

    public String getPlayerName() {
        return playerName;
    }

    public BetType getType() {
        return type;
    }

    public Set<Integer> getNumbers() {
        return numbers;
    }

    public long getAmount() {
        return amount;
    }

    public void addAmount(long delta) {
        this.amount += delta;
    }

    /**
     * Позиционный ключ ставки: {@code TYPE:числа} (без игрока), числа отсортированы.
     *
     * <p>ВНИМАНИЕ: это ключ <b>позиции</b>, а не полный ключ реестра.
     * В {@code BetBook} ставки хранятся под ключом {@code UUID:positionKey},
     * иначе ставки разных игроков на одной позиции сливаются (баг R1).</p>
     */
    public String key() {
        return positionKey(type, numbers);
    }

    /** Позиционный ключ по типу и набору чисел (без игрока). */
    public static String positionKey(BetType type, Set<Integer> numbers) {
        return type.name() + ":" + numbers.stream().sorted()
                .map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Человекочитаемое описание, например "сплит 5-8" или "красное". */
    public String describe() {
        if (type == BetType.STRAIGHT) {
            return type.getLabel() + " " + numbers.iterator().next();
        }
        if (type == BetType.DOZEN || type == BetType.COLUMN) {
            return type.getLabel() + " " + numbers.iterator().next() + "-" + ((TreeSet<Integer>) numbers).last();
        }
        if (type == BetType.SPLIT || type == BetType.STREET || type == BetType.CORNER
                || type == BetType.SIXLINE) {
            return type.getLabel() + " " + numbers.stream().map(String::valueOf).collect(Collectors.joining("-"));
        }
        return type.getLabel();
    }
}
