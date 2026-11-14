package com.aitherapist.dls.data

import java.io.File
import javax.crypto.AEADBadTagException

/**
 * Encrypted evidence frames (design §Encryption): `files/frames/<sessionId>/<attemptId>-<n>.bin`,
 * each IV(12) || AES-GCM(JPEG) with the `dls_master` key. Ids must match `[A-Za-z0-9-]+`.
 */
class FrameStore internal constructor(private val filesDir: File, private val keys: KeyManager) {
    private val root get() = File(filesDir, "frames")

    fun file(sessionId: String, attemptId: String, n: Int): File {
        requireId(sessionId)
        requireId(attemptId)
        require(n >= 0) { "Frame index must be >= 0" }
        return File(File(root, sessionId), "$attemptId-$n.bin")
    }

    /** The frame's path relative to the app files dir, as stored in `attempt.framePaths`. */
    fun relativePath(sessionId: String, attemptId: String, n: Int): String =
        file(sessionId, attemptId, n).relativeTo(filesDir).invariantSeparatorsPath

    /** Encrypts and writes [jpeg]; returns the file. */
    fun write(sessionId: String, attemptId: String, n: Int, jpeg: ByteArray): File {
        val target = file(sessionId, attemptId, n)
        val dir = checkNotNull(target.parentFile)
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        try {
            tmp.writeBytes(keys.encrypt(jpeg))
            check(tmp.renameTo(target)) { "Frame write failed" }
        } catch (e: Exception) {
            // No partial file is left behind.
            tmp.delete()
            target.delete()
            throw e
        }
        return target
    }

    /**
     * Decrypted JPEG bytes, or null when the frame does not exist. Throws `AEADBadTagException` if the file was
     * tampered with or truncated; never returns partial bytes.
     */
    fun read(sessionId: String, attemptId: String, n: Int): ByteArray? {
        val f = file(sessionId, attemptId, n)
        if (!f.isFile) return null
        val data = f.readBytes()
        if (data.size <= KeyManager.IV_BYTES) throw AEADBadTagException("Frame too short")
        return keys.decrypt(data)
    }

    /**
     * Decrypted JPEG bytes of a stored relative path (`frames/<sessionId>/<attemptId>-<n>.bin`), or null when the
     * path is malformed, outside the frames folder, missing or fails to decrypt. Never throws, never logs.
     */
    fun readRelative(path: String): ByteArray? {
        val match = RELATIVE.matchEntire(path) ?: return null
        val (sessionId, attemptId, n) = match.destructured
        return runCatching { read(sessionId, attemptId, n.toInt()) }.getOrNull()
    }

    /** Whether the frame at a stored relative path exists on disk (no decryption); false for a malformed path. */
    fun existsRelative(path: String): Boolean {
        val match = RELATIVE.matchEntire(path) ?: return false
        val (sessionId, attemptId, n) = match.destructured
        return runCatching { file(sessionId, attemptId, n.toInt()).isFile }.getOrDefault(false)
    }

    /** Deletes one frame by its stored relative path; only paths under the frames folder are touched. */
    fun deleteRelative(path: String): Boolean {
        val f = File(filesDir, path).canonicalFile
        if (!f.path.startsWith(root.canonicalFile.path + File.separator)) return false
        return !f.exists() || f.delete()
    }

    /** Removes every frame of a session; true when nothing remains. */
    fun deleteSession(sessionId: String): Boolean {
        requireId(sessionId)
        return File(root, sessionId).deleteRecursively()
    }

    private fun requireId(id: String) = require(ID.matches(id)) { "Invalid frame id" }

    private companion object {
        val ID = Regex("[A-Za-z0-9-]+")
        val RELATIVE = Regex("frames/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)-([0-9]{1,4})\\.bin")
    }
}
