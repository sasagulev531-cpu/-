package Frontiks.Craft.casino.gui;

import Frontiks.Craft.casino.CasinoPlugin;
import Frontiks.Craft.casino.bets.Bet;
import Frontiks.Craft.casino.bets.BetType;
import Frontiks.Craft.casino.game.RouletteGame;
import Frontiks.Craft.casino.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI-казино: единое меню рулетки на 54 слота.
 *
 * <p>Меню открывается по {@code /roulette}. Все игроки видят один и тот же
 * общий раунд ({@link RouletteGame}), но ставки и баланс — свои.</p>
 *
 * <p>Раскладка — {@link GuiLayout}: числа 0–36, быстрые внешние ставки,
 * вторая страница с дюжинами/колонками, третья — с комбинациями;
 * выбор фишки и сброс своих ставок.</p>
 */
public class CasinoGui {

    /** Holder, по которому слушатель опознаёт наше меню. */
    public static final class Holder implements InventoryHolder {
        private final UUID player;
        private int page;
        private Inventory inventory;
        private BetType combination = BetType.SPLIT;
        private boolean vertical;

        Holder(UUID player, int page) {
            this.player = player;
            this.page = page;
        }

        public UUID getPlayerId() {
            return player;
        }

        public int getPage() {
            return page;
        }

        void setPage(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final CasinoPlugin plugin;
    private final java.util.Map<UUID, Inventory> open = new ConcurrentHashMap<>();

    public CasinoGui(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        int page = GuiLayout.PAGE_NUMBERS;
        Holder holder = new Holder(player.getUniqueId(), page);
        Inventory inv = Bukkit.createInventory(holder, GuiLayout.SIZE, Messages.legacy(Messages.raw("gui-title")));
        holder.inventory = inv;
        render(inv, player.getUniqueId(), page);
        player.openInventory(inv);
        open.put(player.getUniqueId(), inv);
    }

    public void close(Player player, Inventory closed) {
        open.remove(player.getUniqueId(), closed);
    }

    public void closeAll() {
        for (UUID id : open.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.closeInventory();
            }
        }
        open.clear();
    }

    public boolean isViewing(Player player) {
        return open.containsKey(player.getUniqueId());
    }

    public void broadcast(net.kyori.adventure.text.Component message) {
        for (UUID id : open.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Messages.send(p, message);
            }
        }
    }

    /** Перерисовать все открытые меню (тик фазы, новая ставка и т.д.). */
    public void refreshAll() {
        for (java.util.Map.Entry<UUID, Inventory> entry : open.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) {
                open.remove(entry.getKey());
                continue;
            }
            int page = entry.getValue().getHolder() instanceof Holder h ? h.getPage() : GuiLayout.PAGE_NUMBERS;
            render(entry.getValue(), entry.getKey(), page);
        }
    }

    public void refresh(Player player) {
        Inventory inv = open.get(player.getUniqueId());
        if (inv != null) {
            int page = inv.getHolder() instanceof Holder h ? h.getPage() : GuiLayout.PAGE_NUMBERS;
            render(inv, player.getUniqueId(), page);
        }
    }

    public void handleClick(Player player, int slot) {
        Inventory inv = open.get(player.getUniqueId());
        if (inv == null) {
            return;
        }
        int page = inv.getHolder() instanceof Holder h ? h.getPage() : GuiLayout.PAGE_NUMBERS;
        Holder holder = (Holder) inv.getHolder();

        // --- служебные слоты ---
        if (slot == GuiLayout.CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == GuiLayout.PAGE_SWITCH) {
            int next = (page + 1) % 3;
            if (inv.getHolder() instanceof Holder h) {
                h.setPage(next);
            }
            render(inv, player.getUniqueId(), next);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.6f);
            return;
        }
        if (slot == GuiLayout.CLEAR_BETS) {
            plugin.getGame().clearBets(player);
            render(inv, player.getUniqueId(), page);
            return;
        }
        if (slot >= GuiLayout.CHIP_START && slot < GuiLayout.CHIP_START + GuiLayout.CHIP_COUNT) {
            int idx = slot - GuiLayout.CHIP_START;
            List<Long> denominations = plugin.getChipManager().getDenominations();
            if (idx < denominations.size()) {
                long amount = denominations.get(idx);
                if (plugin.getChipManager().select(player, amount)) {
                    Messages.send(player, Messages.legacy(Messages.rawPrefix())
                            .append(Messages.format("chip-selected", "amount", amount)));
                }
            }
            render(inv, player.getUniqueId(), page);
            return;
        }

        if (page == GuiLayout.PAGE_COMBINATIONS && slot >= GuiLayout.COMBO_START
                && slot < GuiLayout.COMBO_START + 4) {
            holder.combination = new BetType[] {BetType.SPLIT, BetType.STREET,
                    BetType.CORNER, BetType.SIXLINE}[slot - GuiLayout.COMBO_START];
            render(inv, player.getUniqueId(), page);
            return;
        }
        if (page == GuiLayout.PAGE_COMBINATIONS && slot == GuiLayout.COMBO_ORIENTATION) {
            holder.vertical = !holder.vertical;
            render(inv, player.getUniqueId(), page);
            return;
        }
        // --- игровые слоты ---
        GuiLayout.BetSpec spec = page == GuiLayout.PAGE_COMBINATIONS
                ? GuiLayout.combinationAt(holder.combination, GuiLayout.numberAt(slot), holder.vertical)
                : GuiLayout.betAt(page, slot);
        if (spec == null) {
            if (page == GuiLayout.PAGE_COMBINATIONS && GuiLayout.numberAt(slot) >= 0) {
                Messages.send(player, Messages.legacy(Messages.rawPrefix()).append(Messages.get("gui-invalid-position")));
            }
            return;
        }
        long chip = plugin.getChipManager().getSelectedAmount(player);
        plugin.getGame().placeBet(player, spec.type(), spec.numbers(), chip);
        render(inv, player.getUniqueId(), page);
    }

    // ---------------------------------------------------------------
    //  Отрисовка
    // ---------------------------------------------------------------

    private void render(Inventory inv, UUID playerId, int page) {
        RouletteGame game = plugin.getGame();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        long balance = plugin.getBalanceProvider().getBalance(playerId);
        long myBets = game.getPlayerBets(playerId);
        long chip = plugin.getChipManager().getSelectedAmount(player);
        int highlight = game.getHighlightNumber();

        inv.clear();

        // --- шапка ---
        String phaseKey = switch (game.getPhase()) {
            case BETTING -> "gui-phase-betting";
            case SPINNING -> "gui-phase-spinning";
            case RESULT -> "gui-phase-result";
        };
        List<String> infoLore = new ArrayList<>();
        if (game.getPhase() == RouletteGame.Phase.BETTING) {
            infoLore.add(Messages.raw("gui-countdown").replace("{seconds}",
                    String.valueOf(game.getSecondsLeft())));
        } else if (game.getPhase() == RouletteGame.Phase.RESULT) {
            infoLore.add(Messages.raw("gui-result").replace("{number}",
                    String.valueOf(game.getWinningNumber())));
        } else {
            infoLore.add(Messages.raw("gui-spinning-lore"));
        }
        infoLore.add(Messages.raw("gui-bank").replace("{amount}", String.valueOf(game.getBank())));
        infoLore.add(Messages.raw("gui-your-bets").replace("{amount}", String.valueOf(myBets)));
        infoLore.add(Messages.raw("gui-limits").replace("{min}",
                String.valueOf(plugin.getConfig().getLong("min-bet", 10))));
        inv.setItem(GuiLayout.INFO, item(Material.CLOCK, "&6&lКазино &8| &fРулетка", phaseKey, infoLore));

        // --- фишки ---
        List<Long> denominations = plugin.getChipManager().getDenominations();
        for (int i = 0; i < GuiLayout.CHIP_COUNT; i++) {
            if (i >= denominations.size()) {
                inv.setItem(GuiLayout.CHIP_START + i, filler());
                continue;
            }
            long amount = denominations.get(i);
            boolean selected = amount == chip;
            String name = (selected ? "&a&l✔ &f" : "&f") + "Фишка: " + amount;
            inv.setItem(GuiLayout.CHIP_START + i, item(
                    plugin.getChipManager().getChipMaterial(amount), name,
                    selected ? "&aВыбрана — кликните по числу" : "&7Нажмите, чтобы выбрать"));
        }

        // --- баланс и кнопки ---
        inv.setItem(GuiLayout.BALANCE, item(Material.GOLD_INGOT, "&6Баланс: &f" + balance,
                "&7Валюта: " + (plugin.getBalanceProvider() instanceof Frontiks.Craft.casino.balance.FileBalanceProvider
                        ? "тестовая (balances.yml)" : plugin.getConfig().getString("fcp-currency", "coins"))));
        inv.setItem(GuiLayout.CLEAR_BETS, item(Material.BARRIER, "&cСбросить мои ставки",
                myBets > 0 ? "&7Вернуть &f" + myBets : "&7У вас нет ставок"));
        inv.setItem(GuiLayout.CLOSE, item(Material.OAK_DOOR, "&cЗакрыть", "&7Выход из меню"));

        if (page == GuiLayout.PAGE_NUMBERS) {
            renderNumbers(inv, playerId, highlight, chip);
            renderQuickOutside(inv, playerId, chip);
        } else if (page == GuiLayout.PAGE_OUTSIDE) {
            renderOutsidePage(inv, playerId, chip);
        } else {
            renderCombinations(inv, playerId, chip, (Holder) inv.getHolder());
        }
        String nextPage = switch (page) {
            case GuiLayout.PAGE_NUMBERS -> "Внешние ставки";
            case GuiLayout.PAGE_OUTSIDE -> "Комбинации";
            default -> "Числа";
        };
        inv.setItem(GuiLayout.PAGE_SWITCH, item(Material.ARROW,
                "&eСледующая: " + nextPage + " &7(→)", "&7Страница " + (page + 1) + "/3"));
    }

    private void renderCombinations(Inventory inv, UUID playerId, long chip, Holder holder) {
        BetType[] types = {BetType.SPLIT, BetType.STREET, BetType.CORNER, BetType.SIXLINE};
        for (int i = 0; i < types.length; i++) {
            BetType type = types[i];
            inv.setItem(GuiLayout.COMBO_START + i, item(
                    holder.combination == type ? Material.EMERALD_BLOCK : Material.BOOK,
                    (holder.combination == type ? "&a✔ " : "&e") + type.getLabel(),
                    "&7Выплата: &f×" + type.getPayout(), "&7Выберите тип, затем начальное число"));
        }
        inv.setItem(GuiLayout.COMBO_ORIENTATION, item(Material.COMPASS,
                "&eСплит: " + (holder.vertical ? "вертикальный ↓" : "горизонтальный →"),
                "&7Работает только для сплита"));
        for (int n = 0; n <= 36; n++) {
            GuiLayout.BetSpec spec = GuiLayout.combinationAt(holder.combination, n, holder.vertical);
            if (spec == null) {
                inv.setItem(GuiLayout.NUMBER_START + n, item(Material.GRAY_STAINED_GLASS_PANE,
                        "&8" + n + " — недоступно", "&7Выберите подходящее начальное число"));
                continue;
            }
            Bet bet = plugin.getGame().getPlayerBet(playerId, spec.type(), spec.numbers());
            inv.setItem(GuiLayout.NUMBER_START + n, item(Material.CYAN_CONCRETE,
                    "&b" + n + " &7→ &f" + spec.numbers(),
                    "&7Выплата: &f×" + spec.type().getPayout(),
                    "&7Клик — поставить &f" + chip,
                    bet == null ? "" : "&eВаша ставка: &f" + bet.getAmount()));
        }
    }

    private void renderNumbers(Inventory inv, UUID playerId, int highlight, long chip) {
        for (int n = 0; n < 37; n++) {
            Material material = n == 0 ? Material.LIME_CONCRETE
                    : Frontiks.Craft.casino.util.WheelNumbers.isRed(n)
                    ? Material.RED_CONCRETE : Material.GRAY_CONCRETE;
            String name = "&f" + (n == 0 ? "0 (зеро)" : n);
            List<String> lore = new ArrayList<>();
            lore.add("&7Выплата: &f×35");
            Bet bet = plugin.getGame().getPlayerBet(playerId, BetType.STRAIGHT, Set.of(n));
            if (bet != null) {
                lore.add("&eВаша ставка: &f" + bet.getAmount());
            } else {
                lore.add("&7Клик — поставить &f" + chip);
            }
            if (highlight == n) {
                name = "&e&l➤ &f" + n;
                lore.add("&e&l← шарик здесь");
                inv.setItem(GuiLayout.NUMBER_START + n, item(Material.YELLOW_CONCRETE, name, lore.toArray(String[]::new)));
            } else {
                inv.setItem(GuiLayout.NUMBER_START + n, item(material, name, lore.toArray(String[]::new)));
            }
        }
    }

    private void renderQuickOutside(Inventory inv, UUID playerId, long chip) {
        renderOutside(inv, playerId, chip, GuiLayout.QUICK_OUTSIDE_START);
    }

    private void renderOutsidePage(Inventory inv, UUID playerId, long chip) {
        // дюжины
        String[] dozenNames = {"&a1-12", "&e13-24", "&c25-36"};
        Material[] dozenMats = {Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.RED_CONCRETE};
        for (int i = 0; i < 3; i++) {
            int from = i * 12 + 1;
            Set<Integer> numbers = rangeSet(from, from + 11);
            Bet bet = plugin.getGame().getPlayerBet(playerId, BetType.DOZEN, numbers);
            inv.setItem(GuiLayout.DOZEN_START + i, item(dozenMats[i],
                    "&fДюжина " + dozenNames[i],
                    "&7Выплата: &f×2", "&7Клик — поставить &f" + chip,
                    bet != null ? "&eВаша ставка: &f" + bet.getAmount() : ""));
        }
        // колонки
        for (int i = 0; i < 3; i++) {
            Set<Integer> numbers = new java.util.TreeSet<>();
            int mod = (i + 1) % 3;
            for (int n = 1; n <= 36; n++) {
                if (n % 3 == mod) {
                    numbers.add(n);
                }
            }
            Bet bet = plugin.getGame().getPlayerBet(playerId, BetType.COLUMN, numbers);
            inv.setItem(GuiLayout.COLUMN_START + i, item(Material.CYAN_CONCRETE,
                    "&fКолонка " + (i + 1), "&7Выплата: &f×2", "&7Клик — поставить &f" + chip,
                    bet != null ? "&eВаша ставка: &f" + bet.getAmount() : ""));
        }
        renderOutside(inv, playerId, chip, GuiLayout.OUTSIDE_START);
    }

    private void renderOutside(Inventory inv, UUID playerId, long chip, int startSlot) {
        BetType[] types = {BetType.RED, BetType.BLACK, BetType.EVEN, BetType.ODD, BetType.LOW, BetType.HIGH};
        String[] names = {"&cКрасное", "&8Чёрное", "&bЧёт", "&dНечёт", "&f1-18", "&f19-36"};
        Material[] mats = {Material.RED_CONCRETE, Material.BLACK_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                Material.PURPLE_CONCRETE, Material.WHITE_CONCRETE, Material.GRAY_CONCRETE};
        for (int i = 0; i < types.length; i++) {
            BetType type = types[i];
            Set<Integer> numbers = outsideNumbers(type);
            Bet bet = plugin.getGame().getPlayerBet(playerId, type, numbers);
            List<String> lore = new ArrayList<>();
            lore.add("&7Выплата: &f×1");
            lore.add("&7Клик — поставить &f" + chip);
            if (bet != null) {
                lore.add("&eВаша ставка: &f" + bet.getAmount());
            }
            inv.setItem(startSlot + i, item(mats[i], names[i], lore.toArray(String[]::new)));
        }
    }

    private static Set<Integer> outsideNumbers(BetType type) {
        return switch (type) {
            case RED -> GuiLayout.redNumbers();
            case BLACK -> GuiLayout.blackNumbers();
            case EVEN -> rangeSet(2, 36, 2);
            case ODD -> rangeSet(1, 35, 2);
            case LOW -> rangeSet(1, 18, 1);
            case HIGH -> rangeSet(19, 36, 1);
            default -> Set.of();
        };
    }

    private static Set<Integer> rangeSet(int from, int to) {
        java.util.Set<Integer> set = new java.util.TreeSet<>();
        for (int n = from; n <= to; n++) {
            set.add(n);
        }
        return set;
    }

    private static Set<Integer> rangeSet(int from, int to, int step) {
        java.util.Set<Integer> set = new java.util.TreeSet<>();
        for (int n = from; n <= to; n += step) {
            set.add(n);
        }
        return set;
    }

    // ---------------------------------------------------------------
    //  Предметы
    // ---------------------------------------------------------------

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.legacy(name));
        List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            if (!line.isEmpty()) {
                loreComponents.add(Messages.legacy(line));
            }
        }
        meta.lore(loreComponents);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String name, String loreKey, List<String> extraLore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.legacy(name));
        List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
        loreComponents.add(Messages.legacy(Messages.raw(loreKey)));
        for (String line : extraLore) {
            loreComponents.add(Messages.legacy(line));
        }
        meta.lore(loreComponents);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ", "");
    }
}
