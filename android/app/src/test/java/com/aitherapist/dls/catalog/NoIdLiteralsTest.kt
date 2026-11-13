package com.aitherapist.dls.catalog

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** No step or activity ID literal may appear in Kotlin outside tests (AD-1, AD-2). */
class NoIdLiteralsTest {
    @Test
    fun `main sources contain no step or activity id literals`() {
        val src = listOf(File("src"), File("app/src"), File("android/app/src")).first { File(it, "main").isDirectory }
        val roots = src.listFiles { f ->
            f.isDirectory && !f.name.startsWith("test") && !f.name.startsWith("androidTest")
        }.orEmpty()
        val files = roots.flatMap { r -> r.walkTopDown().filter { it.extension == "kt" }.toList() }
        assertTrue("no main Kotlin sources found", files.isNotEmpty())
        val hits = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (STRING_LITERAL.findAll(line).any { ID.containsMatchIn(it.value) }) "${f.path}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertTrue("ID literals found:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun `pattern catches both id kinds`() {
        fun hit(line: String) = STRING_LITERAL.findAll(line).any { ID.containsMatchIn(it.value) }
        assertTrue(hit("val s = \"drink.cup.01\""))
        assertTrue(hit("val a = \"mild.rom.01\""))
        assertTrue(hit("val s = \"drink.cup.01.slow\""))
        assertTrue(hit("val s = \"\$x drink.cup.01\""))
        assertTrue(hit("val s = \"audio/eat.spoon.12.mp3\""))
        assertTrue(!hit("val s = \"xdrink.cup.01\""))
        assertTrue(!hit("// drink.cup.01 in a comment"))
    }

    companion object {
        val STRING_LITERAL = Regex(""""(?:[^"\\]|\\.)*"""")
        val ID =
            Regex("""\b(?:(?:dress|eat|drink|hygiene)\.[a-z_]+|(?:mild|moderate|severe)\.(?:rom|coord|hand|midline|planning))\.\d{2}\b""")
    }
}
