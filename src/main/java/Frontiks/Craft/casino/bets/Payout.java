package Frontiks.Craft.casino.bets;

/** Расчёт выплаты по ставке (инвариант §2.2 ТЗ). */
public final class Payout {

    private Payout() {
    }

    /**
     * @param returnStake возвращать ли ставку вместе с выигрышем
     *                    ({@code return-stake-on-win})
     */
    public static long compute(Bet bet, boolean returnStake) {
        long win = bet.getAmount() * bet.getType().getPayout();
        return returnStake ? win + bet.getAmount() : win;
    }
}
