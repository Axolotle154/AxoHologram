package org.axostudio.axohologram.common.validation

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class HologramIdTest {
    @Test
    fun acceptsSafeIdentifiers() {
        assertTrue(HologramId.isValid("spawn-01"))
        assertTrue(HologramId.isValid("A_64"))
    }

    @Test
    fun rejectsPathAndYamlBreakingIdentifiers() {
        listOf("../escape", "a/b", "a\\b", ".hidden", "", "with space", "a".repeat(65)).forEach {
            assertFalse(HologramId.isValid(it), it)
        }
    }
}
