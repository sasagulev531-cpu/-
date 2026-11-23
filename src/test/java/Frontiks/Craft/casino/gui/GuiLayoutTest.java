package Frontiks.Craft.casino.gui;

import Frontiks.Craft.casino.bets.BetType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GuiLayoutTest {
    @Test void everyNumberHasAUniqueSlot() {
        for (int n = 0; n <= 36; n++) {
            int slot = GuiLayout.NUMBER_START + n;
            assertEquals(n, GuiLayout.numberAt(slot));
            assertEquals(new GuiLayout.BetSpec(BetType.STRAIGHT, Set.of(n)),
                    GuiLayout.betAt(GuiLayout.PAGE_NUMBERS, slot));
        }
        assertEquals(-1, GuiLayout.numberAt(46));
        assertNull(GuiLayout.betAt(GuiLayout.PAGE_NUMBERS, GuiLayout.PAGE_SWITCH));
    }

    @Test void outsideBetsHaveExactlyEighteenNumbersAndNeverZero() {
        for (int page : new int[]{GuiLayout.PAGE_NUMBERS, GuiLayout.PAGE_OUTSIDE}) {
            int start = page == GuiLayout.PAGE_NUMBERS ? GuiLayout.QUICK_OUTSIDE_START : GuiLayout.OUTSIDE_START;
            for (int slot = start; slot < start + 6; slot++) {
                var bet = GuiLayout.betAt(page, slot);
                assertEquals(18, bet.numbers().size());
                assertFalse(bet.numbers().contains(0));
            }
        }
        assertEquals(18, GuiLayout.redNumbers().size());
        assertEquals(18, GuiLayout.blackNumbers().size());
    }

    @Test void dozensAndColumns() {
        for (int i = 0; i < 3; i++) {
            var dozen = GuiLayout.betAt(GuiLayout.PAGE_OUTSIDE, GuiLayout.DOZEN_START + i);
            var column = GuiLayout.betAt(GuiLayout.PAGE_OUTSIDE, GuiLayout.COLUMN_START + i);
            assertEquals(BetType.DOZEN, dozen.type());
            assertEquals(BetType.COLUMN, column.type());
            assertEquals(12, dozen.numbers().size());
            assertEquals(12, column.numbers().size());
            assertTrue(dozen.numbers().contains(i * 12 + 1));
            assertTrue(column.numbers().contains(i + 1));
        }
    }

    @Test void combinationAnchorsAndEdges() {
        assertEquals(Set.of(1, 2), GuiLayout.combinationAt(BetType.SPLIT, 1, false).numbers());
        assertEquals(Set.of(1, 4), GuiLayout.combinationAt(BetType.SPLIT, 1, true).numbers());
        assertEquals(Set.of(1, 2, 3), GuiLayout.combinationAt(BetType.STREET, 1, false).numbers());
        assertEquals(Set.of(1, 2, 4, 5), GuiLayout.combinationAt(BetType.CORNER, 1, false).numbers());
        assertEquals(Set.of(1, 2, 3, 4, 5, 6), GuiLayout.combinationAt(BetType.SIXLINE, 1, false).numbers());
        assertNull(GuiLayout.combinationAt(BetType.SPLIT, 3, false));
        assertNull(GuiLayout.combinationAt(BetType.SPLIT, 36, true));
        assertNull(GuiLayout.combinationAt(BetType.STREET, 2, false));
        assertNull(GuiLayout.combinationAt(BetType.CORNER, 35, false));
        assertNull(GuiLayout.combinationAt(BetType.SIXLINE, 34, false));
        assertNull(GuiLayout.combinationAt(BetType.SIXLINE, 0, false));
    }
}
