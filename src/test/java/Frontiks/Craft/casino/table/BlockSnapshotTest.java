package Frontiks.Craft.casino.table;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Снимок исходных блоков для /roulette undo. */
class BlockSnapshotTest {

    @Test
    void recordsFirstStateOnly() {
        BlockSnapshot snapshot = new BlockSnapshot();
        snapshot.record(1, 64, -2, "minecraft:grass_block");
        // стол ставит блок в ту же точку повторно — исходное состояние не теряется
        snapshot.record(1, 64, -2, "minecraft:white_concrete");

        assertEquals(1, snapshot.size());
        assertEquals("minecraft:grass_block", snapshot.asMap().get("1,64,-2"));
    }

    @Test
    void serializesAndDeserializes() {
        BlockSnapshot snapshot = new BlockSnapshot();
        snapshot.record(0, 63, 0, "minecraft:stone");
        snapshot.record(-3, 62, 7, "minecraft:air");

        List<String> data = snapshot.serialize();
        assertEquals(2, data.size());

        BlockSnapshot restored = BlockSnapshot.deserialize(data);
        assertEquals(snapshot.asMap(), restored.asMap());
    }

    @Test
    void deserializationSkipsBrokenEntries() {
        List<String> data = List.of(
                "1,2,3=minecraft:stone",
                "no-separator",
                "1,2=minecraft:stone",
                "a,b,c=minecraft:stone",
                "=minecraft:stone",
                "5,6,7=minecraft:dirt");

        BlockSnapshot restored = BlockSnapshot.deserialize(data);
        assertEquals(Map.of("1,2,3", "minecraft:stone", "5,6,7", "minecraft:dirt"),
                restored.asMap());
    }

    @Test
    void deserializationOfNullIsEmpty() {
        assertTrue(BlockSnapshot.deserialize(null).isEmpty());
    }
}
