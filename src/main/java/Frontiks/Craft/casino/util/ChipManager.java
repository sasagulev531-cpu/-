package Frontiks.Craft.casino.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Номиналы фишек, их цвета на столе и выбранный игроком номинал. */
public class ChipManager {

    private final JavaPlugin plugin;
    private List<Long> denominations = new ArrayList<>();
    private Map<Long, Material> colors = new HashMap<>();
    private Material chipMaterial = Material.SUNFLOWER;
    private String chipName = "";
    private final Map<UUID, Integer> selectedIndex = new HashMap<>();

    public ChipManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        denominations = new ArrayList<>();
        for (long d : plugin.getConfig().getLongList("chips.denominations")) {
            denominations.add(d);
        }
        if (denominations.isEmpty()) {
            denominations.add(100L);
        }
        colors = new LinkedHashMap<>();
        var section = plugin.getConfig().getConfigurationSection("chips.colors");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material mat = Material.matchMaterial(section.getString(key, "WHITE_CARPET"));
                if (mat == null) {
                    continue;
                }
                try {
                    colors.put(Long.parseLong(key), mat);
                } catch (NumberFormatException e) {
                    // R7: опечатка в chips.colors не должна ронять reload
                    plugin.getLogger().warning("chips.colors: ключ '" + key + "' не число — пропущен");
                }
            }
        }
        chipMaterial = Material.matchMaterial(plugin.getConfig().getString("chip-material", "SUNFLOWER"));
        if (chipMaterial == null) {
            chipMaterial = Material.SUNFLOWER;
        }
        chipName = plugin.getConfig().getString("chip-name", "");
    }

    public List<Long> getDenominations() {
        return denominations;
    }

    public long getSelectedAmount(Player player) {
        int idx = selectedIndex.getOrDefault(player.getUniqueId(), 0);
        if (idx < 0 || idx >= denominations.size()) {
            idx = 0;
        }
        return denominations.get(idx);
    }

    /** Переключить номинал на следующий. Возвращает новый номинал. */
    public long cycle(Player player) {
        int idx = (selectedIndex.getOrDefault(player.getUniqueId(), 0) + 1) % denominations.size();
        selectedIndex.put(player.getUniqueId(), idx);
        return denominations.get(idx);
    }

    /** Попытаться выбрать конкретный номинал (команда /roulette chip). */
    public boolean select(Player player, long amount) {
        int idx = denominations.indexOf(amount);
        if (idx < 0) {
            return false;
        }
        selectedIndex.put(player.getUniqueId(), idx);
        return true;
    }

    public Material getChipMaterial(long amount) {
        return colors.getOrDefault(amount, Material.WHITE_CARPET);
    }

    /** Является ли предмет в руке игровой фишкой. */
    public boolean isChipItem(ItemStack item) {
        if (item == null || item.getType() != chipMaterial) {
            return false;
        }
        if (chipName == null || chipName.isEmpty()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        // Сравниваем имя предмета (сериализуем в §-коды) с ожидаемым
        String actual = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(meta.displayName());
        String wanted = chipName.replace('&', '\u00a7');
        return actual.equalsIgnoreCase(wanted);
    }
}
