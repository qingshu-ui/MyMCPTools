package io.github.qingshu.mcpaudiotools.utils

import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class Args(
    private val values: List<String>,
) {
    operator fun component1() = values[0]
    operator fun component2() = values[1]
    operator fun component3() = values[2]
    operator fun component4() = values[3]
    operator fun component5() = values[4]
}

fun JsonObject?.requireArgs(vararg keys: String): Result<Args> {
    val missing = mutableListOf<String>()
    val values = mutableListOf<String>()

    for (key in keys) {
        val value = this?.get(key)?.jsonPrimitive?.content
        if (value.isNullOrEmpty()) {
            missing.add(key)
        } else {
            values.add(value)
        }
    }
    return if (missing.isEmpty()) {
        Result.success(Args(values))
    } else {
        Result.failure(IllegalArgumentException("Missing required arguments: ${missing.joinToString()}"))
    }
}

suspend fun ClientConnection.log(content: String, level: LoggingLevel = LoggingLevel.Info) {
    sendLoggingMessage(
        notification = LoggingMessageNotification(
            params = LoggingMessageNotificationParams(
                level = level,
                data = JsonPrimitive(content),
            ),
        ),
    )
}
