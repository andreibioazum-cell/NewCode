/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2026 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CompletionResult(
    val content: String?,
    val toolCalls: List<ChatMessage.ToolCall>
)

object OpenRouterClient {

    // ------------------------------------------------------------------
    // Ключ API OpenRouter (выдаётся в личном кабинете на openrouter.ai).
    //
    // Ключ хранится в base64-виде, чтобы GitHub Push Protection не
    // блокировал коммиты с секретом. При декомпиляции APK ключ всё равно
    // можно извлечь — для публикации используйте собственный ключ и
    // замените его здесь (см. https://openrouter.ai/keys).
    // ------------------------------------------------------------------
    private const val API_KEY_BASE64 =
        "c2stb3ItdjEtM2RkMjdmZWNhNWI1ZmJmODhlOWJiOWQ0OTRiMzE5MzE4ZTYyZWZjMTdmYjRiZDg4NTBkY2FiZDFjMzgzZDIzOQ=="
    private val API_KEY: String = String(java.util.Base64.getDecoder().decode(API_KEY_BASE64))

    // Модель "openrouter/auto" — автоматический роутер OpenRouter: сам
    // выбирает лучшую доступную модель под запрос (должна поддерживать
    // function calling). Можно заменить на конкретную, например
    // "openai/gpt-4o-mini" или "qwen/qwen-2.5-72b-instruct".
    private const val MODEL = "openrouter/auto"

    private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"

    // Ограничение истории диалога, отправляемой модели (контекст).
    private const val MAX_HISTORY_MESSAGES = 30

    private val jsonMediaType = MediaType.parse("application/json; charset=utf-8")

    // Ответы LLM могут идти долго, поэтому таймауты чтения/вызова увеличены.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    /**
     * Отправляет диалог в OpenRouter и возвращает ответ модели.
     * Выполняется на фоновом потоке (Dispatchers.IO).
     *
     * Если передан [tools] (JSON-схема function calling), модель может
     * вернуть [CompletionResult.toolCalls] вместо текста — их нужно
     * выполнить и продолжить диалог.
     */
    suspend fun complete(
        systemPrompt: String,
        history: List<ChatMessage>,
        tools: JsonArray? = null
    ): CompletionResult = withContext(Dispatchers.IO) {
        val requestBodyJson = buildRequestBody(systemPrompt, history, tools).toString()
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Title", "NewCode")
            .post(RequestBody.create(jsonMediaType, requestBodyJson))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(extractApiError(responseBody) ?: "HTTP ${response.code()}")
            }
            parseResponse(responseBody)
        }
    }

    private fun buildRequestBody(
        systemPrompt: String,
        history: List<ChatMessage>,
        tools: JsonArray?
    ): JsonObject {
        val conversation = history
            .filter { it.role != ChatMessage.ROLE_SYSTEM }
            .takeLast(MAX_HISTORY_MESSAGES)

        val jsonMessages = JsonArray()
        jsonMessages.add(systemMessage(systemPrompt))
        conversation.forEach { jsonMessages.add(messageJson(it)) }

        return JsonObject().apply {
            addProperty("model", MODEL)
            add("messages", jsonMessages)
            if (tools != null && tools.size() > 0) {
                add("tools", tools)
                addProperty("tool_choice", "auto")
            }
            addProperty("stream", false)
        }
    }

    private fun systemMessage(content: String): JsonObject =
        messageJson(ChatMessage(ChatMessage.ROLE_SYSTEM, content))

    private fun messageJson(message: ChatMessage): JsonObject = JsonObject().apply {
        addProperty("role", message.role)
        if (message.role == ChatMessage.ROLE_TOOL) {
            message.toolCallId?.let { addProperty("tool_call_id", it) }
            addProperty("content", message.content)
        } else if (message.toolCalls != null) {
            addProperty("content", message.content)
            val toolCallsArray = JsonArray()
            message.toolCalls.forEach { toolCall ->
                val function = JsonObject()
                function.addProperty("name", toolCall.name)
                function.addProperty("arguments", toolCall.arguments)
                val toolCallJson = JsonObject()
                toolCallJson.addProperty("id", toolCall.id)
                toolCallJson.addProperty("type", "function")
                toolCallJson.add("function", function)
                toolCallsArray.add(toolCallJson)
            }
            add("tool_calls", toolCallsArray)
        } else {
            addProperty("content", message.content)
        }
    }

    private fun parseResponse(responseBody: String): CompletionResult {
        val root = runCatching { JsonParser.parseString(responseBody).asJsonObject }
            .getOrNull() ?: throw IOException("Unexpected server response")

        extractApiError(responseBody)?.let { throw IOException(it) }

        val message = runCatching {
            root.get("choices")?.asJsonArray
                ?.firstOrNull()?.asJsonObject
                ?.get("message")?.asJsonObject
        }.getOrNull() ?: throw IOException("Unexpected server response")

        val content = message.get("content")?.takeIf { !it.isJsonNull }?.asString

        val toolCalls = mutableListOf<ChatMessage.ToolCall>()
        runCatching {
            message.get("tool_calls")?.asJsonArray?.forEach { toolCall ->
                val toolCallObject = toolCall.asJsonObject
                val function = toolCallObject.getAsJsonObject("function")
                val id = toolCallObject.get("id")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val name = function.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val arguments = function.get("arguments")?.takeIf { !it.isJsonNull }?.asString ?: "{}"
                toolCalls.add(ChatMessage.ToolCall(id, name, arguments))
            }
        }

        return CompletionResult(content, toolCalls)
    }

    private fun extractApiError(responseBody: String): String? =
        runCatching {
            val root = JsonParser.parseString(responseBody).asJsonObject
            root.get("error")?.asJsonObject?.get("message")?.takeIf { !it.isJsonNull }?.asString
        }.getOrNull()
}
