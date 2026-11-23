package Frontiks.Craft.casino.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/** Доступ к сообщениям из config.yml с поддержкой '&'-кодов и плейсхолдеров {key}. */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static JavaPlugin plugin;

    private Messages() {
    }

    public static void init(JavaPlugin pl) {
        plugin = pl;
    }

    public static String raw(String key) {
        String s = plugin.getConfig().getString("messages." + key);
        return s != null ? s : "&c[нет сообщения: " + key + "]";
    }

    public static String rawPrefix() {
        return raw("prefix");
    }

    public static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }

    public static Component get(String key) {
        return legacy(raw(key));
    }

    /** Форматирование: format("bet-placed", "amount", 100, "bet", "красное") */
    public static Component format(String key, Object... placeholders) {
        String s = raw(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            s = s.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return legacy(s);
    }

    public static Map<String, String> mapOf(Object... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
        return map;
    }

    /** Отправка компонента через строковый API (избегает зависимости от bungee-chat). */
    public static void send(CommandSender sender, Component component) {
        sender.sendMessage(SECTION.serialize(component));
    }
}
