package Frontiks.Craft.casino.bets;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BetDetectorTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static Bet detect(double lx, double lz, double u, double v) {
        return BetDetector.guess(
                (type, numbers, amount) -> new Bet(PLAYER, "tester", type, numbers, amount),
                lx, lz, u, v);
    }

    private static Set<Integer> range(int from, int to) {
        Set<Integer> set = new TreeSet<>();
        for (int n = from; n <= to; n++) {
            set.add(n);
        }
        return set;
    }

    @Test
    void zeroIsStraightZero() {
        Bet bet = detect(-1, 8.5, 0.5, 0.5);
        assertNotNull(bet);
        assertEquals(BetType.STRAIGHT, bet.getType());
        assertEquals(Set.of(0), bet.getNumbers());
    }

    @Test
    void straightNumber() {
        Bet bet = detect(0, 8, 0.5, 0.5);
        assertEquals(BetType.STRAIGHT, bet.getType());
        assertEquals(Set.of(1), bet.getNumbers());
    }

    @Test
    void split() {
        Bet bet = detect(1, 8, 0.1, 0.5);
        assertEquals(BetType.SPLIT, bet.getType());
        assertEquals(Set.of(1, 4), bet.getNumbers());
    }

    @Test
    void street() {
        Bet bet = detect(0, 8, 0.1, 0.5);
        assertEquals(BetType.STREET, bet.getType());
        assertEquals(Set.of(1, 2, 3), bet.getNumbers());
    }

    @Test
    void corner() {
        Bet bet = detect(1, 9, 0.2, 0.2);
        assertEquals(BetType.CORNER, bet.getType());
        assertEquals(Set.of(1, 2, 4, 5), bet.getNumbers());
    }

    @Test
    void sixLine() {
        Bet bet = detect(1, 9, 0.1, 0.1);
        assertEquals(BetType.SIXLINE, bet.getType());
        assertEquals(Set.of(1, 2, 3, 4, 5, 6), bet.getNumbers());
    }

    @Test
    void dozen() {
        Bet bet = detect(1.5, 11.5, 0.5, 0.5);
        assertEquals(BetType.DOZEN, bet.getType());
        assertEquals(range(1, 12), bet.getNumbers());
    }

    @Test
    void column() {
        Bet bet = detect(12.5, 8.5, 0.5, 0.5);
        assertEquals(BetType.COLUMN, bet.getType());
        Set<Integer> expected = new TreeSet<>();
        for (int n = 1; n <= 36; n++) {
            if (n % 3 == 1) {
                expected.add(n);
            }
        }
        assertEquals(expected, bet.getNumbers());
    }

    @Test
    void evenMoneyBets() {
        assertEquals(BetType.LOW, detect(0.5, 12.5, 0.5, 0.5).getType());
        assertEquals(BetType.EVEN, detect(2.5, 12.5, 0.5, 0.5).getType());
        assertEquals(BetType.RED, detect(4.5, 12.5, 0.5, 0.5).getType());
        assertEquals(BetType.BLACK, detect(6.5, 12.5, 0.5, 0.5).getType());
        assertEquals(BetType.ODD, detect(8.5, 12.5, 0.5, 0.5).getType());
        assertEquals(BetType.HIGH, detect(10.5, 12.5, 0.5, 0.5).getType());
    }

    @Test
    void outsideTableIsNull() {
        assertNull(detect(20, 20, 0.5, 0.5));
        assertNull(detect(-5, 8.5, 0.5, 0.5));
    }
}
