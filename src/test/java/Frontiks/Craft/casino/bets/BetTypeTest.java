package Frontiks.Craft.casino.bets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BetTypeTest {

    @Test
    void payoutTableMatchesEuropeanRouletteInvariant() {
        assertEquals(35, BetType.STRAIGHT.getPayout());
        assertEquals(17, BetType.SPLIT.getPayout());
        assertEquals(11, BetType.STREET.getPayout());
        assertEquals(8, BetType.CORNER.getPayout());
        assertEquals(5, BetType.SIXLINE.getPayout());
        assertEquals(2, BetType.DOZEN.getPayout());
        assertEquals(2, BetType.COLUMN.getPayout());
        assertEquals(1, BetType.RED.getPayout());
        assertEquals(1, BetType.BLACK.getPayout());
        assertEquals(1, BetType.EVEN.getPayout());
        assertEquals(1, BetType.ODD.getPayout());
        assertEquals(1, BetType.LOW.getPayout());
        assertEquals(1, BetType.HIGH.getPayout());
    }

    @Test
    void exactly13BetTypes() {
        assertEquals(13, BetType.values().length);
    }
}
