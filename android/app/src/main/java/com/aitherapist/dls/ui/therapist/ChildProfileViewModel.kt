package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.data.ChildEntity
import com.aitherapist.dls.data.ChildProfiles
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Add/edit one child profile and delete it (with confirmation). The only writer of the `child` table. */
@HiltViewModel
class ChildProfileViewModel
internal constructor(
    private val children: ChildProfiles,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long,
    private val newId: () -> String
) : ViewModel() {
    @Inject constructor(children: ChildProfiles) :
        this(children, Dispatchers.IO, System::currentTimeMillis, { UUID.randomUUID().toString() })

    private val state = MutableStateFlow<ChildProfileUiState>(ChildProfileUiState.Loading)
    val uiState: StateFlow<ChildProfileUiState> = state.asStateFlow()

    private var loadedId: String? = null
    private var loadedOnce = false
    private var original: ChildEntity? = null
    private var startedNew = false

    /** Call once the shell has handled [ChildProfileUiState.Content.result]; a "new" form starts blank again. */
    fun consumeResult() {
        val current = state.value as? ChildProfileUiState.Content ?: return
        if (current.result == null) return
        if (startedNew) {
            loadedId = null
            original = null
            state.value = ChildProfileUiState.Content(childId = null)
        } else {
            state.value = current.copy(result = null)
        }
    }

    /** Loads [childId] (null = new child). Repeated calls with the same id are ignored. */
    fun load(childId: String?) {
        if (loadedOnce && childId == loadedId) return
        loadedOnce = true
        loadedId = childId
        startedNew = childId == null
        fetch()
    }

    fun onEvent(event: ChildProfileEvent) {
        if (event == ChildProfileEvent.Retry) {
            if (state.value is ChildProfileUiState.Error) fetch()
            return
        }
        val current = state.value as? ChildProfileUiState.Content ?: return
        if (current.busy) return
        when (event) {
            is ChildProfileEvent.NameChanged -> editForm(current) { it.copy(name = event.value) }
            is ChildProfileEvent.CodeChanged -> editForm(current) { it.copy(code = event.value) }
            is ChildProfileEvent.AgeChanged -> editForm(current) { it.copy(age = event.value) }
            is ChildProfileEvent.CharacterChanged -> editForm(current) { it.copy(character = event.value) }
            is ChildProfileEvent.EffectChanged -> editForm(current) { it.copy(effectLevel = event.value) }
            ChildProfileEvent.Save -> save(current)
            ChildProfileEvent.RequestDelete -> if (!current.isNew) state.value = current.copy(confirmDelete = true)
            ChildProfileEvent.CancelDelete -> state.value = current.copy(confirmDelete = false)
            ChildProfileEvent.ConfirmDelete -> if (current.confirmDelete) delete(current)
            ChildProfileEvent.Retry -> Unit
        }
    }

    private fun editForm(current: ChildProfileUiState.Content, change: (ChildForm) -> ChildForm) {
        state.value = current.copy(form = change(current.form), errors = ChildFormErrors(), result = null, saved = false, message = null)
    }

    private fun fetch() {
        val id = loadedId
        original = null
        if (id == null) {
            state.value = ChildProfileUiState.Content(childId = null)
            return
        }
        state.value = ChildProfileUiState.Loading
        guarded {
            val child = withContext(io) { children.get(id) }
            if (child == null || child.isEval) {
                state.value = ChildProfileUiState.Error("not_found")
                return@guarded
            }
            original = child
            state.value =
                ChildProfileUiState.Content(
                    childId = id,
                    form = ChildForm(child.name, child.code, child.ageYears.toString(), child.characterVariant, child.effectLevel)
                )
        }
    }

    private fun save(current: ChildProfileUiState.Content) {
        val form = current.form
        val basic = ChildValidation.validate(form)
        if (!basic.isEmpty) {
            state.value = current.copy(errors = basic)
            return
        }
        state.value = current.copy(busy = true, message = null)
        guarded(onError = { e -> writeFailed(current, e, ProfileMessage.SAVE_FAILED) }) {
            val code = ChildValidation.normalizeCode(form.code)
            val inUse = withContext(io) { children.codeExists(code, current.childId) }
            val errors = ChildValidation.validate(form, codeInUse = inUse)
            if (!errors.isEmpty) {
                state.update { if (it is ChildProfileUiState.Content) it.copy(errors = errors, busy = false) else it }
                return@guarded
            }
            val saved = persist(form, code)
            original = saved
            loadedId = saved.id
            state.value =
                ChildProfileUiState.Content(
                    childId = saved.id,
                    form = form.copy(name = saved.name, code = saved.code, age = saved.ageYears.toString()),
                    saved = true,
                    result = ProfileResult.Saved(saved.id)
                )
        }
    }

    private suspend fun persist(form: ChildForm, code: String): ChildEntity {
        val age = requireNotNull(ChildValidation.parseAge(form.age))
        val character = requireNotNull(form.character)
        val existing = original
        return if (existing == null) {
            ChildEntity(
                id = newId(),
                name = form.name.trim(),
                code = code,
                ageYears = age,
                effectLevel = form.effectLevel,
                characterVariant = character,
                createdAt = clock()
            ).also { withContext(io) { children.add(it) } }
        } else {
            existing
                .copy(name = form.name.trim(), code = code, ageYears = age, effectLevel = form.effectLevel, characterVariant = character)
                .also { withContext(io) { children.update(it) } }
        }
    }

    private fun delete(current: ChildProfileUiState.Content) {
        val id = current.childId ?: return
        state.value = current.copy(busy = true, confirmDelete = false, message = null)
        guarded(onError = { e -> writeFailed(current, e, ProfileMessage.DELETE_FAILED) }) {
            withContext(io) { children.delete(id) }
            original = null
            state.value = ChildProfileUiState.Deleted
        }
    }

    /** A failed write keeps the typed form; a unique-code violation (race) becomes the code field error. */
    private fun writeFailed(current: ChildProfileUiState.Content, e: Exception, message: ProfileMessage) {
        state.value =
            if (e::class.simpleName == "SQLiteConstraintException") {
                current.copy(busy = false, confirmDelete = false, errors = ChildFormErrors(code = ChildFieldError.CODE_IN_USE))
            } else {
                current.copy(busy = false, confirmDelete = false, message = message)
            }
    }

    /** Never crash; the reason is the exception type only (messages may carry data). */
    private fun guarded(
        onError: (Exception) -> Unit = { e -> state.value = ChildProfileUiState.Error(e::class.simpleName ?: "unknown") },
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                onError(e)
            }
        }
    }
}
