package Frontiks.Craft.casino.balance;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Тестовая реализация экономики: балансы в YAML-файле.
 * Нужна, чтобы казино работало «из коробки», пока не подключена настоящая валюта.
 */
public class FileBalanceProvider implements BalanceProvider {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public FileBalanceProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public long getBalance(UUID player) {
        return yaml.getLong(player.toString(), 0);
    }

    @Override
    public void deposit(UUID player, long amount) {
        if (amount <= 0) {
            return;
        }
        yaml.set(player.toString(), getBalance(player) + amount);
        save();
    }

    @Override
    public boolean withdraw(UUID player, long amount) {
        if (amount <= 0) {
            return true;
        }
        long balance = getBalance(player);
        if (balance < amount) {
            return false;
        }
        yaml.set(player.toString(), balance - amount);
        save();
        return true;
    }

    @Override
    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить balances.yml: " + e.getMessage());
        }
    }
}
