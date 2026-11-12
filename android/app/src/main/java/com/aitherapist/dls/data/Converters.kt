package com.aitherapist.dls.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Room converters: enums as lower_snake text, `framePaths` as a JSON array. */
class Converters {
    @TypeConverter fun fromEffectLevel(v: EffectLevel): String = v.storageName()

    @TypeConverter fun toEffectLevel(v: String): EffectLevel = enumFromStorage(v)

    @TypeConverter fun fromCharacter(v: ChildCharacter): String = v.storageName()

    @TypeConverter fun toCharacter(v: String): ChildCharacter = enumFromStorage(v)

    @TypeConverter fun fromSessionKind(v: SessionKind): String = v.storageName()

    @TypeConverter fun toSessionKind(v: String): SessionKind = enumFromStorage(v)

    @TypeConverter fun fromSessionState(v: SessionState): String = v.storageName()

    @TypeConverter fun toSessionState(v: String): SessionState = enumFromStorage(v)

    @TypeConverter fun fromVerdict(v: AttemptVerdict): String = v.storageName()

    @TypeConverter fun toVerdict(v: String): AttemptVerdict = enumFromStorage(v)

    @TypeConverter fun fromVerifier(v: VerifierKind): String = v.storageName()

    @TypeConverter fun toVerifier(v: String): VerifierKind = enumFromStorage(v)

    @TypeConverter fun fromPaths(v: List<String>): String = Json.encodeToString(PATHS, v)

    @TypeConverter fun toPaths(v: String): List<String> = Json.decodeFromString(PATHS, v)

    private companion object {
        val PATHS = ListSerializer(String.serializer())
    }
}
