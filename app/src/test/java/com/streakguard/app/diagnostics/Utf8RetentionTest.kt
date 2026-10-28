package com.streakguard.app.diagnostics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Utf8RetentionTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun byteCapCountsUtf8BytesRatherThanCharacters() {
        val store = RollingLogStore(temporary.newFolder(), maxBytes = 200)
        repeat(10) { store.append("EVENT", "model=测试手机_$it") }
        val text = store.read()
        assertTrue(text.toByteArray(Charsets.UTF_8).size <= 200)
        assertEquals(store.size(), text.toByteArray(Charsets.UTF_8).size.toLong())
        assertTrue(text.contains("_9"))
    }
}
