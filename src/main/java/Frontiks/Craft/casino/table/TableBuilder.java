package Frontiks.Craft.casino.table;

import org.bukkit.Location;
import org.bukkit.Material;
import Frontiks.Craft.casino.bets.BetDetector;

/**
 * Постройка физического стола блоками в мире.
 * Локальные координаты совпадают с BetDetector:
 * колесо в начале координат, сетка чисел lz [8..11), зеро слева,
 * дюжины lz 11, колонки lx 12, равные шансы lz 12.
 */
public final class TableBuilder {

    private static final Material FLOOR = Material.SMOOTH_STONE;
    private static final Material NUMBER_CELL = Material.WHITE_CONCRETE;
    private static final Material ZERO_CELL = Material.GREEN_CONCRETE;
    private static final Material HUB = Material.GREEN_CONCRETE;
    private static final Material RIM = Material.GRAY_CONCRETE;

    private static final Material[] DOZEN_COLORS = {
            Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.PINK_CONCRETE};
    private static final Material[] EVEN_COLORS = {
            Material.CYAN_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.RED_CONCRETE,
            Material.BLACK_CONCRETE, Material.GRAY_CONCRETE, Material.BLUE_CONCRETE};

    private TableBuilder() {
    }

    /** Построить стол (идемпотентно — можно вызывать повторно). */
    public static void build(RouletteTable table) {
        int baseY = table.getCenter().getBlockY();

        // Пол 23x23
        for (int lx = -9; lx <= 13; lx++) {
            for (int lz = -9; lz <= 13; lz++) {
                setBlock(table, lx, lz, baseY - 1, FLOOR);
            }
        }

        // Сердцевина колеса и обод
        for (int lx = -9; lx <= 9; lx++) {
            for (int lz = -9; lz <= 9; lz++) {
                double r = Math.sqrt(lx * lx + lz * lz);
                if (r <= 1.3) {
                    setBlock(table, lx, lz, baseY, HUB);
                } else if (r >= 7.2 && r <= 8.2) {
                    setBlock(table, lx, lz, baseY, RIM);
                }
            }
        }

        // Зеро (3 блока слева от сетки)
        for (int r = 0; r < BetDetector.ROWS; r++) {
            setBlock(table, -1, (int) BetDetector.GRID_LZ0 + r, baseY, ZERO_CELL);
        }

        // Ячейки чисел
        for (int c = 0; c < BetDetector.COLUMNS; c++) {
            for (int r = 0; r < BetDetector.ROWS; r++) {
                setBlock(table, c, (int) BetDetector.GRID_LZ0 + r, baseY, NUMBER_CELL);
            }
        }

        // Дюжины
        for (int c = 0; c < BetDetector.COLUMNS; c++) {
            int dozen = c / 4;
            setBlock(table, c, (int) BetDetector.GRID_LZ0 + BetDetector.ROWS, baseY, DOZEN_COLORS[dozen]);
        }

        // Колонки 2to1
        for (int r = 0; r < BetDetector.ROWS; r++) {
            setBlock(table, BetDetector.COLUMNS, (int) BetDetector.GRID_LZ0 + r, baseY, DOZEN_COLORS[r]);
        }

        // Равные шансы: 1-18 | Чёт | Красное | Чёрное | Нечёт | 19-36
        for (int c = 0; c < BetDetector.COLUMNS; c++) {
            int idx = c / 2;
            setBlock(table, c, (int) BetDetector.GRID_LZ0 + BetDetector.ROWS + 1, baseY, EVEN_COLORS[idx]);
        }
    }

    private static void setBlock(RouletteTable table, int lx, int lz, int y, Material material) {
        Location loc = table.toWorld(lx + 0.0, lz + 0.0, 0);
        // toWorld считает от центра блока: вычтем 0.5, чтобы получить углы блоков
        org.bukkit.World world = loc.getWorld();
        if (world == null) {
            return;
        }
        int x = (int) Math.floor(loc.getX());
        int z = (int) Math.floor(loc.getZ());
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
