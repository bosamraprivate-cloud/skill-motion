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

    /** Encrypts and writes [jpeg]; returns the file. */
    fun write(sessionId: String, attemptId: String, n: Int, jpeg: ByteArray): File {
        val target = file(sessionId, attemptId, n)
        val dir = checkNotNull(target.parentFile)
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeBytes(keys.encrypt(jpeg))
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("Frame write failed")
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

    /** Removes every frame of a session; true when nothing remains. */
    fun deleteSession(sessionId: String): Boolean {
        requireId(sessionId)
        return File(root, sessionId).deleteRecursively()
    }

    private fun requireId(id: String) = require(ID.matches(id)) { "Invalid frame id" }

    private companion object {
        val ID = Regex("[A-Za-z0-9-]+")
    }
}
