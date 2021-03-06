/*
 * Plugin Commons, a Bukkit development library
 * Copyright (C) Lucky_fish0w0
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package moe.orangemc.plugincommons.inventory.impl.listener;

import moe.orangemc.plugincommons.inventory.PluginInventory;
import moe.orangemc.plugincommons.inventory.PluginInventoryManager;
import moe.orangemc.plugincommons.inventory.control.Button;
import moe.orangemc.plugincommons.inventory.control.Control;
import moe.orangemc.plugincommons.inventory.control.ProgressBar;
import moe.orangemc.plugincommons.inventory.control.handler.InventoryHandler;
import moe.orangemc.plugincommons.inventory.impl.PluginInventoryImpl;
import moe.orangemc.plugincommons.inventory.impl.control.ButtonImpl;
import moe.orangemc.plugincommons.inventory.impl.control.PlaceableFieldImpl;
import moe.orangemc.plugincommons.inventory.impl.control.ProgressBarImpl;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Don't use it :)
 * And don't stare at these shitty code
 */
public final class InventoryListener implements Listener {
    private final PluginInventoryManager manager;

    public InventoryListener(PluginInventoryManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory lookingInventory = event.getWhoClicked().getOpenInventory().getTopInventory();

        PluginInventory inventory = manager.getInventoryByBukkitInventory(lookingInventory);
        if (inventory == null) {
            return;
        }

        if (!inventory.checkInventory(event.getInventory())) {
            return;
        }

        ItemStack itemPut = event.getOldCursor().clone();
        switch (event.getType()) {
            case SINGLE:
                itemPut.setAmount(1);
                break;
            case EVEN:
                ItemStack remain = event.getCursor();
                int amount = itemPut.getAmount();
                if (remain != null) {
                    amount -= remain.getAmount();
                }
                amount /= event.getInventorySlots().size();
                itemPut.setAmount(amount);
                break;
            default:
                return;
        }
        Set<Integer> remainItems = new HashSet<>(event.getInventorySlots());
        List<Control> controls = new LinkedList<>(inventory.getControls());
        controls.removeIf(control -> !(control instanceof PlaceableFieldImpl));
        remainItems.removeIf((index) -> {
            for (Control control : controls) {
                if (((PlaceableFieldImpl) control).isSlotInsideField(index)) {
                    return true;
                }
            }
            return false;
        });

        if (remainItems.size() != 0) {
            event.setCancelled(true);
            return;
        }

        for (Control control : controls) {
            PlaceableFieldImpl field = (PlaceableFieldImpl) control;
            for (Integer index : event.getInventorySlots()) {
                if (field.isSlotInsideField(index)) {
                    if (processItemPut(index, (Player) event.getWhoClicked(), itemPut, field)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory lookingInventory = event.getWhoClicked().getOpenInventory().getTopInventory();

        PluginInventory inventory = manager.getInventoryByBukkitInventory(lookingInventory);
        if (inventory == null) {
            return;
        }
        List<Control> controls = inventory.getControls();

        // check unmodifiable item.
        List<ItemStack> unmodifiableItems = new ArrayList<>();
        for (Control control : controls) {
            if (control instanceof ButtonImpl) {
                unmodifiableItems.add(((Button) control).getItem());
            }
            if (control instanceof ProgressBarImpl) {
                unmodifiableItems.add(((ProgressBarImpl) control).getEmptyItem());
                unmodifiableItems.add(((ProgressBarImpl) control).getFilledItem());
            }
        }
        for (int index : inventory.getBorderSlots()) {
            unmodifiableItems.add(lookingInventory.getItem(index));
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            for (ItemStack unmodifiableItem : unmodifiableItems) {
                if (unmodifiableItem.isSimilar(event.getCursor())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // check buttons.
        for (Control control : controls) {
            if (!(control instanceof ButtonImpl)) {
                continue;
            }
            ButtonImpl button = (ButtonImpl) control;
            if (event.getClickedInventory() == lookingInventory && event.getSlot() == button.getStartIndex()) {
            	try {
		            button.getHandler().onClick((Player) event.getWhoClicked(), button);
	            } catch (Throwable t) {
		            Bukkit.getLogger().warning("Could pass click event to button");
		            t.printStackTrace();
	            }
                event.setCancelled(true);
                return;
            }
        }

        boolean processed = false;
        // check placeable fields.
        for (Control control : controls) {
            if (!(control instanceof PlaceableFieldImpl)) {
                continue;
            }

            PlaceableFieldImpl field = (PlaceableFieldImpl) control;
            switch (event.getAction()) {
                case PLACE_ALL:
                    if (!inventory.checkInventory(event.getClickedInventory())) {
                        processed = true;
                    } else if (field.isSlotInsideField(event.getSlot())) {
                        processed = true;
                        if (processItemPut(event.getSlot(), (Player) event.getWhoClicked(), event.getCursor(), field)) {
                            event.setCancelled(true);
                        }
                    }
                    break;
                case PLACE_ONE:
                    if (!inventory.checkInventory(event.getClickedInventory())) {
                        processed = true;
                    } else if (field.isSlotInsideField(event.getSlot())) {
                        processed = true;
                        ItemStack itemStack = event.getCursor().clone();
                        itemStack.setAmount(1);
                        if (processItemPut(event.getSlot(), (Player) event.getWhoClicked(), itemStack, field)) {
                            event.setCancelled(true);
                        }
                    }
                    break;
                case DROP_ALL_SLOT:
                case PICKUP_ALL:
                    if (!inventory.checkInventory(event.getClickedInventory())) {
                        processed = true;
                    } else if (field.isSlotInsideField(event.getSlot())) {
                        processed = true;
                        ItemStack itemStack = event.getCurrentItem().clone();
                        if (processItemRemove(event.getSlot(), (Player) event.getWhoClicked(), field, itemStack)) {
                            event.setCancelled(true);
                        }
                    }
                    break;
                case DROP_ONE_SLOT:
                case PICKUP_ONE:
                    if (!inventory.checkInventory(event.getClickedInventory())) {
                        processed = true;
                    } else if (field.isSlotInsideField(event.getSlot())) {
                        processed = true;
	                    ItemStack itemStack = event.getCurrentItem().clone();
	                    itemStack.setAmount(1);
                        if (processItemRemove(event.getSlot(), (Player) event.getWhoClicked(), field, event.getCurrentItem())) {
                            event.setCancelled(true);
                        }
                    }
                    break;
                case SWAP_WITH_CURSOR:
                    if (!inventory.checkInventory(event.getClickedInventory())) {
                        processed = true;
                    } else if (field.isSlotInsideField(event.getSlot())) {
                        processed = true;
                        if (processItemSwap(event.getSlot(), (Player) event.getWhoClicked(), field, event.getCursor(), event.getCurrentItem())) {
                            event.setCancelled(true);
                        }
                    }
                    break;
                case HOTBAR_SWAP:
                    if (!inventory.checkInventory(event.getClickedInventory())) {
                        processed = true;
                    } else if (field.isSlotInsideField(event.getSlot())) {
                        processed = true;
                        ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                        if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                            if (processItemPut(event.getSlot(), (Player) event.getWhoClicked(), hotbarItem, field)) {
                                event.setCancelled(true);
                            }
                        } else {
                            if (processItemRemove(event.getSlot(), (Player) event.getWhoClicked(), field, event.getCurrentItem())) {
                                event.setCancelled(true);
                            }
                        }
                    }
                    break;
                case HOTBAR_MOVE_AND_READD:
                    if (!inventory.checkInventory(event.getClickedInventory())) {
                        processed = true;
                    } else if (field.isSlotInsideField(event.getSlot())) {
                        processed = true;
                        ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                        if (processItemSwap(event.getSlot(), (Player) event.getWhoClicked(), field, hotbarItem, event.getCurrentItem())) {
                            event.setCancelled(true);
                        }
                    }
                    break;
	            case PICKUP_HALF:
	            	if (!inventory.checkInventory(event.getClickedInventory())) {
	            		processed = true;
		            } else if (field.isSlotInsideField(event.getSlot())) {
			            processed = true;
			            ItemStack itemStack = event.getCurrentItem().clone();
			            itemStack.setAmount((int) Math.ceil(itemStack.getAmount() / 2.0));
			            if (processItemRemove(event.getSlot(), (Player) event.getWhoClicked(), field, event.getCurrentItem())) {
				            event.setCancelled(true);
			            }
		            }
	            	break;
            }

            if (processed) {
                break;
            }
        }

        if (!processed) {
            event.setCancelled(true);
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
        	event.setCancelled(false);
            AtomicInteger amount = new AtomicInteger(event.getCursor().getAmount());
            int maxAmount = event.getCursor().getType().getMaxStackSize();
            // check if player wants to take away other controls
	        for (int slots : inventory.getBorderSlots()) {
	        	if (lookingInventory.getItem(slots) != null && lookingInventory.getItem(slots).isSimilar(event.getCursor())) {
	        		event.setCancelled(true);
	        		return;
		        }
	        }

            AtomicBoolean exitForcibly = new AtomicBoolean(false);
            for (Control control : controls) {
	            if (!(control instanceof PlaceableFieldImpl)) {
	            	if (control instanceof Button) {
	            		if (((Button) control).getItem().isSimilar(event.getCursor())) {
	            			event.setCancelled(true);
	            			return;
			            }
		            }
	            	if (control instanceof ProgressBar) {
	            		if (((ProgressBar) control).getEmptyItem().isSimilar(event.getCursor()) || ((ProgressBar) control).getFilledItem().isSimilar(event.getCursor())) {
	            			event.setCancelled(true);
	            			return;
			            }
		            }
		            continue;
	            } else if (exitForcibly.get()) {
		            continue;
	            }

                PlaceableFieldImpl field = (PlaceableFieldImpl) control;
                if (amount.get() >= maxAmount) {
                    break;
                }
                Map<Integer, ItemStack> content = new HashMap<>(field.getContent());
                content.forEach((index, item) -> {
                    if (!item.isSimilar(event.getCursor())) {
                        return;
                    }

                    int takeAmount = Math.min(item.getAmount(), maxAmount - amount.get());
                    ItemStack takenItem = item.clone();
                    takenItem.setAmount(takeAmount);
                    if (!field.getHandler().onTake(index, (Player) event.getWhoClicked(), takenItem)) {
                        event.setCancelled(true);
                        exitForcibly.set(true);
                        return;
                    }
                    item.setAmount(item.getAmount() - takeAmount);
                    if (item.getAmount() == 0) {
                        item.setAmount(1);
                        item.setType(Material.AIR);
                    }

                    amount.addAndGet(takeAmount);
                });
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        PluginInventoryImpl pi = (PluginInventoryImpl) manager.getInventoryByBukkitInventory(event.getInventory());
        if (pi == null) {
            return;
        }

        InventoryHandler ih = pi.getHandler();
        if (ih != null) {
            ih.onClose((Player) event.getPlayer());
        }
    }

    private boolean processItemPut(int slot, Player who, ItemStack itemToPut, PlaceableFieldImpl field) {
        Map<Integer, ItemStack> content = new HashMap<>(field.getContent());
        ItemStack item = content.get(field.locateRelativeOffsetOfSlot(slot));
        ItemStack placedItem;

        if (item != null) {
            item = item.clone();
            int amount = Math.min(item.getType().getMaxStackSize(), item.getAmount() + itemToPut.getAmount());
            item.setAmount(amount);
            placedItem = item.clone();
            placedItem.setAmount(item.getType().getMaxStackSize() - amount);
        } else {
            item = itemToPut;
            placedItem = itemToPut.clone();
        }
        boolean accept;
        try {
	        accept = field.getHandler().onPlace(field.locateRelativeOffsetOfSlot(slot), who, placedItem);
        } catch (Throwable t) {
        	accept = false;
        	Bukkit.getLogger().warning("Could not pass place event to field");
        	t.printStackTrace();
        }
        if (accept) {
            content.put(field.locateRelativeOffsetOfSlot(slot), item);
            field.setContent(content, false);
        }
        return !accept;
    }

    private boolean processItemRemove(int slot, Player who, PlaceableFieldImpl field, ItemStack item) {
        Map<Integer, ItemStack> content = new HashMap<>(field.getContent());
        int located = field.locateRelativeOffsetOfSlot(slot);
        ItemStack itemBeingTaken = content.get(located).clone();
        itemBeingTaken.setAmount(itemBeingTaken.getAmount() - item.getAmount());
        content.put(located, itemBeingTaken);
        if (itemBeingTaken.getAmount() <= 0) {
	        content.remove(field.locateRelativeOffsetOfSlot(slot));
        }

        boolean accept;
        try {
	        accept = field.getHandler().onTake(field.locateRelativeOffsetOfSlot(slot), who, item.clone());
        } catch (Throwable t) {
        	Bukkit.getLogger().warning("Could not pass take event to field");
        	accept = false;
        	t.printStackTrace();
        }
	    if (accept) {
            field.setContent(content, false);
        }
        return !accept;
    }

    private boolean processItemSwap(int slot, Player who, PlaceableFieldImpl field, ItemStack swapWith, ItemStack item) {
        Map<Integer, ItemStack> content = new HashMap<>(field.getContent());
        content.put(field.locateRelativeOffsetOfSlot(slot), swapWith);

        boolean accept;
        try {
	        accept = field.getHandler().onSwap(field.locateRelativeOffsetOfSlot(slot), who, item, swapWith);
        } catch (Throwable t) {
        	accept = false;
        	Bukkit.getLogger().warning("Could not pass swap event to field");
        	t.printStackTrace();
        }
	    if (accept) {
            field.setContent(content, false);
        }
        return !accept;
    }
}
