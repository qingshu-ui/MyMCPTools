package io.github.qingshu.essentialmcp.mcptool

import io.github.qingshu.essentialmcp.DEFAULT_VISION_API_URL
import io.github.qingshu.essentialmcp.DEFAULT_VISION_MODEL
import io.github.qingshu.essentialmcp.VISION_API_KEY
import io.github.qingshu.essentialmcp.VISION_API_URL
import io.github.qingshu.essentialmcp.VISION_MODEL
import io.github.qingshu.essentialmcp.getEnv
import io.github.qingshu.essentialmcp.httpClientEngine
import io.github.qingshu.mcptool.annotations.McpTool
import io.github.qingshu.mcptool.annotations.ToolParam
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

private val json = Json { ignoreUnknownKeys = true }

private val httpClient = lazy {
    HttpClient(httpClientEngine) {
        install(ContentNegotiation) { json(json) }
    }
}

@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens")
    val maxTokens: Int = 4096,
)

@Serializable
internal data class Message(
    val role: String,
    val content: List<ContentPart>,
)

@Serializable
internal data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url")
    val imageUrl: ImageUrl? = null,
)

@Serializable
internal data class ImageUrl(
    val url: String,
)

@Serializable
internal data class ChatResponse(
    val choices: List<Choice>,
)

@Serializable
internal data class Choice(
    val message: ChoiceMessage,
)

@Serializable
internal data class ChoiceMessage(
    val content: String,
)

private fun inferMimeType(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "image/png"
    }
}

@McpTool(
    name = "understand_image",
    description = """
        Analyze and understand an image using a vision model.
        Provide an image (as a URL, base64 data URI, or local file path) and a prompt describing what you want to know about the image.
        Returns the vision model's textual description or analysis of the image.
    """,
)
suspend fun understandImage(
    @ToolParam(description = "The image to analyze. Can be a URL (https://...), a base64 data URI (data:image/png;base64,...), or a local file path.")
    image: String,
    @ToolParam(description = "What you want to know about the image, e.g. 'Describe this image' or 'What text is shown?'")
    prompt: String,
): String {
    val apiKey = getEnv(VISION_API_KEY)
        ?: return "[Failed] VISION_API_KEY environment variable is not set"

    val baseUrl = getEnv(VISION_API_URL) ?: DEFAULT_VISION_API_URL
    val model = getEnv(VISION_MODEL) ?: DEFAULT_VISION_MODEL

    val imageUrl = when {
        image.startsWith("http") -> image

        image.startsWith("data:") -> image

        else -> {
            val bytes = SystemFileSystem.source(Path(image)).use { src ->
                src.buffered().readByteArray()
            }
            val mimeType = inferMimeType(image)
            "data:$mimeType;base64,${Base64.encode(bytes)}"
        }
    }

    val request = ChatRequest(
        model = model,
        messages = listOf(
            Message(
                role = "user",
                content = listOf(
                    ContentPart(type = "text", text = prompt),
                    ContentPart(type = "image_url", imageUrl = ImageUrl(url = imageUrl)),
                ),
            ),
        ),
    )

    return try {
        val response: ChatResponse = httpClient.value.post("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(request)
        }.body()
        val content = response.choices.firstOrNull()?.message?.content
        content ?: "[Failed] No content in vision model response"
    } catch (e: Exception) {
        "[Failed] Vision API request failed: ${e.message}"
    }
}
