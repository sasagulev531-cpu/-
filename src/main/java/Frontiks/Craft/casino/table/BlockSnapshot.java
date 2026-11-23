package Frontiks.Craft.casino.table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Снимок блоков, которые были на месте стола ДО постройки.
 *
 * <p>Ключ — {@code "x,y,z"}, значение — {@code BlockData.getAsString()}.
 * Используется командой {@code /roulette undo}: вместо того чтобы стирать
 * область воздухом, стол восстанавливает то, что было на этом месте раньше.</p>
 *
 * <p>Первое записанное состояние для координаты выигрывает
 * ({@code putIfAbsent}): если стол несколько раз ставит блок в одну точку,
 * в снимке останется именно исходный блок, а не промежуточный.</p>
 */
public final class BlockSnapshot {

    private final Map<String, String> blocks = new LinkedHashMap<>();

    /** Запомнить исходное состояние блока (только первый раз для координаты). */
    public void record(int x, int y, int z, String blockData) {
        blocks.putIfAbsent(key(x, y, z), blockData);
    }

    /** @return неизменяемый вид снимка: {@code "x,y,z" -> BlockData} */
    public Map<String, String> asMap() {
        return Map.copyOf(blocks);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public int size() {
        return blocks.size();
    }

    /** Сериализация в список строк {@code "x,y,z=blockData"} для tables.yml. */
    public List<String> serialize() {
        List<String> entries = new ArrayList<>(blocks.size());
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            entries.add(entry.getKey() + "=" + entry.getValue());
        }
        return entries;
    }

    /** Десериализация из {@link #serialize()}; битые записи пропускаются. */
    public static BlockSnapshot deserialize(List<String> entries) {
        BlockSnapshot snapshot = new BlockSnapshot();
        if (entries == null) {
            return snapshot;
        }
        for (String entry : entries) {
            int sep = entry.indexOf('=');
            if (sep <= 0) {
                continue;
            }
            String key = entry.substring(0, sep);
            String[] coords = key.split(",");
            if (coords.length != 3) {
                continue;
            }
            try {
                Integer.parseInt(coords[0]);
                Integer.parseInt(coords[1]);
                Integer.parseInt(coords[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            snapshot.blocks.put(key, entry.substring(sep + 1));
        }
        return snapshot;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
