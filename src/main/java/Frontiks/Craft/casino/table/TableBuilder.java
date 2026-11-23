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
        forEachBlock(table, (x, y, z, material) -> setBlock(table, x, y, z, material));
    }

    /**
     * Убрать все блоки стола (поставить воздух) — для столов, созданных
     * до появления снимка исходных блоков. Используется как fallback в
     * {@link RouletteTable#restoreBlocks()}.
     */
    public static void remove(RouletteTable table) {
        org.bukkit.World world = table.getCenter().getWorld();
        if (world == null) {
            return;
        }
        forEachBlock(table, (x, y, z, material) -> {
            world.getChunkAt(x >> 4, z >> 4).load();
            world.getBlockAt(x, y, z).setType(Material.AIR, false);
        });
    }

    /** Обходит все позиции, которые занимает стол. */
    private static void forEachBlock(RouletteTable table, Placement placement) {
        int baseY = table.getCenter().getBlockY();

        // Пол 23x23
        for (int lx = -9; lx <= 13; lx++) {
            for (int lz = -9; lz <= 13; lz++) {
                place(table, lx, lz, baseY - 1, FLOOR, placement);
            }
        }

        // Сердцевина колеса и обод
        for (int lx = -9; lx <= 9; lx++) {
            for (int lz = -9; lz <= 9; lz++) {
                double r = Math.sqrt(lx * lx + lz * lz);
                if (r <= 1.3) {
                    place(table, lx, lz, baseY, HUB, placement);
                } else if (r >= 7.2 && r <= 8.2) {
                    place(table, lx, lz, baseY, RIM, placement);
                }
            }
        }

        // Зеро (3 блока слева от сетки)
        for (int r = 0; r < BetDetector.ROWS; r++) {
            place(table, -1, (int) BetDetector.GRID_LZ0 + r, baseY, ZERO_CELL, placement);
        }

        // Ячейки чисел
        for (int c = 0; c < BetDetector.COLUMNS; c++) {
            for (int r = 0; r < BetDetector.ROWS; r++) {
                place(table, c, (int) BetDetector.GRID_LZ0 + r, baseY, NUMBER_CELL, placement);
            }
        }

        // Дюжины
        for (int c = 0; c < BetDetector.COLUMNS; c++) {
            int dozen = c / 4;
            place(table, c, (int) BetDetector.GRID_LZ0 + BetDetector.ROWS, baseY,
                    DOZEN_COLORS[dozen], placement);
        }

        // Колонки 2to1
        for (int r = 0; r < BetDetector.ROWS; r++) {
            place(table, BetDetector.COLUMNS, (int) BetDetector.GRID_LZ0 + r, baseY,
                    DOZEN_COLORS[r], placement);
        }

        // Равные шансы: 1-18 | Чёт | Красное | Чёрное | Нечёт | 19-36
        for (int c = 0; c < BetDetector.COLUMNS; c++) {
            int idx = c / 2;
            place(table, c, (int) BetDetector.GRID_LZ0 + BetDetector.ROWS + 1, baseY,
                    EVEN_COLORS[idx], placement);
        }
    }

    /** Пересчитывает локальные координаты в мировые и вызывает placement. */
    private static void place(RouletteTable table, int lx, int lz, int y, Material material,
                              Placement placement) {
        Location loc = table.toWorld(lx + 0.0, lz + 0.0, 0);
        org.bukkit.World world = loc.getWorld();
        if (world == null) {
            return;
        }
        // toWorld считает от центра блока: floor даёт угол блока
        int x = (int) Math.floor(loc.getX());
        int z = (int) Math.floor(loc.getZ());
        placement.accept(x, y, z, material);
    }

    private static void setBlock(RouletteTable table, int x, int y, int z, Material material) {
        org.bukkit.World world = table.getCenter().getWorld();
        if (world == null) {
            return;
        }
        org.bukkit.block.Block block = world.getBlockAt(x, y, z);
        // запоминаем, что было здесь до стола — для /roulette undo
        table.recordOriginalBlock(x, y, z, block.getBlockData().getAsString());
        block.setType(material, false);
    }

    @FunctionalInterface
    private interface Placement {
        void accept(int x, int y, int z, Material material);
    }
}
