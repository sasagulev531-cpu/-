package Frontiks.Craft.casino.gui;

import Frontiks.Craft.casino.bets.BetType;
import Frontiks.Craft.casino.util.WheelNumbers;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Чистая раскладка GUI-рулетки: какой слот какой ставке соответствует.
 * Не зависит от Bukkit — покрыто юнит-тестами.
 *
 * <p>Инвентарь 6×9 = 54 слота:</p>
 * <pre>
 *  0        1..5        6        7        8
 *  [инфо] [фишки ×5] [баланс] [сброс] [закрыть]
 *
 *  9..45 — числа 0..36 (слот 9 = зеро, 10..45 = 1..36)
 * 46..51 — быстрые внешние: красное, чёрное, чёт, нечёт, 1-18, 19-36
 * 52     — переключение страницы
 * 53     — заполнитель
 *
 * Страница 1 («Внешние ставки»):
 *  9..11  — дюжины 1-12 / 13-24 / 25-36
 * 12..14  — колонки 1 / 2 / 3
 * 15..20  — красное, чёрное, чёт, нечёт, 1-18, 19-36
 *
 * Страница 2 («Комбинации»):
 * 9..45 — начальное число комбинации (невозможные якоря неактивны)
 * 46..49 — выбор сплита, стрита, каре или шести номеров
 * 50 — направление сплита (горизонтально/вертикально)
 * </pre>
 */
public final class GuiLayout {

    public static final int SIZE = 54;

    public static final int INFO = 0;
    public static final int CHIP_START = 1;
    public static final int CHIP_COUNT = 5;
    public static final int BALANCE = 6;
    public static final int CLEAR_BETS = 7;
    public static final int CLOSE = 8;

    public static final int NUMBER_START = 9;
    public static final int NUMBER_COUNT = 37;
    public static final int NUMBER_END = NUMBER_START + NUMBER_COUNT - 1;

    public static final int QUICK_OUTSIDE_START = 46;
    public static final int PAGE_SWITCH = 52;

    public static final int PAGE_NUMBERS = 0;
    public static final int PAGE_OUTSIDE = 1;
    public static final int PAGE_COMBINATIONS = 2;
    public static final int COMBO_START = 46;
    public static final int COMBO_ORIENTATION = 50;

    public static final int DOZEN_START = 9;
    public static final int COLUMN_START = 12;
    public static final int OUTSIDE_START = 15;

    private GuiLayout() {
    }

    /** Ставка, соответствующая слоту, или {@code null}, если слот не игровой. */
    public record BetSpec(BetType type, Set<Integer> numbers) {
    }

    /** @return спецификация ставки для слота на странице или {@code null} */
    public static BetSpec betAt(int page, int slot) {
        if (page == PAGE_NUMBERS) {
            if (slot >= NUMBER_START && slot <= NUMBER_END) {
                return new BetSpec(BetType.STRAIGHT, Set.of(slot - NUMBER_START));
            }
            if (slot >= QUICK_OUTSIDE_START && slot < QUICK_OUTSIDE_START + 6) {
                return outsideSpec(slot - QUICK_OUTSIDE_START);
            }
            return null;
        }
        if (page == PAGE_OUTSIDE) {
            if (slot >= DOZEN_START && slot < DOZEN_START + 3) {
                int idx = slot - DOZEN_START;
                return new BetSpec(BetType.DOZEN, range(idx * 12 + 1, idx * 12 + 12, 1));
            }
            if (slot >= COLUMN_START && slot < COLUMN_START + 3) {
                int idx = slot - COLUMN_START;
                int mod = (idx + 1) % 3; // 1, 2, 0
                Set<Integer> nums = new TreeSet<>();
                for (int n = 1; n <= 36; n++) {
                    if (n % 3 == mod) {
                        nums.add(n);
                    }
                }
                return new BetSpec(BetType.COLUMN, nums);
            }
            if (slot >= OUTSIDE_START && slot < OUTSIDE_START + 6) {
                return outsideSpec(slot - OUTSIDE_START);
            }
            return null;
        }
        return null;
    }

    /** Комбинация по якорному числу, без 0; null для недопустимого якоря. */
    public static BetSpec combinationAt(BetType type, int number, boolean vertical) {
        if (number < 1 || number > 36) {
            return null;
        }
        return switch (type) {
            case SPLIT -> vertical
                    ? number <= 33 ? new BetSpec(type, Set.of(number, number + 3)) : null
                    : number % 3 != 0 ? new BetSpec(type, Set.of(number, number + 1)) : null;
            case STREET -> number % 3 == 1
                    ? new BetSpec(type, Set.of(number, number + 1, number + 2)) : null;
            case CORNER -> number <= 33 && number % 3 != 0
                    ? new BetSpec(type, Set.of(number, number + 1, number + 3, number + 4)) : null;
            case SIXLINE -> number <= 31 && number % 3 == 1
                    ? new BetSpec(type, Set.of(number, number + 1, number + 2,
                            number + 3, number + 4, number + 5)) : null;
            default -> null;
        };
    }

    private static BetSpec outsideSpec(int idx) {
        return switch (idx) {
            case 0 -> new BetSpec(BetType.RED, redNumbers());
            case 1 -> new BetSpec(BetType.BLACK, blackNumbers());
            case 2 -> new BetSpec(BetType.EVEN, range(2, 36, 2));
            case 3 -> new BetSpec(BetType.ODD, range(1, 35, 2));
            case 4 -> new BetSpec(BetType.LOW, range(1, 18, 1));
            case 5 -> new BetSpec(BetType.HIGH, range(19, 36, 1));
            default -> null;
        };
    }

    public static Set<Integer> redNumbers() {
        Set<Integer> set = new LinkedHashSet<>();
        for (int n = 1; n <= 36; n++) {
            if (WheelNumbers.isRed(n)) {
                set.add(n);
            }
        }
        return set;
    }

    public static Set<Integer> blackNumbers() {
        Set<Integer> set = new LinkedHashSet<>();
        for (int n = 1; n <= 36; n++) {
            if (WheelNumbers.isBlack(n)) {
                set.add(n);
            }
        }
        return set;
    }

    private static Set<Integer> range(int from, int to, int step) {
        Set<Integer> set = new LinkedHashSet<>();
        for (int n = from; n <= to; n += step) {
            set.add(n);
        }
        return set;
    }

    /** Число, соответствующее слоту на странице чисел, или -1. */
    public static int numberAt(int slot) {
        if (slot >= NUMBER_START && slot <= NUMBER_END) {
            return slot - NUMBER_START;
        }
        return -1;
    }
}
