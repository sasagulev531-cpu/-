package Frontiks.Craft.casino.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelNumbersTest {

    @Test
    void europeanOrderHas37UniqueNumbersFrom0To36() {
        assertEquals(37, WheelNumbers.EUROPEAN_ORDER.length);
        Set<Integer> seen = new HashSet<>();
        for (int number : WheelNumbers.EUROPEAN_ORDER) {
            assertTrue(number >= 0 && number <= 36, "число вне диапазона: " + number);
            assertTrue(seen.add(number), "повтор числа: " + number);
        }
        assertEquals(37, seen.size());
    }

    @Test
    void exactly18RedNumbers() {
        int red = 0;
        for (int number = 1; number <= 36; number++) {
            if (WheelNumbers.isRed(number)) {
                red++;
            }
        }
        assertEquals(18, red);
    }

    @Test
    void zeroIsNeitherRedNorBlack() {
        assertFalse(WheelNumbers.isRed(0));
        assertFalse(WheelNumbers.isBlack(0));
    }

    @Test
    void everyNumberIsEitherRedOrBlackOrZero() {
        for (int number = 0; number <= 36; number++) {
            if (number == 0) {
                assertFalse(WheelNumbers.isRed(number) || WheelNumbers.isBlack(number));
            } else {
                assertTrue(WheelNumbers.isRed(number) ^ WheelNumbers.isBlack(number),
                        "число " + number + " должно быть ровно одного цвета");
            }
        }
    }
}
