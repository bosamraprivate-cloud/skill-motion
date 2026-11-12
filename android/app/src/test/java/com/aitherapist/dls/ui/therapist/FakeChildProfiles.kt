package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.ChildEntity
import com.aitherapist.dls.data.ChildProfiles
import com.aitherapist.dls.data.normalizeChildCode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ChildProfiles] mirroring the DAO: eval hidden, ordered by name, code unique ignoring case. */
class FakeChildProfiles(vararg initial: ChildEntity) : ChildProfiles {
    val rows = MutableStateFlow(initial.toList())
    var failReads = false
    var failWrites: Exception? = null
    var deleted = mutableListOf<String>()

    override fun observeChildren(): Flow<List<ChildEntity>> = rows.map { list ->
        if (failReads) throw IOException("disk")
        list.filterNot { it.isEval }.sortedBy { it.name }
    }

    override suspend fun get(id: String): ChildEntity? {
        if (failReads) throw IOException("disk")
        return rows.value.firstOrNull { it.id == id }
    }

    override suspend fun add(child: ChildEntity) {
        failWrites?.let { throw it }
        check(!codeExists(child.code, null)) { "unique" }
        rows.value = rows.value + child
    }

    override suspend fun update(child: ChildEntity) {
        rows.value = rows.value.map { if (it.id == child.id) child else it }
    }

    override suspend fun codeExists(code: String, excludeId: String?): Boolean =
        rows.value.any { it.id != excludeId && it.code == normalizeChildCode(code) }

    override suspend fun delete(id: String) {
        failWrites?.let { throw it }
        deleted += id
        rows.value = rows.value.filterNot { it.id == id }
    }

    companion object {
        fun child(id: String, name: String, code: String, isEval: Boolean = false, character: ChildCharacter = ChildCharacter.GIRL) =
            ChildEntity(id = id, name = name, code = code, ageYears = 6, characterVariant = character, isEval = isEval, createdAt = 1L)
    }
}
