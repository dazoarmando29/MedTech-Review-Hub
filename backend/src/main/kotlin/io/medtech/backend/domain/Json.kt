package io.medtech.backend.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
    prettyPrint = false
}

inline fun <reified T> decodeJson(text: String): T = AppJson.decodeFromString(text)

inline fun <reified T> encodeJson(value: T): String = AppJson.encodeToString(value)

/**
 * `preferredSchedule` maps weekday -> array of [startMinuteOfDay, endMinuteOfDay] windows.
 * Decoding is lenient: both {"mon": [[120, 1260]]} and the flat shorthand {"mon": [120, 1260]}
 * are accepted; encoding always emits the nested form.
 */
object ScheduleWindowsSerializer : KSerializer<Map<String, List<List<Int>>>> {
    private val delegate = MapSerializer(String.serializer(), ListSerializer(ListSerializer(Int.serializer())))
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): Map<String, List<List<Int>>> {
        val obj = decoder.decodeSerializableValue(JsonObject.serializer())
        return obj.mapValues { (_, value) ->
            val arr = value as? JsonArray ?: throw SerializationException("preferredSchedule values must be arrays")
            if (arr.isEmpty()) return@mapValues emptyList()
            val first = arr.first()
            if (first is JsonArray) {
                arr.map { window ->
                    val pair = window.jsonArray
                    if (pair.size != 2) throw SerializationException("each window must be [startMinute, endMinute]")
                    listOf(pair[0].jsonPrimitive.int, pair[1].jsonPrimitive.int)
                }
            } else {
                if (arr.size != 2) throw SerializationException("flat window must be [startMinute, endMinute]")
                listOf(listOf(arr[0].jsonPrimitive.int, arr[1].jsonPrimitive.int))
            }
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, List<List<Int>>>) {
        val obj = JsonObject(value.mapValues { (_, windows) ->
            JsonArray(windows.map { window -> JsonArray(window.map { JsonPrimitive(it) }) })
        })
        encoder.encodeSerializableValue(JsonObject.serializer(), obj)
    }
}
