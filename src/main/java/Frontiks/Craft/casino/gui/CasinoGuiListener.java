package Frontiks.Craft.casino.gui;

import Frontiks.Craft.casino.CasinoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Обработка кликов и закрытия меню казино. */
public class CasinoGuiListener implements Listener {

    private final CasinoPlugin plugin;

    public CasinoGuiListener(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof CasinoGui.Holder)) {
            return;
        }
        // в нашем меню ничего нельзя вытаскивать
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return; // клик по собственному инвентарю — просто отменён
        }
        if (!((CasinoGui.Holder) holder).getPlayerId().equals(player.getUniqueId())
                || !plugin.getGui().isViewing(player)) {
            return;
        }
        plugin.getGui().handleClick(player, event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CasinoGui.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CasinoGui.Holder) {
            plugin.getGui().close((Player) event.getPlayer(), event.getInventory());
        }
    }
}
