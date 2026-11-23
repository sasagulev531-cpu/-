package Frontiks.Craft.casino.table;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import Frontiks.Craft.casino.CasinoPlugin;
import Frontiks.Craft.casino.bets.Bet;
import Frontiks.Craft.casino.bets.BetBook;
import Frontiks.Craft.casino.bets.BetDetector;
import Frontiks.Craft.casino.bets.Payout;
import Frontiks.Craft.casino.util.Messages;
import Frontiks.Craft.casino.util.WheelNumbers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Один физический стол европейской рулетки.
 * Состоит из построенных блоков (TableBuilder) и сущностей:
 * 37 лунок (ArmorStand с блоком на голове), шарик, голограммы, фишки игроков.
 */
public class RouletteTable {

    public enum Phase {BETTING, SPINNING, RESULT}

    private static final double TRACK_R = 6.9;   // дорожка шарика
    private static final double POCKET_R = 5.1;  // круг лунок
    private static final double POCKET_Y = 1.0;  // высота лунок над центром
    private static final double BALL_Y = 1.55;   // высота шарика
    private static final double STEP = Math.PI * 2 / 37;
    private static final double FULL = Math.PI * 2;

    private final CasinoPlugin plugin;
    private final String id;
    private final Location center; // точный центр блока колеса (x.5/z.5)
    private final float yaw;
    // локальный базис: forward — направление взгляда при создании, right — вправо
    private final double fx, fz, rx, rz;

    private Phase phase = Phase.BETTING;
    private int phaseTicks;
    private final BetBook bets = new BetBook();

    /**
     * Снимок блоков, которые были на месте стола ДО постройки.
     * Нужен для {@code /roulette undo}, чтобы откатить постройку,
     * а не просто стереть всё воздухом.
     */
    private final BlockSnapshot originalBlocks = new BlockSnapshot();

    private final List<ArmorStand> pockets = new ArrayList<>();
    private final List<ArmorStand> chipStands = new ArrayList<>();
    private final List<Entity> displays = new ArrayList<>();
    private ArmorStand ball;
    private TextDisplay titleDisplay;
    private TextDisplay statusDisplay;

    private BukkitRunnable task;

    // состояние прокрутки
    private double wheelAngle = 0;
    private double wheelStartAngle = 0;
    private int winningNumber = -1;
    private double targetBallAngle = 0;
    private int spinTicks = 1;

    public RouletteTable(CasinoPlugin plugin, String id, Location blockCenter, float yaw) {
        this.plugin = plugin;
        this.id = id;
        this.center = new Location(blockCenter.getWorld(),
                blockCenter.getBlockX() + 0.5, blockCenter.getBlockY(), blockCenter.getBlockZ() + 0.5);
        this.yaw = yaw;
        double rad = Math.toRadians(yaw);
        this.fx = -Math.sin(rad);
        this.fz = Math.cos(rad);
        this.rx = -fz;
        this.rz = fx;
    }

    // ---------------------------------------------------------------
    //  Жизненный цикл
    // ---------------------------------------------------------------

    /** Запуск стола: спавн сущностей и главный таймер. */
    public void start() {
        spawnEntities();
        startBettingPhase();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 20L, 1L);
    }

    /** Остановка: удаление сущностей (блоки в мире остаются). */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (ArmorStand stand : new ArrayList<>(pockets)) {
            stand.remove();
        }
        for (ArmorStand stand : new ArrayList<>(chipStands)) {
            stand.remove();
        }
        for (Entity entity : new ArrayList<>(displays)) {
            entity.remove();
        }
        if (ball != null) {
            ball.remove();
        }
        pockets.clear();
        chipStands.clear();
        displays.clear();
        ball = null;
    }

    private void tick() {
        // R3: чанк центра выгружен — сущности неперсистентны и удалены,
        // таймеры фаз стоят, чтобы ставки не уходили «в пустоту»
        if (center.getWorld() == null) {
            return;
        }
        if (!center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
            return;
        }
        ensureEntitiesAlive();

        switch (phase) {
            case BETTING -> {
                phaseTicks--;
                if (phaseTicks % 20 == 0) {
                    updateStatusHologram();
                }
                if (phaseTicks <= 0) {
                    beginSpin();
                }
            }
            case SPINNING -> {
                phaseTicks++;
                animateSpin();
                if (phaseTicks >= spinTicks) {
                    finishSpin();
                }
            }
            case RESULT -> {
                phaseTicks--;
                if (phaseTicks <= 0) {
                    startBettingPhase();
                }
            }
        }
    }

    // ---------------------------------------------------------------
    //  Фазы
    // ---------------------------------------------------------------

    private void startBettingPhase() {
        phase = Phase.BETTING;
        phaseTicks = plugin.getConfig().getInt("betting-seconds", 20) * 20;
        bets.clear();
        clearChipStands();
        wheelAngle = normalize(wheelAngle);
        updateStatusHologram();
    }

    /** Начать прокрутку (вызывается таймером или командой /roulette spin). */
    public void beginSpin() {
        if (phase != Phase.BETTING) {
            return;
        }
        if (bets.isEmpty()) {
            broadcast(Messages.format("no-bets"));
            phase = Phase.BETTING;
            phaseTicks = plugin.getConfig().getInt("betting-seconds", 20) * 20;
            updateStatusHologram();
            return;
        }
        winningNumber = ThreadLocalRandom.current().nextInt(37);
        wheelStartAngle = wheelAngle;
        double finalWheelAngle = wheelStartAngle + FULL * plugin.getConfig().getDouble("wheel-turns", 2.5);
        targetBallAngle = finalWheelAngle + WheelNumbers.indexOf(winningNumber) * STEP;
        spinTicks = Math.max(20, plugin.getConfig().getInt("spin-seconds", 12) * 20);
        phase = Phase.SPINNING;
        phaseTicks = 0;
        broadcast(Messages.format("spin-start"));
        updateStatusHologram();
    }

    private void animateSpin() {
        double t = phaseTicks;
        double total = spinTicks;
        double p = easeOutQuart(Math.min(1.0, t / total));
        wheelAngle = wheelStartAngle + FULL * plugin.getConfig().getDouble("wheel-turns", 2.5) * p;
        double ballAngle = targetBallAngle + FULL * plugin.getConfig().getDouble("ball-turns", 5.0) * (1 - p);

        double dropStart = total * 0.68;
        double dropDur = total * 0.27;
        double radius;
        if (t < dropStart) {
            radius = TRACK_R;
        } else {
            double dp = Math.min(1.0, (t - dropStart) / dropDur);
            radius = TRACK_R + (POCKET_R - TRACK_R) * easeInOutCubic(dp);
        }
        // лёгкая «подпрыгивающая» дрожь на затухании
        radius += 0.12 * Math.sin(t * 0.9) * (1 - p);

        teleportWheel(wheelAngle);
        teleportBall(ballAngle, radius);
    }

    private void finishSpin() {
        wheelAngle = normalize(wheelStartAngle + FULL * plugin.getConfig().getDouble("wheel-turns", 2.5));
        teleportWheel(wheelAngle);
        teleportBall(targetBallAngle, POCKET_R);

        String colorKey = winningNumber == 0 ? "color-zero"
                : WheelNumbers.isRed(winningNumber) ? "color-red" : "color-black";
        broadcast(Messages.format("result", "number", winningNumber, "color",
                Messages.raw(colorKey)));

        boolean returnStake = plugin.getConfig().getBoolean("return-stake-on-win", true);
        for (Bet bet : bets.all()) {
            if (bet.getNumbers().contains(winningNumber)) {
                long win = Payout.compute(bet, returnStake);
                plugin.getBalanceProvider().deposit(bet.getPlayer(), win);
                broadcast(Messages.format("win-broadcast",
                        "player", bet.getPlayerName(), "amount", win, "bet", bet.describe()));
                Player online = Bukkit.getPlayer(bet.getPlayer());
                if (online != null) {
                    Messages.send(online, Messages.legacy(Messages.rawPrefix())
                            .append(Messages.format("you-win", "amount", win, "bet", bet.describe())));
                }
            } else {
                Player online = Bukkit.getPlayer(bet.getPlayer());
                if (online != null) {
                    Messages.send(online, Messages.legacy(Messages.rawPrefix())
                            .append(Messages.format("you-lose", "amount", bet.getAmount(), "bet", bet.describe())));
                }
            }
        }

        center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        phase = Phase.RESULT;
        phaseTicks = plugin.getConfig().getInt("result-seconds", 5) * 20;
        updateStatusHologram();
    }

    // ---------------------------------------------------------------
    //  Ставки
    // ---------------------------------------------------------------

    /**
     * Попытка принять ставку от игрока.
     *
     * @param clicked кликнутый блок стола
     * @param point   точное место клика (getInteractionPoint)
     */
    public void tryPlaceBet(Player player, Block clicked, Location point) {
        if (phase != Phase.BETTING) {
            Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(Messages.get("betting-closed")));
            return;
        }
        if (!player.hasPermission("roulette.play")) {
            Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(Messages.get("no-permission")));
            return;
        }

        // локальные координаты блока и дробная позиция клика внутри блока
        double bx = clicked.getX() + 0.5;
        double bz = clicked.getZ() + 0.5;
        double ddx = bx - center.getX();
        double ddz = bz - center.getZ();
        double lx = ddx * rx + ddz * rz;
        double lz = ddx * fx + ddz * fz;
        double ox = point.getX() - clicked.getX();
        double oz = point.getZ() - clicked.getZ();
        double u = clamp01(ox * rx + oz * rz);
        double v = clamp01(ox * fx + oz * fz);

        long chip = plugin.getChipManager().getSelectedAmount(player);
        Bet bet = BetDetector.guess(
                (type, numbers, amount) -> new Bet(player.getUniqueId(), player.getName(), type, numbers, chip),
                lx, lz, u, v);
        if (bet == null) {
            return;
        }

        long minBet = plugin.getConfig().getLong("min-bet", 10);
        long maxBet = plugin.getConfig().getLong("max-bet", 100000);
        long maxTotal = plugin.getConfig().getLong("max-total-per-spin", 500000);

        // R1: лимиты считаются по изолированному реестру (UUID:positionKey)
        BetBook.Status status = bets.validate(bet, minBet, maxBet, maxTotal);
        switch (status) {
            case BELOW_MIN -> {
                Messages.send(player, Messages.legacy(Messages.rawPrefix())
                        .append(Messages.format("below-min", "min", minBet)));
                return;
            }
            case ABOVE_MAX -> {
                Messages.send(player, Messages.legacy(Messages.rawPrefix())
                        .append(Messages.format("above-max", "max", maxBet)));
                return;
            }
            case ABOVE_TOTAL -> {
                Messages.send(player, Messages.legacy(Messages.rawPrefix())
                        .append(Messages.format("above-max-total", "limit", maxTotal)));
                return;
            }
            case OK -> {
            }
        }

        if (!plugin.getBalanceProvider().withdraw(player.getUniqueId(), chip)) {
            Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(Messages.format("not-enough",
                    "amount", chip, "balance", plugin.getBalanceProvider().getBalance(player.getUniqueId()))));
            return;
        }

        // R1: склейка только внутри одного игрока на одной позиции
        boolean merged = bets.put(bet);
        if (merged) {
            bet = bets.get(player.getUniqueId(), bet);
        }
        spawnChipStand(clicked, point, chip);

        Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(merged
                ? Messages.format("bet-merged", "amount", chip, "bet", bet.describe(), "total", bet.getAmount())
                : Messages.format("bet-placed", "amount", chip, "bet", bet.describe())));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        sendChipActionBar(player);
    }

    public void sendChipActionBar(Player player) {
        long chip = plugin.getChipManager().getSelectedAmount(player);
        long balance = plugin.getBalanceProvider().getBalance(player.getUniqueId());
        net.kyori.adventure.audience.Audience audience = player;
        audience.sendActionBar(Messages.legacy("&6Фишка: &f" + chip + " &8| &6Баланс: &f" + balance
                + " &8| &7Шифт+ПКМ — сменить номинал"));
    }

    private void spawnChipStand(Block clicked, Location point, long chip) {
        // высота стопки = число фишек рядом с точкой клика
        int stack = 0;
        for (ArmorStand stand : chipStands) {
            Location l = stand.getLocation();
            double dx = l.getX() - point.getX();
            double dz = l.getZ() - point.getZ();
            if (dx * dx + dz * dz < 0.25) {
                stack++;
            }
        }
        Location loc = new Location(center.getWorld(),
                point.getX(), clicked.getY() + 1.02 + 0.16 * stack, point.getZ());
        ArmorStand stand = center.getWorld().spawn(loc, ArmorStand.class);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setPersistent(false);
        stand.getEquipment().setHelmet(new ItemStack(plugin.getChipManager().getChipMaterial(chip)));
        chipStands.add(stand);
    }

    private void clearChipStands() {
        for (ArmorStand stand : chipStands) {
            stand.remove();
        }
        chipStands.clear();
    }

    // ---------------------------------------------------------------
    //  Голограммы
    // ---------------------------------------------------------------

    private void spawnTextDisplay(Location loc, String text, float scale) {
        TextDisplay display = center.getWorld().spawn(loc, TextDisplay.class);
        display.setText(text);
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(true);
        display.setPersistent(false);
        if (scale != 1.0f) {
            display.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(scale, scale, scale),
                    new org.joml.Quaternionf(),
                    new org.joml.Vector3f(1, 1, 1),
                    new org.joml.Quaternionf()));
        }
        displays.add(display);
    }

    private void updateStatusHologram() {
        if (statusDisplay == null || statusDisplay.isDead()) {
            return;
        }
        switch (phase) {
            case BETTING -> statusDisplay.setText(Messages.raw("betting-countdown")
                    .replace("{seconds}", String.valueOf(Math.max(0, (phaseTicks + 19) / 20))));
            case SPINNING -> statusDisplay.setText(Messages.raw("spin-start"));
            case RESULT -> {
                String colorKey = winningNumber == 0 ? "color-zero"
                        : WheelNumbers.isRed(winningNumber) ? "color-red" : "color-black";
                statusDisplay.setText(Messages.raw("result")
                        .replace("{number}", String.valueOf(winningNumber))
                        .replace("{color}", Messages.raw(colorKey)));
            }
        }
    }

    /**
     * R3: сущности стола неперсистентны. Если чанк выгружался, лунки/шарик/
     * голограммы удалены, а тикер жив и ставки уходят «в пустоту».
     * Пересоздаём сущности, не задваивая живые.
     */
    private void ensureEntitiesAlive() {
        boolean broken = pockets.size() != 37 || ball == null || ball.isDead();
        if (!broken) {
            for (ArmorStand pocket : pockets) {
                if (pocket.isDead()) {
                    broken = true;
                    break;
                }
            }
        }
        if (!broken) {
            return;
        }

        // убираем мёртвых из списков
        pockets.removeIf(Entity::isDead);
        chipStands.removeIf(Entity::isDead);
        displays.removeIf(Entity::isDead);
        if (ball != null && ball.isDead()) {
            ball = null;
        }

        // живые сущности тоже убираем, чтобы после респауна не было дублей
        for (ArmorStand stand : new ArrayList<>(pockets)) {
            if (!stand.isDead()) {
                stand.remove();
            }
        }
        for (ArmorStand stand : new ArrayList<>(chipStands)) {
            if (!stand.isDead()) {
                stand.remove();
            }
        }
        for (Entity entity : new ArrayList<>(displays)) {
            if (!entity.isDead()) {
                entity.remove();
            }
        }
        if (ball != null && !ball.isDead()) {
            ball.remove();
        }
        pockets.clear();
        chipStands.clear();
        displays.clear();
        ball = null;
        titleDisplay = null;
        statusDisplay = null;

        spawnEntities();
        plugin.getLogger().info("Стол " + id + ": сущности пересозданы после выгрузки чанка");
    }

    // ---------------------------------------------------------------
    //  Геометрия колеса
    // ---------------------------------------------------------------

    private void teleportWheel(double angle) {
        for (int i = 0; i < pockets.size(); i++) {
            double a = angle + i * STEP;
            ArmorStand stand = pockets.get(i);
            if (stand != null && !stand.isDead()) {
                stand.teleport(pointAt(a, POCKET_R, POCKET_Y));
            }
        }
    }

    private void teleportBall(double angle, double radius) {
        if (ball != null && !ball.isDead()) {
            ball.teleport(pointAt(angle, radius, BALL_Y));
        }
    }

    private Location pointAt(double angle, double radius, double yOffset) {
        double c = Math.cos(angle) * radius;
        double s = Math.sin(angle) * radius;
        return new Location(center.getWorld(),
                center.getX() + rx * c + fx * s,
                center.getY() + yOffset,
                center.getZ() + rz * c + fz * s);
    }

    /** Входит ли блок в защищённую зону стола (для антигрифа и обработки кликов). */
    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(center.getWorld())) {
            return false;
        }
        if (Math.abs(loc.getY() - center.getY()) > 4) {
            return false;
        }
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double lx = dx * rx + dz * rz;
        double lz = dx * fx + dz * fz;
        return lx >= -1.6 && lx <= 13.6 && lz >= -8.6 && lz <= 13.3;
    }

    public boolean isCasinoEntity(Entity entity) {
        return pockets.contains(entity) || chipStands.contains(entity)
                || displays.contains(entity) || entity.equals(ball);
    }

    // ---------------------------------------------------------------
    //  Сущности стола
    // ---------------------------------------------------------------

    private void spawnEntities() {
        // 37 лунок с цветными блоками
        for (int i = 0; i < 37; i++) {
            ArmorStand stand = center.getWorld().spawn(pointAt(i * STEP, POCKET_R, POCKET_Y), ArmorStand.class);
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.getEquipment().setHelmet(new ItemStack(WheelNumbers.helmetFor(WheelNumbers.EUROPEAN_ORDER[i])));
            pockets.add(stand);
        }
        // шарик
        ball = center.getWorld().spawn(pointAt(Math.PI, TRACK_R, BALL_Y), ArmorStand.class);
        ball.setInvisible(true);
        ball.setMarker(true);
        ball.setGravity(false);
        ball.setInvulnerable(true);
        ball.setPersistent(false);
        ball.getEquipment().setHelmet(new ItemStack(Material.WHITE_CONCRETE));

        // голограммы
        spawnTextDisplay(center.clone().add(0, 3.4, 0), "&6&lЕВРОПЕЙСКАЯ РУЛЕТКА", 1.2f);
        titleDisplay = (TextDisplay) displays.get(displays.size() - 1);
        spawnTextDisplay(center.clone().add(0, 2.6, 0), "", 1.0f);
        statusDisplay = (TextDisplay) displays.get(displays.size() - 1);

        // цифры над ячейками сетки
        if (plugin.getConfig().getBoolean("show-table-numbers", true)) {
            boolean wheelNumbers = plugin.getConfig().getBoolean("show-wheel-numbers", false);
            for (int c = 0; c < BetDetector.COLUMNS; c++) {
                for (int r = 0; r < BetDetector.ROWS; r++) {
                    int n = BetDetector.numberAt(c, r);
                    Location cell = toWorld(c + 0.5, BetDetector.GRID_LZ0 + r + 0.5, 1.05);
                    String color = WheelNumbers.isRed(n) ? "&c" : "&f";
                    spawnTextDisplay(cell, color + n, 0.45f);
                }
            }
            spawnTextDisplay(toWorld(-0.5, BetDetector.GRID_LZ0 + 1.5, 1.05), "&a0", 0.45f);
            if (wheelNumbers) {
                for (int i = 0; i < 37; i++) {
                    int n = WheelNumbers.EUROPEAN_ORDER[i];
                    Location cell = pointAt(i * STEP, POCKET_R, 2.0);
                    String color = n == 0 ? "&a" : WheelNumbers.isRed(n) ? "&c" : "&8";
                    spawnTextDisplay(cell, color + n, 0.4f);
                }
            }
        }
    }

    /** Точка мира по локальным координатам стола. */
    public Location toWorld(double lx, double lz, double yOffset) {
        return new Location(center.getWorld(),
                center.getX() + rx * lx + fx * lz,
                center.getY() + yOffset,
                center.getZ() + rz * lx + fz * lz);
    }

    // ---------------------------------------------------------------
    //  Откат постройки (/roulette undo)
    // ---------------------------------------------------------------

    /**
     * Запоминает исходное состояние блока перед постройкой стола.
     * Вызывается из {@link TableBuilder} для каждого блока.
     */
    public void recordOriginalBlock(int x, int y, int z, String blockData) {
        originalBlocks.record(x, y, z, blockData);
    }

    /** @return снимок исходных блоков ({@code "x,y,z" -> BlockData}) */
    public BlockSnapshot getOriginalBlocks() {
        return originalBlocks;
    }

    /** Загружает снимок исходных блоков из tables.yml. */
    public void setOriginalBlocks(List<String> entries) {
        BlockSnapshot loaded = BlockSnapshot.deserialize(entries);
        for (Map.Entry<String, String> entry : loaded.asMap().entrySet()) {
            String[] coords = entry.getKey().split(",");
            originalBlocks.record(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]),
                    Integer.parseInt(coords[2]), entry.getValue());
        }
    }

    /**
     * Убирает стол и восстанавливает блоки, которые были до постройки.
     *
     * <p>Если снимка нет (стол создан до появления {@code /roulette undo}),
     * в fallback очищается вся область стола блоками воздуха — иначе
     * «откат» оставил бы бетон и камень в мире.</p>
     *
     * @return true, если блоки были восстановлены по снимку
     */
    public boolean restoreBlocks() {
        org.bukkit.World world = center.getWorld();
        if (world == null) {
            return false;
        }
        if (originalBlocks.isEmpty()) {
            TableBuilder.remove(this);
            return false;
        }
        int restored = 0;
        for (Map.Entry<String, String> entry : originalBlocks.asMap().entrySet()) {
            String[] coords = entry.getKey().split(",");
            if (coords.length != 3) {
                continue;
            }
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);
            world.getChunkAt(x >> 4, z >> 4).load();
            try {
                world.getBlockAt(x, y, z).setBlockData(org.bukkit.Bukkit.createBlockData(entry.getValue()), false);
                restored++;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Не удалось восстановить блок " + entry.getKey()
                        + " (" + entry.getValue() + "): " + e.getMessage());
            }
        }
        plugin.getLogger().info("Стол " + id + ": восстановлено блоков после отката — " + restored);
        return true;
    }

    // ---------------------------------------------------------------
    //  Утилиты
    // ---------------------------------------------------------------

    private void broadcast(net.kyori.adventure.text.Component message) {
        double radius = plugin.getConfig().getDouble("broadcast-radius", 30);
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player player) {
                Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(message));
            }
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double normalize(double angle) {
        double a = angle % FULL;
        return a < 0 ? a + FULL : a;
    }

    private static double easeOutQuart(double t) {
        return 1 - Math.pow(1 - t, 4);
    }

    private static double easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    // ---------------------------------------------------------------
    //  Геттеры
    // ---------------------------------------------------------------

    public String getId() {
        return id;
    }

    public Phase getPhase() {
        return phase;
    }

    /** Центр блока колеса (для сохранения). */
    public Location getCenter() {
        return center.clone();
    }

    public float getYaw() {
        return yaw;
    }

    public int getBetCount() {
        return bets.size();
    }
}
