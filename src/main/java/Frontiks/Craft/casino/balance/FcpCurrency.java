package Frontiks.Craft.casino.balance;

/**
 * Валюта FCP, которую списывает/зачисляет казино.
 *
 * <p>{@code COINS} — основная валюта FCP ({@code %fcp_coins%});
 * {@code DCOINS} — донат-монеты ({@code %fcp_donate_coins%}).</p>
 */
public enum FcpCurrency {
    COINS,
    DCOINS;

    /** Читает значение из config.yml ({@code coins}/{@code dcoins}); по умолчанию COINS. */
    public static FcpCurrency fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return COINS;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return COINS;
        }
    }
}
