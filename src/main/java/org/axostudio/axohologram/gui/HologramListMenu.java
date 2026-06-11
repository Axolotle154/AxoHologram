package org.axostudio.axohologram.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.axostudio.axohologram.media.MediaHologram;
import org.axostudio.axohologram.media.MediaType;
import org.axostudio.axohologram.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class HologramListMenu implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int CONTENT_SLOTS = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final AxoHologram plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey idKey;

    public HologramListMenu(AxoHologram plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "list_menu_action");
        this.idKey = new NamespacedKey(plugin, "list_menu_id");
    }

    public void open(Player player, int page) {
        if (player == null || !player.isOnline()) {
            return;
        }

        List<MenuEntry> entries = entries();
        if (entries.isEmpty()) {
            MessageUtil.sendMessage(player, plugin.getConfigManager().getMessages().getString("list-empty"));
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / CONTENT_SLOTS));
        int resolvedPage = Math.max(0, Math.min(page, totalPages - 1));
        HologramListHolder holder = new HologramListHolder(resolvedPage);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title(resolvedPage, totalPages));
        holder.setInventory(inventory);

        int start = resolvedPage * CONTENT_SLOTS;
        int end = Math.min(entries.size(), start + CONTENT_SLOTS);
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start, entryItem(entries.get(index)));
        }

        ItemStack filler = controlItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), "none");
        for (int slot = CONTENT_SLOTS; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        if (resolvedPage > 0) {
            inventory.setItem(PREVIOUS_SLOT, controlItem(Material.ARROW, "Pagina anterior",
                    List.of("Ir a la pagina " + resolvedPage), "previous"));
        }
        inventory.setItem(CLOSE_SLOT, controlItem(Material.BARRIER, "Cerrar", List.of(), "close"));
        if (resolvedPage + 1 < totalPages) {
            inventory.setItem(NEXT_SLOT, controlItem(Material.ARROW, "Pagina siguiente",
                    List.of("Ir a la pagina " + (resolvedPage + 2)), "next"));
        }

        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof HologramListHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) {
            return;
        }

        PersistentDataContainer data = clicked.getItemMeta().getPersistentDataContainer();
        String action = data.get(actionKey, PersistentDataType.STRING);
        if (action == null || action.equals("none")) {
            return;
        }

        switch (action) {
            case "previous" -> open(player, holder.page() - 1);
            case "next" -> open(player, holder.page() + 1);
            case "close" -> player.closeInventory();
            case "teleport" -> teleportToEntry(player, data.get(idKey, PersistentDataType.STRING));
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HologramListHolder) {
            event.setCancelled(true);
        }
    }

    private void teleportToEntry(Player player, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (!player.hasPermission("axohologram.teleport") && !player.hasPermission("axohologram.admin")) {
            MessageUtil.sendMessage(player, plugin.getConfigManager().getMessages().getString("no-permission"));
            return;
        }

        Hologram hologram = plugin.getHologramManager().getHologram(id);
        Location location;
        String worldName;
        if (hologram != null) {
            location = hologram.getLocation();
            worldName = hologram.getWorldName();
        } else {
            MediaHologram media = plugin.getMediaManager() == null ? null : plugin.getMediaManager().getHologram(id);
            if (media == null) {
                MessageUtil.sendMessage(player, plugin.getConfigManager().getMessages().getString("hologram-not-found").replace("<hologram_id>", id));
                return;
            }
            location = media.getLocation();
            worldName = media.getWorldName();
        }

        if (location.getWorld() == null) {
            MessageUtil.sendMessage(player, plugin.getConfigManager().getMessages().getString("world-unavailable")
                    .replace("<world>", worldName == null ? "unknown" : worldName));
            return;
        }

        player.closeInventory();
        player.teleportAsync(location);
        MessageUtil.sendMessage(player, plugin.getConfigManager().getMessages().getString("teleport-success").replace("<hologram_id>", id));
    }

    private List<MenuEntry> entries() {
        List<MenuEntry> entries = new ArrayList<>();
        for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
            entries.add(normalEntry(hologram));
        }
        if (plugin.getMediaManager() != null) {
            for (MediaHologram hologram : plugin.getMediaManager().getAllMediaHolograms()) {
                entries.add(mediaEntry(hologram));
            }
        }
        entries.sort(Comparator.comparing(MenuEntry::id, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private MenuEntry normalEntry(Hologram hologram) {
        Location location = hologram.getLocation();
        int lines = 0;
        for (HologramPage page : hologram.getPages()) {
            lines += page.getLines().size();
        }
        return new MenuEntry(
                hologram.getId(),
                "NORMAL",
                materialFor(hologram),
                hologram.getWorldName(),
                location,
                List.of(
                        "Paginas: " + hologram.getPages().size(),
                        "Lineas: " + lines,
                        "Visibilidad: " + hologram.getVisibilityMode().name()
                )
        );
    }

    private MenuEntry mediaEntry(MediaHologram hologram) {
        Location location = hologram.getLocation();
        MediaType type = hologram.getType();
        return new MenuEntry(
                hologram.getId(),
                type.name(),
                type == MediaType.VIDEO ? Material.MUSIC_DISC_13 : Material.FILLED_MAP,
                hologram.getWorldName(),
                location,
                List.of(
                        "Estado: " + hologram.getState().name(),
                        "Tamano: " + formatDecimal(hologram.getSettings().width()) + " x " + formatDecimal(hologram.getSettings().height()),
                        "Visores: " + hologram.viewerCount()
                )
        );
    }

    private ItemStack entryItem(MenuEntry entry) {
        ItemStack item = new ItemStack(entry.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(entry.id(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(line("Tipo: ", entry.type(), NamedTextColor.WHITE));
        lore.add(line("Mundo: ", entry.worldName() == null ? "unknown" : entry.worldName(), NamedTextColor.WHITE));
        lore.add(line("XYZ: ", formatLocation(entry.location()), NamedTextColor.WHITE));
        for (String detail : entry.details()) {
            lore.add(Component.text(detail, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click para teletransportarte", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "teleport");
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, entry.id());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack controlItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(value -> Component.text(value, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private Material materialFor(Hologram hologram) {
        HologramPage page = hologram.getPages().isEmpty() ? null : hologram.getPages().get(Math.max(0, Math.min(hologram.getDefaultPageIndex(), hologram.getPages().size() - 1)));
        HologramLine line = page == null || page.getLines().isEmpty() ? null : page.getLines().getFirst();
        LineType type = line == null ? LineType.TEXT : line.getType();
        return switch (type) {
            case ITEM -> Material.ITEM_FRAME;
            case BLOCK -> Material.GRASS_BLOCK;
            case TEXT -> Material.OAK_SIGN;
        };
    }

    private Component title(int page, int totalPages) {
        return Component.text("AxoHologram", NamedTextColor.AQUA)
                .append(Component.text(" - Hologramas ", NamedTextColor.DARK_GRAY))
                .append(Component.text((page + 1) + "/" + totalPages, NamedTextColor.GRAY));
    }

    private Component line(String label, String value, NamedTextColor valueColor) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, valueColor))
                .decoration(TextDecoration.ITALIC, false);
    }

    private String formatLocation(Location location) {
        return String.format(Locale.US, "%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
    }

    private String formatDecimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private record MenuEntry(String id, String type, Material material, String worldName, Location location, List<String> details) {
    }

    private static final class HologramListHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        private HologramListHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private int page() {
            return page;
        }
    }
}
