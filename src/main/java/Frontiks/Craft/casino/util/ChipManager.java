package Frontiks.Craft.casino.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Номиналы фишек и выбранный игроком номинал для GUI. */
public class ChipManager {
    private final JavaPlugin plugin;
    private List<Long> denominations = new ArrayList<>();
    private Map<Long, Material> colors = new HashMap<>();
    private final Map<UUID, Integer> selectedIndex = new HashMap<>();

    public ChipManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        denominations = new ArrayList<>();
        for (long d : plugin.getConfig().getLongList("chips.denominations")) {
            if (d > 0 && d <= Integer.MAX_VALUE && !denominations.contains(d)) {
                denominations.add(d);
            }
        }
        if (denominations.isEmpty()) {
            denominations.add(100L);
        }
        colors = new LinkedHashMap<>();
        var section = plugin.getConfig().getConfigurationSection("chips.colors");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material mat = Material.matchMaterial(section.getString(key, "WHITE_CARPET"));
                if (mat == null || !mat.isItem()) {
                    continue;
                }
                try {
                    colors.put(Long.parseLong(key), mat);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("chips.colors: ключ '" + key + "' не число — пропущен");
                }
            }
        }
    }

    public List<Long> getDenominations() {
        return List.copyOf(denominations);
    }

    public long getSelectedAmount(Player player) {
        int idx = selectedIndex.getOrDefault(player.getUniqueId(), 0);
        return denominations.get(Math.max(0, Math.min(idx, denominations.size() - 1)));
    }

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
}
