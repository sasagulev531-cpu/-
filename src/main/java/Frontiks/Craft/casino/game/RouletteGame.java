package Frontiks.Craft.casino.game;

import Frontiks.Craft.casino.CasinoPlugin;
import Frontiks.Craft.casino.bets.Bet;
import Frontiks.Craft.casino.bets.BetBook;
import Frontiks.Craft.casino.bets.BetType;
import Frontiks.Craft.casino.bets.Payout;
import Frontiks.Craft.casino.util.Messages;
import Frontiks.Craft.casino.util.WheelNumbers;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Общий раунд рулетки: все игроки ставят на один спин.
 *
 * <p>Фазы: {@code BETTING} (приём ставок) → {@code SPINNING} (прокрутка) →
 * {@code RESULT} (результат) → снова {@code BETTING}. Таймер общий для всего
 * сервера, ставки лежат в одном {@link BetBook}, выплаты — каждому игроку
 * отдельно (ключ {@code UUID:positionKey}, см. R1).</p>
 */
public class RouletteGame {

    public enum Phase { BETTING, SPINNING, RESULT }

    private final CasinoPlugin plugin;
    private final BetBook bets = new BetBook();

    private Phase phase = Phase.BETTING;
    private int phaseTicks;
    private int winningNumber = -1;
    private int spinTicks = 1;
    private int highlightIndex = -1;

    private BukkitRunnable task;

    public RouletteGame(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        startBettingPhase();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 20L, 1L);
    }

    public void stop() {
        if (phase != Phase.RESULT) {
            for (Bet bet : bets.all()) {
                plugin.getBalanceProvider().deposit(bet.getPlayer(), bet.getAmount());
            }
        }
        bets.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ---------------------------------------------------------------
    //  Таймер раунда
    // ---------------------------------------------------------------

    private void tick() {
        switch (phase) {
            case BETTING -> {
                phaseTicks--;
                if (phaseTicks % 20 == 0) {
                    plugin.getGui().refreshAll();
                }
                if (phaseTicks <= 0) {
                    beginSpin();
                }
            }
            case SPINNING -> {
                phaseTicks++;
                updateHighlight();
                plugin.getGui().refreshAll();
                if (phaseTicks >= spinTicks) {
                    finishSpin();
                }
            }
            case RESULT -> {
                phaseTicks--;
                if (phaseTicks % 20 == 0) {
                    plugin.getGui().refreshAll();
                }
                if (phaseTicks <= 0) {
                    startBettingPhase();
                }
            }
        }
    }

    private void startBettingPhase() {
        phase = Phase.BETTING;
        phaseTicks = Math.max(1, plugin.getConfig().getInt("betting-seconds", 20)) * 20;
        winningNumber = -1;
        highlightIndex = -1;
        bets.clear();
        plugin.getGui().refreshAll();
    }

    /** Запуск общего вращения по таймеру. */
    private void beginSpin() {
        if (phase != Phase.BETTING) {
            return;
        }
        winningNumber = ThreadLocalRandom.current().nextInt(37);
        spinTicks = Math.max(20, plugin.getConfig().getInt("spin-seconds", 12) * 20);
        phase = Phase.SPINNING;
        phaseTicks = 0;
        highlightIndex = 0;
        announce(Messages.format("spin-start"));
        plugin.getGui().refreshAll();
    }

    private void finishSpin() {
        String colorKey = winningNumber == 0 ? "color-zero"
                : WheelNumbers.isRed(winningNumber) ? "color-red" : "color-black";
        announce(Messages.format("result", "number", winningNumber, "color",
                Messages.raw(colorKey)));

        boolean returnStake = plugin.getConfig().getBoolean("return-stake-on-win", true);
        for (Bet bet : bets.all()) {
            Player online = Bukkit.getPlayer(bet.getPlayer());
            if (bet.getNumbers().contains(winningNumber)) {
                long win = Payout.compute(bet, returnStake);
                plugin.getBalanceProvider().deposit(bet.getPlayer(), win);
                announce(Messages.format("win-broadcast",
                        "player", bet.getPlayerName(), "amount", win, "bet", bet.describe()));
                if (online != null) {
                    Messages.send(online, Messages.legacy(Messages.rawPrefix())
                            .append(Messages.format("you-win", "amount", win, "bet", bet.describe())));
                    online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }
            } else if (online != null) {
                Messages.send(online, Messages.legacy(Messages.rawPrefix())
                        .append(Messages.format("you-lose", "amount", bet.getAmount(), "bet", bet.describe())));
            }
        }

        bets.clear(); // рассчитанные ставки больше не являются обязательством при выключении сервера
        phase = Phase.RESULT;
        phaseTicks = Math.max(1, plugin.getConfig().getInt("result-seconds", 5)) * 20;
        plugin.getGui().refreshAll();
    }

    /** Подсветка «шарика» в GUI: движется по порядку колеса и останавливается на выигрыше. */
    private void updateHighlight() {
        double p = Math.min(1.0, (double) phaseTicks / Math.max(1, spinTicks));
        double eased = 1 - Math.pow(1 - p, 4); // easeOutQuart
        int turns = (int) Math.max(1, plugin.getConfig().getDouble("ball-turns", 5.0));
        int target = WheelNumbers.indexOf(Math.max(0, winningNumber));
        int steps = WheelNumbers.EUROPEAN_ORDER.length * turns + target;
        highlightIndex = (int) Math.round(eased * steps) % WheelNumbers.EUROPEAN_ORDER.length;
    }

    /** Число, которое подсвечено в GUI сейчас (шарик или результат). */
    public int getHighlightNumber() {
        if (phase == Phase.RESULT) {
            return winningNumber;
        }
        if (phase == Phase.SPINNING && highlightIndex >= 0) {
            return WheelNumbers.EUROPEAN_ORDER[highlightIndex];
        }
        return -1;
    }

    // ---------------------------------------------------------------
    //  Действия игрока
    // ---------------------------------------------------------------

    /**
     * Принять ставку от игрока. Проверяет фазу, право, лимиты, списывает деньги.
     *
     * @return статус лимитов или {@code null}, если ставка не принята
     */
    public BetBook.Status placeBet(Player player, BetType type, Set<Integer> numbers, long amount) {
        if (phase != Phase.BETTING) {
            Messages.send(player, Messages.legacy(Messages.rawPrefix())
                    .append(Messages.get("betting-closed")));
            return null;
        }
        if (!player.hasPermission("roulette.play")) {
            Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(Messages.get("no-permission")));
            return null;
        }
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(Messages.get("invalid-amount")));
            return null;
        }
        long minBet = plugin.getConfig().getLong("min-bet", 10);
        long maxBet = plugin.getConfig().getLong("max-bet", 100000);
        long maxTotal = plugin.getConfig().getLong("max-total-per-spin", 500000);

        Bet bet = new Bet(player.getUniqueId(), player.getName(), type, numbers, amount);
        BetBook.Status status = bets.validate(bet, minBet, maxBet, maxTotal);
        switch (status) {
            case BELOW_MIN -> {
                Messages.send(player, Messages.legacy(Messages.rawPrefix())
                        .append(Messages.format("below-min", "min", minBet)));
                return status;
            }
            case ABOVE_MAX -> {
                Messages.send(player, Messages.legacy(Messages.rawPrefix())
                        .append(Messages.format("above-max", "max", maxBet)));
                return status;
            }
            case ABOVE_TOTAL -> {
                Messages.send(player, Messages.legacy(Messages.rawPrefix())
                        .append(Messages.format("above-max-total", "limit", maxTotal)));
                return status;
            }
            case OK -> {
            }
        }

        if (!plugin.getBalanceProvider().withdraw(player.getUniqueId(), amount)) {
            Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(Messages.format("not-enough",
                    "amount", amount, "balance", plugin.getBalanceProvider().getBalance(player.getUniqueId()))));
            return null;
        }

        boolean merged = bets.put(bet);
        Bet current = merged ? bets.get(player.getUniqueId(), type, numbers) : bet;
        Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(merged
                ? Messages.format("bet-merged", "amount", amount, "bet", current.describe(), "total", current.getAmount())
                : Messages.format("bet-placed", "amount", amount, "bet", current.describe())));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        plugin.getGui().refreshAll();
        return BetBook.Status.OK;
    }

    /** Сбросить свои ставки (деньги возвращаются). Только во время приёма ставок. */
    public void clearBets(Player player) {
        if (phase != Phase.BETTING) {
            Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(Messages.get("betting-closed")));
            return;
        }
        long refunded = bets.removePlayer(player.getUniqueId());
        if (refunded <= 0) {
            Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(Messages.get("gui-no-bets")));
            return;
        }
        plugin.getBalanceProvider().deposit(player.getUniqueId(), refunded);
        Messages.send(player, Messages.legacy(Messages.rawPrefix())
                .append(Messages.format("gui-bets-cleared", "amount", refunded)));
        plugin.getGui().refreshAll();
    }

    // ---------------------------------------------------------------
    //  Геттеры для GUI
    // ---------------------------------------------------------------

    public Phase getPhase() {
        return phase;
    }

    /** Секунд до конца текущей фазы (для GUI). */
    public int getSecondsLeft() {
        return Math.max(0, (phaseTicks + 19) / 20);
    }

    public int getWinningNumber() {
        return winningNumber;
    }

    public BetBook getBets() {
        return bets;
    }

    public long getBank() {
        return bets.total();
    }

    public long getPlayerBets(java.util.UUID uuid) {
        return bets.playerTotal(uuid);
    }

    public Bet getPlayerBet(UUID uuid, BetType type, Set<Integer> numbers) {
        return bets.get(uuid, type, numbers);
    }

    // ---------------------------------------------------------------
    //  Рассылка
    // ---------------------------------------------------------------

    /** Сообщение всем, у кого открыто меню, плюс всем участникам раунда. */
    private void announce(net.kyori.adventure.text.Component message) {
        plugin.getGui().broadcast(Messages.legacy(Messages.rawPrefix()).append(message));
        for (Bet bet : bets.all()) {
            Player online = Bukkit.getPlayer(bet.getPlayer());
            if (online != null && !plugin.getGui().isViewing(online)) {
                Messages.send(online, Messages.legacy(Messages.rawPrefix()).append(message));
            }
        }
    }
}
