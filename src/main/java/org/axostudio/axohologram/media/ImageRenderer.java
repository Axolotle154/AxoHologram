package org.axostudio.axohologram.media;

import org.axostudio.axohologram.AxoHologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class ImageRenderer {

    public static final String MEDIA_FRAME_TAG = "axohologram_media_frame";

    private static final int MAP_SIZE = 128;

    protected final AxoHologram plugin;

    public ImageRenderer(AxoHologram plugin) {
        this.plugin = plugin;
    }

    public void renderFrame(MediaHologram hologram, BufferedImage frame) {
        if (hologram == null || frame == null) {
            return;
        }
        renderFrame(hologram, MapFrameData.fromImage(frame));
    }

    public void renderFrame(MediaHologram hologram, MapFrameData frame) {
        renderFrame(hologram, frame, 0);
    }

    public void renderFrame(MediaHologram hologram, MapFrameData frame, int viewerBatchSize) {
        if (hologram == null || frame == null) {
            return;
        }

        Location location = hologram.getResolvedLocationView();
        plugin.getSchedulerUtil().runAtLocation(location, () -> renderFrameNow(hologram, frame, location, viewerBatchSize));
    }

    public void show(MediaHologram hologram, Player player) {
        if (hologram == null || player == null) {
            return;
        }

        List<MediaMapTile> tiles = hologram.getMapTiles();
        if (hasAllValidDisplays(hologram, player, tiles)) {
            return;
        }

        if (tiles.isEmpty() || !hasAllDisplays(tiles)) {
            renderCurrentFrame(hologram);
            return;
        }

        if (plugin.getSchedulerUtil().isFoliaServer()) {
            Location location = hologram.getResolvedLocationView();
            ensureDisplays(hologram, location, tiles, () -> showTiles(hologram, player, tiles));
            return;
        }

        if (!hasAllValidDisplays(tiles)) {
            renderCurrentFrame(hologram);
            return;
        }
        showTiles(hologram, player, tiles);
    }

    public void hide(MediaHologram hologram, Player player) {
        if (hologram == null || player == null) {
            return;
        }

        hologram.markDisplaysHidden(player);
        List<MediaMapTile> tiles = hologram.getMapTiles();
        if (tiles.isEmpty()) {
            return;
        }

        plugin.getSchedulerUtil().runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (MediaMapTile tile : tiles) {
                ItemFrame display = tile.display();
                if (display != null) {
                    player.hideEntity(plugin, display);
                }
            }
        });
    }

    public void destroy(MediaHologram hologram) {
        if (hologram == null) {
            return;
        }

        List<MediaMapTile> tiles = hologram.getMapTiles();
        hologram.setMapTiles(List.of());
        hologram.markDisplaysChanged();
        for (MediaMapTile tile : tiles) {
            removeDisplay(tile.display());
            tile.display(null);
        }
    }

    protected void renderFrameNow(MediaHologram hologram, MapFrameData frame, Location location, int viewerBatchSize) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        int columns = frame.columns();
        int rows = frame.rows();
        List<MediaMapTile> tiles = hologram.getMapTiles();
        boolean refreshDisplays;
        List<MediaMapTile> changedTiles;
        if (!isRuntimeCompatible(tiles, columns, rows)) {
            destroy(hologram);
            tiles = createMapRuntime(frame, location.getWorld(), columns, rows);
            hologram.setMapTiles(tiles);
            refreshDisplays = true;
            changedTiles = tiles;
        } else {
            changedTiles = updateTileImages(hologram, frame, tiles);
            refreshDisplays = !hasAllDisplays(tiles)
                    || (!plugin.getSchedulerUtil().isFoliaServer() && !hasAllValidDisplays(tiles));
        }

        if (refreshDisplays) {
            List<MediaMapTile> currentTiles = tiles;
            ensureDisplays(hologram, location, currentTiles, () -> {
                for (Player player : hologram.viewerPlayers()) {
                    if (player != null) {
                        showTiles(hologram, player, currentTiles);
                    }
                }
            });
            return;
        }
        if (!changedTiles.isEmpty()) {
            sendTileMapsToViewers(hologram, changedTiles, viewerBatchSize);
        }
    }

    private List<MediaMapTile> createMapRuntime(MapFrameData frame, World world, int columns, int rows) {
        List<MediaMapTile> tiles = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                MapView mapView = Bukkit.createMap(world);
                for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
                    mapView.removeRenderer(renderer);
                }

                MapFrameRenderer frameRenderer = new MapFrameRenderer(frame.tile(column, row));
                mapView.addRenderer(frameRenderer);

                ItemStack itemStack = new ItemStack(Material.FILLED_MAP);
                if (itemStack.getItemMeta() instanceof MapMeta mapMeta) {
                    mapMeta.setMapView(mapView);
                    itemStack.setItemMeta(mapMeta);
                }

                tiles.add(new MediaMapTile(column, row, columns, rows, mapView, frameRenderer, itemStack));
            }
        }
        return List.copyOf(tiles);
    }

    private List<MediaMapTile> updateTileImages(MediaHologram hologram, MapFrameData frame, List<MediaMapTile> tiles) {
        List<MediaMapTile> changedTiles = hologram.changedTilesBuffer();
        changedTiles.clear();
        for (MediaMapTile tile : tiles) {
            if (tile.renderer().setPixels(frame.tile(tile.column(), tile.row()))) {
                changedTiles.add(tile);
            }
        }
        return changedTiles;
    }

    private void ensureDisplays(MediaHologram hologram, Location location, List<MediaMapTile> tiles, Runnable callback) {
        if (location == null || location.getWorld() == null || tiles.isEmpty()) {
            return;
        }

        MediaSettings settings = hologram.getSettings();
        BlockFace facing = resolveFacing(location.getYaw() + 180.0F);
        Rotation rotation = resolveRotation(settings.rotation());
        AtomicInteger remainingTiles = new AtomicInteger(tiles.size());
        AtomicBoolean displaysChanged = new AtomicBoolean();

        for (MediaMapTile tile : tiles) {
            Location tileLocation = tileLocation(location, tile, facing);
            plugin.getSchedulerUtil().runAtLocation(tileLocation, () -> {
                if (ensureDisplay(tile, tileLocation, facing, rotation)) {
                    displaysChanged.set(true);
                }
                if (remainingTiles.decrementAndGet() != 0) {
                    return;
                }
                if (displaysChanged.get()) {
                    hologram.markDisplaysChanged();
                }
                callback.run();
            });
        }
    }

    private boolean ensureDisplay(MediaMapTile tile, Location location, BlockFace facing, Rotation rotation) {
        ItemFrame display = tile.display();
        if (display != null && display.isValid() && isSameTile(display, location, facing)) {
            return false;
        }

        removeDisplay(display);
        tile.display(spawnDisplay(tile, location, facing, rotation));
        return true;
    }

    private ItemFrame spawnDisplay(MediaMapTile tile, Location location, BlockFace facing, Rotation rotation) {
        try {
            return location.getWorld().spawn(location, GlowItemFrame.class, display -> configureDisplay(display, tile, facing, rotation));
        } catch (RuntimeException glowException) {
            try {
                return location.getWorld().spawn(location, ItemFrame.class, display -> configureDisplay(display, tile, facing, rotation));
            } catch (RuntimeException frameException) {
                plugin.getLogger().log(Level.WARNING, "Could not spawn media map frame at " + formatLocation(location), frameException);
                return null;
            }
        }
    }

    private void configureDisplay(ItemFrame display, MediaMapTile tile, BlockFace facing, Rotation rotation) {
        display.setVisibleByDefault(false);
        display.setVisible(false);
        display.setFixed(true);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setNoPhysics(true);
        display.setItemDropChance(0.0F);
        display.addScoreboardTag(MEDIA_FRAME_TAG);
        display.setFacingDirection(facing, true);
        display.setRotation(rotation);
        ItemStack itemStack = tile.mapItem();
        if (itemStack != null) {
            display.setItem(itemStack, false);
        }
    }

    private void showTiles(MediaHologram hologram, Player player, List<MediaMapTile> tiles) {
        if (!hologram.markDisplaysShown(player)) {
            return;
        }
        if (!plugin.getSchedulerUtil().runAtEntity(player, () -> {
            if (!player.isOnline() || !hologram.isViewing(player)) {
                hologram.markDisplaysHidden(player);
                return;
            }
            for (MediaMapTile tile : tiles) {
                ItemFrame display = tile.display();
                if (display != null) {
                    player.showEntity(plugin, display);
                }
                MapView mapView = tile.mapView();
                if (mapView != null) {
                    player.sendMap(mapView);
                }
            }
        })) {
            hologram.markDisplaysHidden(player);
        }
    }

    private void sendTileMaps(Player player, List<MediaMapTile> tiles) {
        plugin.getSchedulerUtil().runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (MediaMapTile tile : tiles) {
                MapView mapView = tile.mapView();
                if (mapView != null) {
                    player.sendMap(mapView);
                }
            }
        });
    }

    private void sendTileMapsToViewers(MediaHologram hologram, List<MediaMapTile> tiles, int viewerBatchSize) {
        int viewerCount = hologram.viewerCount();
        if (viewerCount <= 0) {
            return;
        }
        int batchSize = viewerBatchSize <= 0 ? viewerCount : Math.min(viewerBatchSize, viewerCount);
        int startIndex = hologram.nextViewerBatchStart(viewerCount, batchSize);
        int index = 0;
        int sent = 0;

        for (Player player : hologram.viewerPlayers()) {
            if (sent >= batchSize) {
                return;
            }
            if (index++ < startIndex) {
                continue;
            }
            if (player != null) {
                sendTileMaps(player, tiles);
                sent++;
            }
        }

        if (sent >= batchSize || startIndex == 0) {
            return;
        }
        for (Player player : hologram.viewerPlayers()) {
            if (sent >= batchSize) {
                return;
            }
            if (player != null) {
                sendTileMaps(player, tiles);
                sent++;
            }
        }
    }

    private Location tileLocation(Location origin, MediaMapTile tile, BlockFace facing) {
        BlockFace right = rightFace(facing);
        double horizontalOffset = tile.column() - ((tile.columns() - 1) / 2.0D);
        double verticalOffset = ((tile.rows() - 1) / 2.0D) - tile.row();
        int x = tileBlockCoordinate(origin.getX(), right.getModX(), horizontalOffset);
        int y = (int) Math.floor(origin.getY() + verticalOffset);
        int z = tileBlockCoordinate(origin.getZ(), right.getModZ(), horizontalOffset);
        return new Location(origin.getWorld(), x, y, z, origin.getYaw(), 0.0F);
    }

    private int tileBlockCoordinate(double originCoordinate, int axisModifier, double offset) {
        if (axisModifier == 0) {
            return (int) Math.floor(originCoordinate);
        }
        return (int) Math.floor(originCoordinate + axisModifier * offset);
    }

    private boolean isRuntimeCompatible(List<MediaMapTile> tiles, int columns, int rows) {
        if (tiles == null || tiles.size() != columns * rows) {
            return false;
        }
        for (MediaMapTile tile : tiles) {
            if (tile.columns() != columns || tile.rows() != rows) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAllValidDisplays(List<MediaMapTile> tiles) {
        if (tiles == null || tiles.isEmpty()) {
            return false;
        }
        for (MediaMapTile tile : tiles) {
            ItemFrame display = tile.display();
            if (display == null || !display.isValid()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAllValidDisplays(MediaHologram hologram, Player player, List<MediaMapTile> tiles) {
        return hologram.hasCurrentDisplays(player)
                && hasAllDisplays(tiles)
                && (plugin.getSchedulerUtil().isFoliaServer() || hasAllValidDisplays(tiles));
    }

    private boolean hasAllDisplays(List<MediaMapTile> tiles) {
        if (tiles == null || tiles.isEmpty()) {
            return false;
        }
        for (MediaMapTile tile : tiles) {
            if (tile.display() == null) {
                return false;
            }
        }
        return true;
    }

    private void renderCurrentFrame(MediaHologram hologram) {
        ProcessedMedia processedMedia = hologram.getProcessedMedia();
        if (processedMedia == null) {
            return;
        }
        MapFrameData frame = hologram.getType() == MediaType.VIDEO
                ? processedMedia.mapFrame(hologram.getCurrentFrame())
                : processedMedia.firstMapFrame();
        if (frame != null) {
            renderFrame(hologram, frame);
        }
    }

    private boolean isSameTile(ItemFrame display, Location location, BlockFace facing) {
        Location current = display.getLocation();
        return current.getWorld() == location.getWorld()
                && current.getBlockX() == location.getBlockX()
                && current.getBlockY() == location.getBlockY()
                && current.getBlockZ() == location.getBlockZ()
                && display.getFacing() == facing;
    }

    private void removeDisplay(ItemFrame display) {
        if (display == null) {
            return;
        }
        plugin.getSchedulerUtil().runAtEntity(display, () -> {
            if (display.isValid()) {
                display.remove();
            }
        });
    }

    private BlockFace rightFace(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case EAST -> BlockFace.NORTH;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.EAST;
        };
    }

    private BlockFace resolveFacing(float yaw) {
        float normalizedYaw = ((yaw % 360.0F) + 360.0F) % 360.0F;
        int quadrant = Math.round(normalizedYaw / 90.0F) & 3;
        return switch (quadrant) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    private Rotation resolveRotation(float degrees) {
        float normalizedDegrees = ((degrees % 360.0F) + 360.0F) % 360.0F;
        int step = Math.round(normalizedDegrees / 45.0F) & 7;
        return switch (step) {
            case 1 -> Rotation.CLOCKWISE_45;
            case 2 -> Rotation.CLOCKWISE;
            case 3 -> Rotation.CLOCKWISE_135;
            case 4 -> Rotation.FLIPPED;
            case 5 -> Rotation.FLIPPED_45;
            case 6 -> Rotation.COUNTER_CLOCKWISE;
            case 7 -> Rotation.COUNTER_CLOCKWISE_45;
            default -> Rotation.NONE;
        };
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " "
                + location.getBlockX() + ", "
                + location.getBlockY() + ", "
                + location.getBlockZ();
    }
}
