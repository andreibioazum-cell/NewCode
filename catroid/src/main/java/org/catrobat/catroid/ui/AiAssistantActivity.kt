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
package org.catrobat.catroid.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.catrobat.catroid.ProjectManager
import org.catrobat.catroid.R
import org.catrobat.catroid.ai.AiProjectActions
import org.catrobat.catroid.ai.BrickCatalog
import org.catrobat.catroid.ai.ChatMessage
import org.catrobat.catroid.ai.OpenRouterClient
import org.catrobat.catroid.ai.ProjectSnapshot
import org.catrobat.catroid.databinding.ActivityAiAssistantBinding
import org.catrobat.catroid.databinding.ItemChatMessageBinding
import org.catrobat.catroid.io.asynctask.saveProjectSerial
import org.koin.android.ext.android.inject

class AiAssistantActivity : BaseActivity() {

    companion object {
        private const val SAVED_MESSAGES = "saved_messages"
        private const val MAX_TOOL_ROUNDS = 8
    }

    private lateinit var binding: ActivityAiAssistantBinding
    private val projectManager: ProjectManager by inject()
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val adapter = ChatAdapter()
    private var requestJob: Job? = null
    private var changesMade = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.ai_assistant_title)

        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = adapter

        binding.chatSendButton.setOnClickListener { sendMessage() }
        binding.chatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        restoreMessages(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        EdgeToEdge.applyBottomMargin(binding.chatInputBar)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Приветственное сообщение (role = system) в историю не сохраняем:
        // при восстановлении экрана оно добавляется заново.
        val history = adapter.messages.filter { it.role != ChatMessage.ROLE_SYSTEM }
        outState.putString(SAVED_MESSAGES, gson.toJson(history))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (changesMade > 0) {
            // Пересоздаём ProjectActivity, чтобы списки сцен/спрайтов
            // показали изменения, внесённые помощником.
            startActivity(
                Intent(this, ProjectActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_ai_assistant, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear_chat -> clearChat()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun restoreMessages(savedInstanceState: Bundle?) {
        val json = savedInstanceState?.getString(SAVED_MESSAGES)
        if (json.isNullOrEmpty()) {
            addWelcomeMessage()
            return
        }
        val type = object : TypeToken<List<ChatMessage>>() {}.type
        val restored: List<ChatMessage>? = gson.fromJson(json, type)
        if (restored.isNullOrEmpty()) {
            addWelcomeMessage()
        } else {
            adapter.messages.addAll(restored)
            adapter.notifyDataSetChanged()
        }
    }

    private fun addWelcomeMessage() {
        adapter.messages.add(ChatMessage(ChatMessage.ROLE_SYSTEM, getString(R.string.ai_welcome)))
        adapter.notifyItemInserted(adapter.messages.size - 1)
    }

    private fun clearChat() {
        requestJob?.cancel()
        adapter.isLoading = false
        adapter.clear()
        addWelcomeMessage()
    }

    private fun sendMessage() {
        if (requestJob?.isActive == true) {
            return
        }
        val text = binding.chatInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            return
        }
        binding.chatInput.setText("")
        adapter.addMessage(ChatMessage(ChatMessage.ROLE_USER, text))
        adapter.isLoading = true
        scrollToBottom()

        requestJob = scope.launch {
            val project = projectManager.currentProject
            if (project == null) {
                adapter.isLoading = false
                adapter.addMessage(
                    ChatMessage(ChatMessage.ROLE_ASSISTANT, getString(R.string.ai_no_project))
                )
                return@launch
            }
            val scene = projectManager.currentlyEditedScene ?: project.getDefaultScene()

            // История для API: обычные сообщения чата + сообщения tool-вызовов.
            // Сообщение пользователя уже добавлено в adapter.messages выше.
            val apiHistory = mutableListOf<ChatMessage>()
            apiHistory.addAll(adapter.messages)

            var finalContent: String? = null
            var roundChanges = 0
            var rounds = 0
            while (rounds < MAX_TOOL_ROUNDS) {
                rounds++
                val systemPrompt = getString(R.string.ai_system_prompt) + "\n\n" +
                    brickCatalogPrompt() + "\n\n" +
                    getString(R.string.ai_project_state) + "\n" + ProjectSnapshot.build(project)
                val response = runCatching {
                    OpenRouterClient.complete(systemPrompt, apiHistory, AiProjectActions.toolsJson)
                }.getOrElse {
                    if (!isActive) {
                        return@launch
                    }
                    adapter.isLoading = false
                    adapter.addMessage(
                        ChatMessage(
                            ChatMessage.ROLE_ASSISTANT,
                            getString(
                                R.string.ai_error,
                                it.message ?: getString(R.string.ai_error_generic)
                            )
                        )
                    )
                    return@launch
                }

                if (response.toolCalls.isEmpty()) {
                    finalContent = response.content
                    break
                }

                apiHistory.add(ChatMessage(ChatMessage.ROLE_ASSISTANT, "", toolCalls = response.toolCalls))
                for (toolCall in response.toolCalls) {
                    val resultJson = AiProjectActions.execute(
                        applicationContext, project, scene, toolCall.name, toolCall.arguments
                    )
                    apiHistory.add(
                        ChatMessage(ChatMessage.ROLE_TOOL, resultJson, toolCallId = toolCall.id)
                    )
                    val summary = actionSummary(resultJson, toolCall.name)
                    if (resultJson.contains("\"ok\":true")) {
                        roundChanges++
                    }
                    adapter.addMessage(ChatMessage(ChatMessage.ROLE_ASSISTANT, "⚙️ $summary"))
                }
                scrollToBottom()
            }

            if (!isActive) {
                return@launch
            }
            adapter.isLoading = false
            adapter.addMessage(
                ChatMessage(
                    ChatMessage.ROLE_ASSISTANT,
                    finalContent ?: getString(R.string.ai_rounds_exhausted)
                )
            )
            if (roundChanges > 0) {
                changesMade += roundChanges
                val saved = withContext(Dispatchers.IO) {
                    saveProjectSerial(project, applicationContext)
                }
                if (saved) {
                    adapter.addMessage(
                        ChatMessage(
                            ChatMessage.ROLE_ASSISTANT,
                            getString(R.string.ai_changes_saved, roundChanges)
                        )
                    )
                }
            }
            scrollToBottom()
        }
    }

    /** Компактный список ВСЕХ блоков репозитория для промпта модели. */
    private fun brickCatalogPrompt(): String {
        val bricks = BrickCatalog.allBricks(applicationContext)
        if (bricks.isEmpty()) {
            return getString(R.string.ai_brick_catalog_header) + ": (недоступен)"
        }
        val builder = StringBuilder()
        builder.append(getString(R.string.ai_brick_catalog_header)).append(":\n")
        bricks.forEach { spec ->
            val params = if (spec.constructors.isNotEmpty()) {
                spec.constructors.joinToString(" | ") { it }
            } else {
                ""
            }
            builder.append("- ").append(spec.className)
            if (params.isNotEmpty()) {
                builder.append(" (").append(params).append(")")
            }
            builder.append("\n")
        }
        return builder.toString()
    }

    private fun actionSummary(resultJson: String, actionName: String): String = runCatching {
        val obj = JsonParser.parseString(resultJson).asJsonObject
        val ok = obj.get("ok")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        if (ok) {
            obj.get("summary")?.takeIf { !it.isJsonNull }?.asString ?: "Выполнено: $actionName"
        } else {
            "Ошибка: " + (obj.get("error")?.takeIf { !it.isJsonNull }?.asString ?: actionName)
        }
    }.getOrDefault("Выполнено: $actionName")

    private fun scrollToBottom() {
        binding.chatRecyclerView.post {
            if (adapter.itemCount > 0) {
                binding.chatRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

        val messages = mutableListOf<ChatMessage>()

        var isLoading: Boolean = false
            set(value) {
                if (field == value) {
                    return
                }
                field = value
                if (value) {
                    notifyItemInserted(messages.size)
                } else {
                    notifyItemRemoved(messages.size)
                }
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val itemBinding = ItemChatMessageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ChatViewHolder(itemBinding)
        }

        override fun getItemCount(): Int = messages.size + if (isLoading) 1 else 0

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val itemBinding = holder.binding
            if (position >= messages.size) {
                bindLoading(itemBinding)
                return
            }
            bindMessage(itemBinding, messages[position])
        }

        private fun bindLoading(itemBinding: ItemChatMessageBinding) {
            itemBinding.chatMessageContainer.gravity = Gravity.START
            itemBinding.chatMessageText.setBackgroundResource(R.drawable.chat_bubble_assistant)
            itemBinding.chatMessageText.text =
                itemBinding.root.context.getString(R.string.ai_thinking)
        }

        private fun bindMessage(itemBinding: ItemChatMessageBinding, message: ChatMessage) {
            val isUser = message.role == ChatMessage.ROLE_USER
            val isAction = message.content.startsWith("⚙️ ")
            itemBinding.chatMessageContainer.gravity = if (isUser) Gravity.END else Gravity.START
            itemBinding.chatMessageText.setBackgroundResource(
                when {
                    isUser -> R.drawable.chat_bubble_user
                    isAction -> R.drawable.chat_bubble_action
                    else -> R.drawable.chat_bubble_assistant
                }
            )
            itemBinding.chatMessageText.text = message.content
        }

        fun addMessage(message: ChatMessage) {
            messages.add(message)
            notifyItemInserted(messages.size - 1)
        }

        fun clear() {
            messages.clear()
            notifyDataSetChanged()
        }

        class ChatViewHolder(val binding: ItemChatMessageBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
