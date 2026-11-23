package Frontiks.Craft.casino.bets;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BetTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void keyIsStableAndDoesNotContainPlayer() {
        Bet first = new Bet(PLAYER, "A", BetType.SPLIT, Set.of(5, 8), 100);
        Bet second = new Bet(UUID.randomUUID(), "B", BetType.SPLIT, Set.of(5, 8), 999);

        assertEquals("SPLIT:5,8", first.key());
        assertEquals(first.key(), second.key());
        assertFalse(first.key().contains(PLAYER.toString()));
    }

    @Test
    void describeCoversAllBetTypes() {
        assertEquals("число 7", new Bet(PLAYER, "A", BetType.STRAIGHT, Set.of(7), 1).describe());
        assertEquals("сплит (2 числа) 5-8", new Bet(PLAYER, "A", BetType.SPLIT, Set.of(5, 8), 1).describe());
        assertEquals("стрит (3 числа) 1-2-3", new Bet(PLAYER, "A", BetType.STREET, Set.of(1, 2, 3), 1).describe());
        assertEquals("каре (4 числа) 1-2-4-5",
                new Bet(PLAYER, "A", BetType.CORNER, Set.of(1, 2, 4, 5), 1).describe());
        assertEquals("шесть номеров 1-2-3-4-5-6",
                new Bet(PLAYER, "A", BetType.SIXLINE, Set.of(1, 2, 3, 4, 5, 6), 1).describe());
        assertEquals("дюжина 1-12",
                new Bet(PLAYER, "A", BetType.DOZEN, Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), 1).describe());
        assertEquals("колонка 1-34",
                new Bet(PLAYER, "A", BetType.COLUMN,
                        Set.of(1, 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34), 1).describe());
        assertEquals("красное", new Bet(PLAYER, "A", BetType.RED, Set.of(1, 3, 5), 1).describe());
        assertEquals("чёрное", new Bet(PLAYER, "A", BetType.BLACK, Set.of(2, 4, 6), 1).describe());
        assertEquals("чёт", new Bet(PLAYER, "A", BetType.EVEN, Set.of(2, 4), 1).describe());
        assertEquals("нечёт", new Bet(PLAYER, "A", BetType.ODD, Set.of(1, 3), 1).describe());
        assertEquals("1-18", new Bet(PLAYER, "A", BetType.LOW, Set.of(1, 2), 1).describe());
        assertEquals("19-36", new Bet(PLAYER, "A", BetType.HIGH, Set.of(19, 20), 1).describe());
    }

    @Test
    void addAmountAccumulates() {
        Bet bet = new Bet(PLAYER, "A", BetType.RED, Set.of(1), 100);
        bet.addAmount(50);
        assertEquals(150, bet.getAmount());
    }

    @Test
    void numbersAreCopiedAndSorted() {
        Bet bet = new Bet(PLAYER, "A", BetType.STREET, new TreeSet<>(Set.of(3, 1, 2)), 1);
        assertEquals(new TreeSet<>(Set.of(1, 2, 3)), new TreeSet<>(bet.getNumbers()));
    }
}
