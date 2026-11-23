package Frontiks.Craft.casino.bets;

/**
 * Типы ставок европейской рулетки (стандартный набор).
 * payout — коэффициент выигрыша (без возврата ставки).
 */
public enum BetType {
    STRAIGHT(35, "число"),
    SPLIT(17, "сплит (2 числа)"),
    STREET(11, "стрит (3 числа)"),
    CORNER(8, "каре (4 числа)"),
    SIXLINE(5, "шесть номеров"),
    DOZEN(2, "дюжина"),
    COLUMN(2, "колонка"),
    RED(1, "красное"),
    BLACK(1, "чёрное"),
    EVEN(1, "чёт"),
    ODD(1, "нечёт"),
    LOW(1, "1-18"),
    HIGH(1, "19-36");

    private final int payout;
    private final String label;

    BetType(int payout, String label) {
        this.payout = payout;
        this.label = label;
    }

    public int getPayout() {
        return payout;
    }

    public String getLabel() {
        return label;
    }
}
