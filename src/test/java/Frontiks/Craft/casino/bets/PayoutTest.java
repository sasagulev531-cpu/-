package Frontiks.Craft.casino.bets;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayoutTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000021");

    @Test
    void payoutWithStakeReturned() {
        for (BetType type : BetType.values()) {
            Bet bet = new Bet(PLAYER, "tester", type, Set.of(1), 100);
            long expected = 100L * type.getPayout() + 100L;
            assertEquals(expected, Payout.compute(bet, true), type.name());
        }
    }

    @Test
    void payoutWithoutStakeReturned() {
        for (BetType type : BetType.values()) {
            Bet bet = new Bet(PLAYER, "tester", type, Set.of(1), 100);
            long expected = 100L * type.getPayout();
            assertEquals(expected, Payout.compute(bet, false), type.name());
        }
    }
}
