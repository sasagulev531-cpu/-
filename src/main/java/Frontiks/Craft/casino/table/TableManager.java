package Frontiks.Craft.casino.table;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import Frontiks.Craft.casino.table.RouletteTable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Реестр столов: создание, загрузка/сохранение, поиск. */
public class TableManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, RouletteTable> tables = new LinkedHashMap<>();

    public TableManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tables.yml");
    }

    /** Создать стол там, где стоит игрок, по направлению его взгляда. */
    public RouletteTable createTable(Player player) {
        String id = nextId();
        Location center = player.getLocation().getBlock().getLocation();
        RouletteTable table = new RouletteTable((Frontiks.Craft.casino.CasinoPlugin) plugin, id, center, player.getLocation().getYaw());
        TableBuilder.build(table);
        table.start();
        tables.put(id, table);
        save();
        return table;
    }

    private String nextId() {
        int i = 1;
        while (tables.containsKey("table-" + i)) {
            i++;
        }
        return "table-" + i;
    }

    public RouletteTable get(String id) {
        return tables.get(id);
    }

    public Collection<RouletteTable> all() {
        return tables.values();
    }

    /** Удалить стол: сущности и данные. Блоки в мире остаются (удалите их вручную). */
    public boolean removeTable(String id) {
        RouletteTable table = tables.remove(id);
        if (table == null) {
            return false;
        }
        table.stop();
        save();
        return true;
    }

    /** Стол, в зону которого попадает локация, или null. */
    public RouletteTable findTableAt(Location location) {
        for (RouletteTable table : tables.values()) {
            if (table.contains(location)) {
                return table;
            }
        }
        return null;
    }

    /** Ближайший стол в пределах maxDistance. */
    public RouletteTable nearestTable(Location location, double maxDistance) {
        RouletteTable best = null;
        double bestDist = maxDistance * maxDistance;
        for (RouletteTable table : tables.values()) {
            double dist = table.getCenter().distanceSquared(location);
            if (dist < bestDist) {
                bestDist = dist;
                best = table;
            }
        }
        return best;
    }

    public void load() {
        tables.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("tables");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            World world = Bukkit.getWorld(section.getString(id + ".world", ""));
            if (world == null) {
                plugin.getLogger().warning("Мир стола " + id + " не найден, пропускаем.");
                continue;
            }
            double x = section.getDouble(id + ".x");
            double y = section.getDouble(id + ".y");
            double z = section.getDouble(id + ".z");
            float yaw = (float) section.getDouble(id + ".yaw");
            RouletteTable table = new RouletteTable((Frontiks.Craft.casino.CasinoPlugin) plugin, id,
                    new Location(world, x, y, z), yaw);
            table.start();
            tables.put(id, table);
        }
        plugin.getLogger().info("Загружено столов рулетки: " + tables.size());
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (RouletteTable table : tables.values()) {
            Location c = table.getCenter();
            String path = "tables." + table.getId();
            yaml.set(path + ".world", c.getWorld().getName());
            yaml.set(path + ".x", c.getX());
            yaml.set(path + ".y", c.getY());
            yaml.set(path + ".z", c.getZ());
            yaml.set(path + ".yaw", (double) table.getYaw());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить tables.yml: " + e.getMessage());
        }
    }

    /** Выключение: остановить столы и сохранить. */
    public void shutdown() {
        for (RouletteTable table : tables.values()) {
            table.stop();
        }
        save();
    }
}
