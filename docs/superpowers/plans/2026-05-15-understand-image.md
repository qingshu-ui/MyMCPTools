# Understand Image MCP Tool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `understand_image` MCP tool that sends images to an OpenAI-compatible vision API and returns the result.

**Architecture:** New `UnderstandImage.kt` annotated with `@McpTool` uses Ktor client for HTTP calls and `runProcess("base64", path)` for local file encoding. Registration in `Main.kt` is conditional on `VISION_API_KEY` env var. Remove the temporary `httpPostJson` expect/actual code.

**Tech Stack:** Ktor client (okhttp engine on JVM, curl engine on native), kotlinx.serialization, existing `runProcess`/`getEnv` platform functions.

---

### Task 1: Add Ktor dependencies to version catalog and build config

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `essential-mcp/build.gradle.kts`

- [ ] **Step 1: Add Ktor version and library entries to `gradle/libs.versions.toml`**

Add under `[versions]`:
```toml
ktor = "3.4.1"
```

Add under `[libraries]`:
```toml
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-curl = { module = "io.ktor:ktor-client-curl", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
```

- [ ] **Step 2: Add Ktor dependencies to `essential-mcp/build.gradle.kts` source sets**

In the `sourceSets` block, add to `commonMain.dependencies`:
```kotlin
implementation(libs.ktor.client.core)
implementation(libs.ktor.client.content.negotiation)
implementation(libs.ktor.serialization.json)
```

Add a `jvmMain` source set dependency block (create if not existing):
```kotlin
jvmMain.dependencies {
    implementation(libs.ktor.client.okhttp)
}
```

Add a `nativeMain` source set dependency block (create if not existing):
```kotlin
nativeMain.dependencies {
    implementation(libs.ktor.client.curl)
}
```

- [ ] **Step 3: Run `./gradlew :essential-mcp:dependencies --configuration commonMainCompileKlibraries` to verify dependencies resolve**

Expected: no errors, Ktor libraries appear in the dependency tree.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml essential-mcp/build.gradle.kts
git commit -m "feat: add Ktor client dependencies for image understanding tool"
```

---

### Task 2: Remove temporary `httpPostJson` expect/actual code

**Files:**
- Modify: `essential-mcp/src/commonMain/kotlin/Platform.kt`
- Modify: `essential-mcp/src/jvmMain/kotlin/Platform.jvm.kt`
- Modify: `essential-mcp/src/nativeMain/kotlin/Platform.native.kt`

- [ ] **Step 1: Remove `httpPostJson` expect declaration from `Platform.kt`**

Remove this line:
```kotlin
expect suspend fun httpPostJson(url: String, headers: Map<String, String>, body: String): String
```

- [ ] **Step 2: Remove `httpPostJson` actual implementation from `Platform.jvm.kt`**

Remove the entire `actual suspend fun httpPostJson(...)` function and its unused imports (`java.net.http.*`).

- [ ] **Step 3: Remove `httpPostJson` actual implementation from `Platform.native.kt`**

Remove the entire `actual suspend fun httpPostJson(...)` function.

- [ ] **Step 4: Commit**

```bash
git add essential-mcp/src/commonMain/kotlin/Platform.kt essential-mcp/src/jvmMain/kotlin/Platform.jvm.kt essential-mcp/src/nativeMain/kotlin/Platform.native.kt
git commit -m "refactor: remove temporary httpPostJson expect/actual, replaced by Ktor client"
```

---

### Task 3: Rewrite `UnderstandImage.kt` with Ktor client

**Files:**
- Modify: `essential-mcp/src/commonMain/kotlin/mcptool/UnderstandImage.kt`

- [ ] **Step 1: Rewrite `UnderstandImage.kt`**

Replace the entire file with:

```kotlin
package io.github.qingshu.essentialmcp.mcptool

import io.github.qingshu.essentialmcp.getEnv
import io.github.qingshu.essentialmcp.runProcess
import io.github.qingshu.mcptool.annotations.McpTool
import io.github.qingshu.mcptool.annotations.ToolParam
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    val max_tokens: Int = 4096,
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
    val image_url: ImageUrl? = null,
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

        Configuration via environment variables:
        - VISION_API_KEY: API key for the vision model service (required)
        - VISION_API_URL: Base URL for the API (default: https://api.openai.com/v1)
        - VISION_MODEL: Model name to use (default: gpt-4o)
    """,
)
suspend fun understandImage(
    @ToolParam(description = "The image to analyze. Can be a URL (https://...), a base64 data URI (data:image/png;base64,...), or a local file path.")
    image: String,
    @ToolParam(description = "What you want to know about the image, e.g. 'Describe this image' or 'What text is shown?'")
    prompt: String,
): String {
    val apiKey = getEnv("VISION_API_KEY")
        ?: return "[Failed] VISION_API_KEY environment variable is not set"

    val baseUrl = getEnv("VISION_API_URL") ?: "https://api.openai.com/v1"
    val model = getEnv("VISION_MODEL") ?: "gpt-4o"

    val imageUrl = when {
        image.startsWith("http") -> image
        image.startsWith("data:") -> image
        else -> {
            val result = runProcess("base64", image)
            if (!result.isSuccess) {
                return "[Failed] Failed to read file '$image': ${result.output}"
            }
            val mimeType = inferMimeType(image)
            "data:$mimeType;base64,${result.output.trim()}"
        }
    }

    val request = ChatRequest(
        model = model,
        messages = listOf(
            Message(
                role = "user",
                content = listOf(
                    ContentPart(type = "text", text = prompt),
                    ContentPart(type = "image_url", image_url = ImageUrl(url = imageUrl)),
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
```

- [ ] **Step 2: Commit**

```bash
git add essential-mcp/src/commonMain/kotlin/mcptool/UnderstandImage.kt
git commit -m "feat: rewrite understandImage tool with Ktor client and file path support"
```

---

### Task 4: Add `httpClientEngine` expect/actual

**Files:**
- Modify: `essential-mcp/src/commonMain/kotlin/Platform.kt`
- Modify: `essential-mcp/src/jvmMain/kotlin/Platform.jvm.kt`
- Modify: `essential-mcp/src/nativeMain/kotlin/Platform.native.kt`

- [ ] **Step 1: Add expect declaration to `Platform.kt`**

Add after the existing `getEnv` declaration:
```kotlin
expect val httpClientEngine: HttpClientEngine
```

Add import at top:
```kotlin
import io.ktor.client.engine.HttpClientEngine
```

- [ ] **Step 2: Add JVM actual to `Platform.jvm.kt`**

Add:
```kotlin
import io.ktor.client.engine.okhttp.OkHttp

actual val httpClientEngine: HttpClientEngine = OkHttp.create()
```

- [ ] **Step 3: Add native actual to `Platform.native.kt`**

Add:
```kotlin
import io.ktor.client.engine.curl.Curl

actual val httpClientEngine: HttpClientEngine = Curl.create()
```

- [ ] **Step 4: Commit**

```bash
git add essential-mcp/src/commonMain/kotlin/Platform.kt essential-mcp/src/jvmMain/kotlin/Platform.jvm.kt essential-mcp/src/nativeMain/kotlin/Platform.native.kt
git commit -m "feat: add httpClientEngine expect/actual for Ktor (OkHttp JVM, Curl native)"
```

---

### Task 5: Update `Main.kt` for conditional registration

**Files:**
- Modify: `essential-mcp/src/commonMain/kotlin/Main.kt`

- [ ] **Step 1: Replace `registerGeneratedMcpTools()` with individual calls**

Change the `McpServer` block from:
```kotlin
registerGeneratedMcpTools()
```

To:
```kotlin
registerExecuteCommandTool()
registerSubtitleToLrcTool()
registerTranscodeWavToMp3Tool()
if (getEnv("VISION_API_KEY") != null) {
    registerUnderstandImageTool()
}
```

Add the import for `registerUnderstandImageTool` and the other individual functions if they're not already imported. The generated functions are in `io.github.qingshu.mcptool.generated`:
```kotlin
import io.github.qingshu.mcptool.generated.registerExecuteCommandTool
import io.github.qingshu.mcptool.generated.registerSubtitleToLrcTool
import io.github.qingshu.mcptool.generated.registerTranscodeWavToMp3Tool
import io.github.qingshu.mcptool.generated.registerUnderstandImageTool
```

Remove the old import:
```kotlin
import io.github.qingshu.mcptool.generated.registerGeneratedMcpTools
```

- [ ] **Step 2: Commit**

```bash
git add essential-mcp/src/commonMain/kotlin/Main.kt
git commit -m "feat: register understand_image tool conditionally based on VISION_API_KEY"
```

---

### Task 6: Build and verify

- [ ] **Step 1: Run `./gradlew spotlessApply`**

- [ ] **Step 2: Run `./gradlew :essential-mcp:jvmJar`**

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit any formatting changes if spotless modified files**

```bash
git add -A
git commit -m "style: apply spotless formatting"
```
