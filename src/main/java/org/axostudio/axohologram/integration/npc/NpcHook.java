package org.axostudio.axohologram.integration.npc;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;

public interface NpcHook {

    String getPluginName();

    boolean isAvailable();

    boolean exists(String npcName);

    Optional<Location> getLocation(String npcName);

    Collection<String> getNpcNames();
}
