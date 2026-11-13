package com.aitherapist.dls.catalog

import android.content.Context
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aitherapist.dls.data.AssetCatalogRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M2 content completeness check (Story 2.17, SM-4). Loads the shipped catalogue and opens and
 * decodes every referenced asset. The strict test is the M2 gate and is skipped while the
 * catalogue says `complete: false`.
 */
@RunWith(AndroidJUnit4::class)
class ContentCompletenessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun catalog(): Catalog = runBlocking { AssetCatalogRepository(context).load().getOrThrow() }

    @Test
    fun everyListedAssetExistsAndDecodes() {
        val catalog = catalog()
        val failures = mutableListOf<String>()
        var checked = 0
        for (step in catalog.steps) {
            step.audio?.let { audio ->
                listOf("audio.normal" to audio.normal, "audio.slow" to audio.slow).forEach { (label, path) ->
                    checked++
                    checkAudio(path)?.let { failures += "${step.id} [-] $label $path: $it" }
                }
            }
            for ((variant, media) in step.media) {
                if (media == null) continue
                checked++
                checkImage(media.still)?.let { failures += "${step.id} [$variant] still ${media.still}: $it" }
                media.clip?.let { clip ->
                    checked++
                    checkImage(clip)?.let { failures += "${step.id} [$variant] clip $clip: $it" }
                }
            }
        }
        assertTrue("${failures.size} of $checked assets failed:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun completeCatalogueHasNoMissingMediaOrAudio() {
        val catalog = catalog()
        val variants = catalog.steps.flatMap { it.media.keys }.distinct().sorted()
        val noAudio = catalog.steps.filter { it.audio == null }
        val noMedia = variants.associateWith { v -> catalog.steps.filter { it.media(v) == null } }
        val noClip = variants.associateWith { v -> catalog.steps.filter { it.media(v)?.clip == null } }
        val summary = buildString {
            append("steps=${catalog.steps.size}, missing audio=${noAudio.size}")
            variants.forEach { v -> append("; $v: missing visuals=${noMedia.getValue(v).size}, without clip=${noClip.getValue(v).size}") }
        }
        assumeTrue("catalogue complete=false, M2 gate skipped ($summary)", catalog.complete)
        val gaps = buildList {
            noAudio.forEach { add("${it.id} [-] audio") }
            noMedia.forEach { (v, steps) -> steps.forEach { add("${it.id} [$v] visuals") } }
            if (variants.isEmpty()) add("no character variants")
        }
        assertTrue("complete=true but gaps ($summary):\n" + gaps.joinToString("\n"), gaps.isEmpty())
    }

    /** Returns an error message, or null when the WebP decodes (animated or still). */
    private fun checkImage(path: String): String? = try {
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, path)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) null else "empty image"
    } catch (e: Exception) {
        e.javaClass.simpleName + ": " + e.message
    }

    /** Returns an error message, or null when the mp3 has an audio track with duration > 0. */
    private fun checkAudio(path: String): String? = try {
        context.assets.openFd(path).use { fd ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                val audio = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                when {
                    audio == null -> "no audio track"
                    !audio.containsKey(MediaFormat.KEY_DURATION) || audio.getLong(MediaFormat.KEY_DURATION) <= 0 -> "zero duration"
                    else -> null
                }
            } finally {
                extractor.release()
            }
        }
    } catch (e: Exception) {
        e.javaClass.simpleName + ": " + e.message
    }
}
