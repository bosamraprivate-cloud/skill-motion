package com.aitherapist.dls.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What replay mode reads beyond the runner's [SessionStore] (Story 3.7; implemented by [ReplayRepository]). */
interface ReplayStore {
    /** The hidden eval child's id; the child is created once on first use and never listed. */
    suspend fun evalChildId(): String

    /** The latest replay session, a finished (`done`) one preferred; null when none. */
    suspend fun latestReplaySession(): SessionEntity?

    /** All attempts of [sessionId] in saved order. */
    suspend fun attempts(sessionId: String): List<AttemptEntity>
}

/** Replay sessions and the hidden eval child. */
@Singleton
class ReplayRepository
@Inject
internal constructor(private val holder: DatabaseHolder) : ReplayStore {
    override suspend fun evalChildId(): String {
        val dao = holder.get().childDao()
        dao.evalChild()?.let { return it.id }
        val child =
            ChildEntity(
                id = UUID.randomUUID().toString(),
                name = EVAL_NAME,
                code = EVAL_CODE,
                ageYears = 0,
                characterVariant = ChildCharacter.BOY,
                isEval = true,
                createdAt = System.currentTimeMillis()
            )
        return try {
            dao.insert(child)
            child.id
        } catch (_: SQLiteConstraintException) {
            // Created concurrently: use the stored one.
            checkNotNull(dao.evalChild()).id
        }
    }

    override suspend fun latestReplaySession(): SessionEntity? = holder.get().sessionDao().replaySessions().firstOrNull()

    override suspend fun attempts(sessionId: String): List<AttemptEntity> = holder.get().attemptDao().forSession(sessionId)

    private companion object {
        const val EVAL_NAME = "Eval"
        const val EVAL_CODE = "EVAL"
    }
}

/** Imported replay clips in app-private storage, so Re-score works offline (Story 3.7). */
interface ClipFiles {
    /** Copies [input] to the clip named [name] (replacing it); returns the stored file. */
    fun save(name: String, input: InputStream): File

    /** The stored clip [name] (may not exist). */
    fun file(name: String): File
}

/** [ClipFiles] under `files/replay_clips/`. Names must be plain file names. Logs nothing. */
class ClipStore(private val filesDir: File) : ClipFiles {
    @Inject constructor(@ApplicationContext context: Context) : this(context.filesDir)

    private val root get() = File(filesDir, "replay_clips")

    override fun save(name: String, input: InputStream): File {
        val target = file(name)
        root.mkdirs()
        val tmp = File(root, "$name.tmp")
        try {
            tmp.outputStream().use { input.copyTo(it) }
            // Replaces in one step: an older clip of the same name stays until the new copy is complete.
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        return target
    }

    override fun file(name: String): File {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != "..") { "Invalid clip name" }
        return File(root, name)
    }
}
