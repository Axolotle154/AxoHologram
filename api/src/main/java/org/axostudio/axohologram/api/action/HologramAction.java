package org.axostudio.axohologram.api.action;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public final class HologramAction {

    private final HologramActionType type;
    private final String value;

    public HologramAction(HologramActionType type, String value) {
        this.type = Objects.requireNonNull(type, "Action type cannot be null");
        this.value = Objects.requireNonNull(value, "Action value cannot be null");
    }

    public HologramActionType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public void serialize(ConfigurationSection section) {
        if (section != null) {
            section.set("type", type.name());
            section.set("value", value);
        }
    }

    public static HologramAction deserialize(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        HologramActionType type = HologramActionType.fromString(section.getString("type"));
        String value = section.getString("value");
        if (type == null || value == null || value.trim().isEmpty()) {
            return null;
        }
        return new HologramAction(type, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HologramAction that = (HologramAction) o;
        return type == that.type && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return "HologramAction{" + "type=" + type + ", value='" + value + '\'' + '}';
    }
}
