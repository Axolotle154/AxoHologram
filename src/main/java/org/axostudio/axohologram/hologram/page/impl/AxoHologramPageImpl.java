package org.axostudio.axohologram.hologram.page.impl;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.line.impl.BlockLineImpl;
import org.axostudio.axohologram.hologram.line.impl.ItemLineImpl;
import org.axostudio.axohologram.hologram.line.impl.TextLineImpl;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class AxoHologramPageImpl implements HologramPage {

    private final List<HologramLine> lines;
    private volatile String permission;

    public AxoHologramPageImpl() {
        this.lines = new CopyOnWriteArrayList<>();
        this.permission = null;
    }

    @Override
    public List<HologramLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    @Override
    public HologramLine getLine(int index) {
        if (index >= 0 && index < lines.size()) {
            return lines.get(index);
        }
        return null;
    }

    @Override
    public void addLine(HologramLine line) {
        lines.add(line);
    }

    @Override
    public void insertLine(int index, HologramLine line) {
        if (line == null) {
            return;
        }

        int targetIndex = Math.max(0, Math.min(index, lines.size()));
        lines.add(targetIndex, line);
    }

    @Override
    public void setLine(int index, HologramLine line) {
        if (index >= 0 && index < lines.size()) {
            lines.set(index, line);
        } else if (index == lines.size()) {
            lines.add(line);
        }
    }

    @Override
    public void removeLine(int index) {
        if (index >= 0 && index < lines.size()) {
            lines.remove(index);
        }
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public void setPermission(String permission) {
        this.permission = permission;
    }

    @Override
    public boolean canView(Player player) {
        return permission == null || permission.isEmpty() || player.hasPermission(permission);
    }

    @Override
    public void serialize(ConfigurationSection section) {
        if (permission != null && !permission.isEmpty()) {
            section.set("permission", permission);
        } else {
            section.set("permission", null);
        }

        if (isSimpleTextPage()) {
            List<String> textLines = new ArrayList<>();
            for (HologramLine line : lines) {
                textLines.add(((TextLineImpl) line).getContent());
            }
            section.set("type", "TEXT");
            section.set("text", textLines);
            section.set("lines", null);
            return;
        }

        List<Map<String, Object>> serializedLines = new ArrayList<>();
        List<String> compactTextGroup = new ArrayList<>();
        for (HologramLine line : lines) {
            if (isCompactTextLine(line)) {
                compactTextGroup.add(((TextLineImpl) line).getContent());
                continue;
            }

            flushCompactTextGroup(serializedLines, compactTextGroup);
            YamlConfiguration lineConfig = new YamlConfiguration();
            line.serialize(lineConfig);
            serializedLines.add(new LinkedHashMap<>(lineConfig.getValues(false)));
        }
        flushCompactTextGroup(serializedLines, compactTextGroup);
        section.set("lines", serializedLines);
    }

    public static HologramPage deserialize(ConfigurationSection section, AxoHologram plugin) {
        AxoHologramPageImpl page = new AxoHologramPageImpl();
        page.setPermission(section.getString("permission"));

        List<String> simpleTextLines = readTextEntries(section);
        if (!simpleTextLines.isEmpty()) {
            for (String line : simpleTextLines) {
                page.addLine(new TextLineImpl(line, plugin));
            }
            return page;
        }

        List<Map<?, ?>> serializedLines = section.getMapList("lines");
        if (serializedLines != null && !serializedLines.isEmpty()) {
            for (Map<?, ?> lineMap : serializedLines) {
                ConfigurationSection lineSection = new YamlConfiguration();
                lineMap.forEach((k, v) -> lineSection.set(k.toString(), v));

                String typeString = lineSection.getString("type");
                if (typeString == null) {
                    plugin.getLogger().log(Level.WARNING, "Line type not specified in hologram page. Skipping line.");
                    continue;
                }

                LineType type;
                try {
                    type = LineType.valueOf(typeString.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "Invalid line type '" + typeString + "' found in hologram page. Skipping line.", e);
                    continue;
                }

                try {
                    if (type == LineType.TEXT) {
                        List<String> groupedTextLines = readTextEntries(lineSection);
                        if (!groupedTextLines.isEmpty()) {
                            for (String lineContent : groupedTextLines) {
                                page.addLine(new TextLineImpl(lineContent, plugin));
                            }
                            continue;
                        }
                    }

                    HologramLine line = switch (type) {
                        case TEXT -> TextLineImpl.deserialize(lineSection, plugin);
                        case ITEM -> ItemLineImpl.deserialize(lineSection, plugin);
                        case BLOCK -> BlockLineImpl.deserialize(lineSection, plugin);
                    };
                    if (line != null) {
                        page.addLine(line);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "Invalid content for line type '" + typeString + "' found in hologram page. Skipping line.", e);
                }
            }
        }
        return page;
    }

    private void flushCompactTextGroup(List<Map<String, Object>> serializedLines, List<String> compactTextGroup) {
        if (compactTextGroup.isEmpty()) {
            return;
        }

        Map<String, Object> compactEntry = new LinkedHashMap<>();
        compactEntry.put("type", LineType.TEXT.name());
        compactEntry.put("text", new ArrayList<>(compactTextGroup));
        serializedLines.add(compactEntry);
        compactTextGroup.clear();
    }

    private boolean isCompactTextLine(HologramLine line) {
        if (!(line instanceof TextLineImpl textLine)) {
            return false;
        }
        if (textLine.hasBillboardOverride()) {
            return false;
        }
        if (textLine.hasHeightOverride()) {
            return false;
        }
        if (textLine.getPermission() != null && !textLine.getPermission().isBlank()) {
            return false;
        }
        Vector offset = textLine.getOffset();
        return offset.getX() == 0.0D && offset.getY() == 0.0D && offset.getZ() == 0.0D;
    }

    private static List<String> readTextEntries(ConfigurationSection section) {
        Object rawText = section.get("text");
        if (rawText instanceof List<?> rawList) {
            List<String> lines = new ArrayList<>(rawList.size());
            for (Object entry : rawList) {
                lines.add(entry == null ? "" : String.valueOf(entry));
            }
            return lines;
        }
        if (rawText instanceof String singleLine) {
            return List.of(singleLine);
        }
        return List.of();
    }

    private boolean isSimpleTextPage() {
        if (lines.isEmpty()) {
            return false;
        }

        for (HologramLine line : lines) {
            if (!(line instanceof TextLineImpl textLine)) {
                return false;
            }
            if (textLine.hasBillboardOverride()) {
                return false;
            }
            if (textLine.hasHeightOverride()) {
                return false;
            }
            if (textLine.getPermission() != null && !textLine.getPermission().isBlank()) {
                return false;
            }
            if (textLine.getOffset().getX() != 0.0D || textLine.getOffset().getY() != 0.0D || textLine.getOffset().getZ() != 0.0D) {
                return false;
            }
        }

        return true;
    }
}
