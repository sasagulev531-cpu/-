package Frontiks.Craft.casino.util;

import java.util.Set;

/**
 * Европейская рулетка: порядок чисел на колесе и цвета.
 * Порядок чисел против часовой стрелки (как на реальном европейском колесе).
 */
public final class WheelNumbers {

    /** Индекс числа в массиве = позиция лунки на колесе. */
    public static final int[] EUROPEAN_ORDER = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
            24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    };

    private static final Set<Integer> REDS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private WheelNumbers() {
    }

    public static boolean isRed(int number) {
        return REDS.contains(number);
    }

    public static boolean isBlack(int number) {
        return number != 0 && !REDS.contains(number);
    }

    /** Позиция числа на колесе (0..36) или -1. */
    public static int indexOf(int number) {
        for (int i = 0; i < EUROPEAN_ORDER.length; i++) {
            if (EUROPEAN_ORDER[i] == number) {
                return i;
            }
        }
        return -1;
    }

}
