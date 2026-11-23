package Frontiks.Craft.casino.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import Frontiks.Craft.casino.CasinoPlugin;
import Frontiks.Craft.casino.table.RouletteTable;
import Frontiks.Craft.casino.util.Messages;

/** Клики по столу, фишки и защита столов от грифа. */
public class TableListener implements Listener {

    private final CasinoPlugin plugin;

    public TableListener(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        RouletteTable table = plugin.getTableManager().findTableAt(clicked.getLocation());
        if (table == null) {
            return;
        }
        // в зоне стола отключаем стандартное использование блоков (любой клик)
        event.setCancelled(true);

        // R5: ставка и Шифт+ПКМ (номинал) — только правой кнопкой
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.getChipManager().isChipItem(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getInteractionPoint() == null) {
            return;
        }

        // Шифт + ПКМ — переключение номинала фишки
        if (player.isSneaking()) {
            long chip = plugin.getChipManager().cycle(player);
            Messages.send(player, Messages.legacy(Messages.rawPrefix())
                    .append(Messages.format("chip-selected", "amount", chip)));
            table.sendChipActionBar(player);
            return;
        }

        // обычный ПКМ фишкой — ставка
        table.tryPlaceBet(player, clicked, event.getInteractionPoint());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        RouletteTable table = plugin.getTableManager().findTableAt(event.getBlock().getLocation());
        if (table != null && !event.getPlayer().hasPermission("roulette.admin")) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), Messages.legacy(Messages.rawPrefix())
                    .append(Messages.get("protected-block")));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        RouletteTable table = plugin.getTableManager().findTableAt(event.getBlock().getLocation());
        if (table != null && !event.getPlayer().hasPermission("roulette.admin")) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), Messages.legacy(Messages.rawPrefix())
                    .append(Messages.get("protected-block")));
        }
    }

    /** R4: взрывы не ломают блоки стола. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block ->
                plugin.getTableManager().findTableAt(block.getLocation()) != null);
    }

    /** R4: страховка поверх setInvulnerable — взрывы не бьют сущности казино. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByBlock(EntityDamageByBlockEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.WITHER) {
            if (isCasinoEntity(event.getEntity())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (isCasinoEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandDamage(EntityDamageByEntityEvent event) {
        if (isCasinoEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean isCasinoEntity(Entity entity) {
        for (RouletteTable table : plugin.getTableManager().all()) {
            if (table.isCasinoEntity(entity)) {
                return true;
            }
        }
        return false;
    }
}
