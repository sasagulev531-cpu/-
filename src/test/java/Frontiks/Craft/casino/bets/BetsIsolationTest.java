package Frontiks.Craft.casino.bets;

import Frontiks.Craft.casino.util.WheelNumbers;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetsIsolationTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000012");

    private static Set<Integer> reds() {
        Set<Integer> set = new TreeSet<>();
        for (int n = 1; n <= 36; n++) {
            if (WheelNumbers.isRed(n)) {
                set.add(n);
            }
        }
        return set;
    }

    private static Set<Integer> blacks() {
        Set<Integer> set = new TreeSet<>();
        for (int n = 1; n <= 36; n++) {
            if (WheelNumbers.isBlack(n)) {
                set.add(n);
            }
        }
        return set;
    }

    @Test
    void twoPlayersOnSamePositionStayTwoBets() {
        BetBook book = new BetBook();
        Bet alice = new Bet(ALICE, "Alice", BetType.RED, reds(), 100);
        Bet bob = new Bet(BOB, "Bob", BetType.RED, reds(), 100);

        assertEquals(BetBook.Status.OK, book.validate(alice, 10, 100000, 500000));
        book.put(alice);
        assertEquals(BetBook.Status.OK, book.validate(bob, 10, 100000, 500000));
        book.put(bob);

        assertEquals(2, book.size());
        assertEquals(100, book.get(ALICE, alice).getAmount());
        assertEquals(100, book.get(BOB, bob).getAmount());
        assertFalse(book.get(ALICE, alice) == book.get(BOB, bob));
    }

    @Test
    void samePlayerSamePositionMergesIntoOneBet() {
        BetBook book = new BetBook();
        Bet first = new Bet(ALICE, "Alice", BetType.RED, reds(), 100);
        Bet second = new Bet(ALICE, "Alice", BetType.RED, reds(), 50);

        book.put(first);
        assertTrue(book.put(second));

        assertEquals(1, book.size());
        assertEquals(150, book.get(ALICE, second).getAmount());
    }

    @Test
    void positionLimitIsSharedAcrossPlayers() {
        BetBook book = new BetBook();
        Bet alice = new Bet(ALICE, "Alice", BetType.RED, reds(), 99990);
        assertEquals(BetBook.Status.OK, book.validate(alice, 10, 100000, 500000));
        book.put(alice);

        Bet bob = new Bet(BOB, "Bob", BetType.RED, reds(), 10);
        assertEquals(BetBook.Status.ABOVE_MAX, book.validate(bob, 10, 100000, 500000));

        // на другую позицию B может
        Bet bobOther = new Bet(BOB, "Bob", BetType.BLACK, blacks(), 10);
        assertEquals(BetBook.Status.OK, book.validate(bobOther, 10, 100000, 500000));
    }

    @Test
    void totalLimitIsPerPlayer() {
        BetBook book = new BetBook();
        Bet alice = new Bet(ALICE, "Alice", BetType.RED, reds(), 100000);
        assertEquals(BetBook.Status.OK, book.validate(alice, 10, 200000, 100000));
        book.put(alice);

        // A больше не может, B — может (лимит на игрока)
        Bet aliceMore = new Bet(ALICE, "Alice", BetType.BLACK, blacks(), 10);
        assertEquals(BetBook.Status.ABOVE_TOTAL, book.validate(aliceMore, 10, 200000, 100000));

        Bet bob = new Bet(BOB, "Bob", BetType.BLACK, blacks(), 10);
        assertEquals(BetBook.Status.OK, book.validate(bob, 10, 200000, 100000));
    }
    @Test
    void clearingOnlyOnePlayerRefundsTheirOwnBets() {
        BetBook book = new BetBook();
        book.put(new Bet(ALICE, "Alice", BetType.RED, reds(), 100));
        book.put(new Bet(ALICE, "Alice", BetType.BLACK, blacks(), 50));
        book.put(new Bet(BOB, "Bob", BetType.RED, reds(), 70));
        assertEquals(220, book.total());
        assertEquals(150, book.playerTotal(ALICE));
        assertEquals(150, book.removePlayer(ALICE));
        assertEquals(70, book.total());
        assertEquals(70, book.playerTotal(BOB));
        assertEquals(1, book.size());
    }
}
