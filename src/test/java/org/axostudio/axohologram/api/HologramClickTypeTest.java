package org.axostudio.axohologram.api;

import org.axostudio.axohologram.api.action.HologramActionType;
import org.axostudio.axohologram.api.action.HologramClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HologramClickTypeTest {

    @Test
    void parsesMediaHoverTriggersAndKeepsYamlNamesStable() {
        assertEquals(HologramClickType.HOVER, HologramClickType.fromString("hover"));
        assertEquals(HologramClickType.OFF_HOVER, HologramClickType.fromString("off-hover"));
        assertEquals("hover", HologramClickType.HOVER.getDisplayName());
        assertEquals("off_hover", HologramClickType.OFF_HOVER.getDisplayName());
    }

    @Test
    void stillRejectsUnknownTriggers() {
        assertNull(HologramClickType.fromString("middle_click_unknown"));
    }

    @Test
    void parsesActionTypes() {
        assertEquals(HologramActionType.MENU_PAGE, HologramActionType.fromString("menu-page"));
        assertEquals("menu_page", HologramActionType.MENU_PAGE.getDisplayName());
        assertEquals(HologramActionType.COMMAND, HologramActionType.fromString("command"));
    }
}
