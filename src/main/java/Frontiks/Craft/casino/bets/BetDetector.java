package Frontiks.Craft.casino.bets;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Определяет ставку по локальным координатам клика на столе.
 *
 * Локальная система координат стола (ось X — вправо от игрока, Z — вперёд, к колесу):
 *   колесо — в начале координат (радиус ~7);
 *   сетка чисел:      lx [0..12),  lz [8..11)   (12 колонок x 3 ряда, ячейка = 1 блок)
 *   зеро:             lx [-1..0),  lz [8..11)
 *   дюжины:           lz [11..12), по 4 колонки
 *   колонки (2to1):   lx [12..13), 3 блока по рядам
 *   равные шансы:     lz [12..13), 6 зон по 2 блока
 *
 * Число в ячейке (колонка c = 0..11, ряд r: 0 — ближний, 2 — дальний): n = 3c + r + 1.
 *
 * Внутри ячейки (u, v — дробная позиция клика в блоке, 0..1):
 *   центр (0.25..0.75)²            — прямо на число
 *   боковая грань                  — сплит; на крайней колонке — стрит
 *   торец линии (совсем у края)    — шесть номеров
 *   угол (глубже края)             — каре
 */
public final class BetDetector {

    public static final double GRID_LX0 = 0;
    public static final double GRID_LZ0 = 8;
    public static final int COLUMNS = 12;
    public static final int ROWS = 3;

    private static final double EDGE = 0.25;   // грань ячейки
    private static final double EXTREME = 0.12; // «совсем у края» — торец линии

    private BetDetector() {
    }

    /** Число в ячейке сетки: r=0 (ближний ряд) -> 3c+1, r=2 (дальний) -> 3c+3. */
    public static int numberAt(int column, int row) {
        return 3 * column + row + 1;
    }

    /**
     * Определяет ставку по позиции клика.
     *
     * @param lx,z  локальные координаты кликнутого блока (целочисленные границы ячеек)
     * @param u     дробная позиция внутри блока вдоль оси X (0..1)
     * @param v     дробная позиция внутри блока вдоль оси Z (0..1)
     * @return ставка или null, если клик вне игровой зоны
     */
    public static Bet guess(UUIDHolder holder, double lx, double lz, double u, double v) {
        // --- Зеро ---
        if (lx >= -1 && lx < 0 && lz >= GRID_LZ0 && lz < GRID_LZ0 + ROWS) {
            return holder.create(BetType.STRAIGHT, Set.of(0), 0);
        }

        // --- Сетка чисел ---
        if (lx >= GRID_LX0 && lx < GRID_LX0 + COLUMNS && lz >= GRID_LZ0 && lz < GRID_LZ0 + ROWS) {
            int c = (int) Math.floor(lx - GRID_LX0);
            int r = (int) Math.floor(lz - GRID_LZ0);
            return gridBet(holder, c, r, u, v);
        }

        // --- Дюжины ---
        if (lz >= GRID_LZ0 + ROWS && lz < GRID_LZ0 + ROWS + 1 && lx >= 0 && lx < COLUMNS) {
            int dozen = (int) Math.floor(lx / 4);
            Set<Integer> nums = new LinkedHashSet<>();
            for (int n = dozen * 12 + 1; n <= dozen * 12 + 12; n++) {
                nums.add(n);
            }
            return holder.create(BetType.DOZEN, nums, 0);
        }

        // --- Колонки 2to1 (r: 0 -> 1,4,7..., 1 -> 2,5,8..., 2 -> 3,6,9...) ---
        if (lx >= COLUMNS && lx < COLUMNS + 1 && lz >= GRID_LZ0 && lz < GRID_LZ0 + ROWS) {
            int r = (int) Math.floor(lz - GRID_LZ0);
            int mod = (r + 1) % 3; // 1,2,0
            Set<Integer> nums = new LinkedHashSet<>();
            for (int n = 1; n <= 36; n++) {
                if (n % 3 == mod) {
                    nums.add(n);
                }
            }
            return holder.create(BetType.COLUMN, nums, 0);
        }

        // --- Равные шансы ---
        if (lz >= GRID_LZ0 + ROWS + 1 && lz < GRID_LZ0 + ROWS + 2 && lx >= 0 && lx < COLUMNS) {
            int idx = (int) Math.floor(lx / 2); // 0..5
            return switch (idx) {
                case 0 -> holder.create(BetType.LOW, range(1, 18), 0);
                case 1 -> holder.create(BetType.EVEN, evens(true), 0);
                case 2 -> holder.create(BetType.RED, reds(), 0);
                case 3 -> holder.create(BetType.BLACK, blacks(), 0);
                case 4 -> holder.create(BetType.ODD, evens(false), 0);
                default -> holder.create(BetType.HIGH, range(19, 36), 0);
            };
        }

        return null;
    }

    private static Bet gridBet(UUIDHolder holder, int c, int r, double u, double v) {
        int n = numberAt(c, r);
        boolean midU = u >= EDGE && u <= 1 - EDGE;
        boolean midV = v >= EDGE && v <= 1 - EDGE;

        if (midU && midV) {
            return holder.create(BetType.STRAIGHT, Set.of(n), 0);
        }

        boolean left = u < EDGE;
        boolean right = u > 1 - EDGE;
        boolean bottom = v < EDGE;
        boolean top = v > 1 - EDGE;
        boolean exL = u < EXTREME;
        boolean exR = u > 1 - EXTREME;
        boolean exB = v < EXTREME;
        boolean exT = v > 1 - EXTREME;

        boolean hasLeft = c > 0;
        boolean hasRight = c < COLUMNS - 1;
        boolean hasBottom = r > 0;
        boolean hasTop = r < ROWS - 1;

        // Боковые грани по центру — сплит, на крайних колонках — стрит
        if (left && midV) {
            return hasLeft ? split(holder, n, numberAt(c - 1, r)) : street(holder, c);
        }
        if (right && midV) {
            return hasRight ? split(holder, n, numberAt(c + 1, r)) : street(holder, c);
        }
        // Вертикальные грани по центру — сплит, у края сетки — просто число
        if (bottom && midU) {
            return hasBottom ? split(holder, n, numberAt(c, r - 1)) : holder.create(BetType.STRAIGHT, Set.of(n), 0);
        }
        if (top && midU) {
            return hasTop ? split(holder, n, numberAt(c, r + 1)) : holder.create(BetType.STRAIGHT, Set.of(n), 0);
        }

        // Углы и торцы линий
        if (left && bottom) {
            if (hasLeft && hasBottom) {
                return (exL || exB) ? sixline(holder, c - 1, c) : corner(holder, c - 1, c, r - 1, r);
            }
            if (hasLeft) {
                return split(holder, n, numberAt(c - 1, r));
            }
            if (hasBottom) {
                return split(holder, n, numberAt(c, r - 1));
            }
        }
        if (left && top) {
            if (hasLeft && hasTop) {
                return (exL || exT) ? sixline(holder, c - 1, c) : corner(holder, c - 1, c, r, r + 1);
            }
            if (hasLeft) {
                return split(holder, n, numberAt(c - 1, r));
            }
            if (hasTop) {
                return split(holder, n, numberAt(c, r + 1));
            }
        }
        if (right && bottom) {
            if (hasRight && hasBottom) {
                return (exR || exB) ? sixline(holder, c, c + 1) : corner(holder, c, c + 1, r - 1, r);
            }
            if (hasRight) {
                return split(holder, n, numberAt(c + 1, r));
            }
            if (hasBottom) {
                return split(holder, n, numberAt(c, r - 1));
            }
        }
        if (right && top) {
            if (hasRight && hasTop) {
                return (exR || exT) ? sixline(holder, c, c + 1) : corner(holder, c, c + 1, r, r + 1);
            }
            if (hasRight) {
                return split(holder, n, numberAt(c + 1, r));
            }
            if (hasTop) {
                return split(holder, n, numberAt(c, r + 1));
            }
        }

        return holder.create(BetType.STRAIGHT, Set.of(n), 0);
    }

    private static Bet split(UUIDHolder holder, int a, int b) {
        return holder.create(BetType.SPLIT, Set.of(a, b), 0);
    }

    private static Bet street(UUIDHolder holder, int c) {
        return holder.create(BetType.STREET, Set.of(numberAt(c, 0), numberAt(c, 1), numberAt(c, 2)), 0);
    }

    private static Bet corner(UUIDHolder holder, int c1, int c2, int r1, int r2) {
        return holder.create(BetType.CORNER,
                Set.of(numberAt(c1, r1), numberAt(c2, r1), numberAt(c1, r2), numberAt(c2, r2)), 0);
    }

    private static Bet sixline(UUIDHolder holder, int c1, int c2) {
        Set<Integer> nums = new LinkedHashSet<>();
        for (int c = c1; c <= c2; c++) {
            nums.add(numberAt(c, 0));
            nums.add(numberAt(c, 1));
            nums.add(numberAt(c, 2));
        }
        return holder.create(BetType.SIXLINE, nums, 0);
    }

    private static Set<Integer> range(int from, int to) {
        Set<Integer> set = new LinkedHashSet<>();
        for (int n = from; n <= to; n++) {
            set.add(n);
        }
        return set;
    }

    private static Set<Integer> evens(boolean even) {
        Set<Integer> set = new LinkedHashSet<>();
        for (int n = 1; n <= 36; n++) {
            if ((n % 2 == 0) == even) {
                set.add(n);
            }
        }
        return set;
    }

    private static Set<Integer> reds() {
        return java.util.stream.IntStream.rangeClosed(1, 36)
                .filter(Frontiks.Craft.casino.util.WheelNumbers::isRed)
                .boxed().collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<Integer> blacks() {
        return java.util.stream.IntStream.rangeClosed(1, 36)
                .filter(Frontiks.Craft.casino.util.WheelNumbers::isBlack)
                .boxed().collect(Collectors.toCollection(TreeSet::new));
    }

    /** Фабрика ставок: реализация привязывает игрока и сумму. */
    public interface UUIDHolder {
        Bet create(BetType type, Set<Integer> numbers, long amount);
    }
}
