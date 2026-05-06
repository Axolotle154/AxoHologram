package org.axostudio.axohologram.hologram.action;

import org.bukkit.configuration.ConfigurationSection;

public final class HologramAction {

    private final HologramActionType type;
    private final String value;

    public HologramAction(HologramActionType type, String value) {
        this.type = type;
        this.value = value;
    }

    public HologramActionType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public void serialize(ConfigurationSection section) {
        section.set("type", type.name());
        section.set("value", value);
    }

    public static HologramAction deserialize(ConfigurationSection section) {
        HologramActionType type = HologramActionType.fromString(section.getString("type"));
        String value = section.getString("value");
        if (type == null || value == null || value.isBlank()) {
            return null;
        }
        return new HologramAction(type, value);
    }
}
