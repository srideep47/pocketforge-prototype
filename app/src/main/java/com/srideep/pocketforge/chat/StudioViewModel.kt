package com.srideep.pocketforge.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.srideep.pocketforge.agent.AgentLoop
import com.srideep.pocketforge.agent.AgentTools
import com.srideep.pocketforge.agent.AgentUpdate
import com.srideep.pocketforge.engine.mnn.MnnLlmEngine
import com.srideep.pocketforge.engine.mnn.ModelConfig
import com.srideep.pocketforge.runtime.node.DevServerClient
import com.srideep.pocketforge.voice.SpeechToText
import com.srideep.pocketforge.workspace.ProjectTemplates
import com.srideep.pocketforge.workspace.Workspace
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single state holder for the studio: chat + agent, the workspace files, and the dev
 * server. There is one model and one project on screen at a time, so one ViewModel keeps
 * the three tabs in step without an event bus between them.
 */
class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = MnnLlmEngine()
    private val devServer = DevServerClient(application)
    private val speech = SpeechToText(application)

    private val projectRoot = File(application.filesDir, "projects/site")
    private val workspace = Workspace(projectRoot)
    private val tools = AgentTools(workspace, devServer)
    private val agent = AgentLoop(engine, tools)

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    private var nextMessageId = 1L
    private var agentJob: Job? = null

    /** Where a model directory is expected: /Android/data/&lt;pkg&gt;/files/models/&lt;name&gt;. */
    private val modelsDir: File =
        File(application.getExternalFilesDir(null) ?: application.filesDir, "models")

    init {
        modelsDir.mkdirs()
        if (workspace.list().isEmpty()) {
            ProjectTemplates.starter("My Site").forEach { (path, content) ->
                workspace.write(path, content)
            }
        }
        refreshFiles()
        refreshModels()
        viewModelScope.launch {
            devServer.state.collect { server ->
                _state.value = _state.value.copy(
                    devServerRunning = server.running,
                    previewUrl = server.url ?: _state.value.previewUrl,
                )
            }
        }
    }

    // --- model -------------------------------------------------------------------

    fun refreshModels() {
        val models = modelsDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && File(it, "llm.mnn").isFile }
            .map { it.name }
            .sorted()
        _state.value = _state.value.copy(
            availableModels = models,
            modelStatus = if (models.isEmpty()) ModelStatus.MISSING else _state.value.modelStatus,
            status = if (models.isEmpty()) {
                "Put an exported MNN model in " + modelsDir.absolutePath + "/<name>/"
            } else {
                _state.value.status
            },
        )
    }

    fun loadModel(name: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                modelStatus = ModelStatus.LOADING,
                modelName = name,
                status = "Loading " + name,
            )
            val loaded = runCatching {
                engine.load(
                    ModelConfig(
                        modelDir = File(modelsDir, name),
                        tmpDir = File(getApplication<Application>().cacheDir, "mnn"),
                    ),
                )
            }.getOrElse { error ->
                Log.e(TAG, "model load failed", error)
                false
            }
            _state.value = _state.value.copy(
                modelStatus = if (loaded) ModelStatus.READY else ModelStatus.FAILED,
                status = if (loaded) name + " ready" else "Could not load " + name,
            )
        }
    }

    // --- chat --------------------------------------------------------------------

    fun onInputChange(value: String) {
        _state.value = _state.value.copy(input = value)
    }

    fun send() {
        val prompt = _state.value.input.trim()
        if (prompt.isEmpty() || _state.value.isGenerating) return
        if (_state.value.modelStatus != ModelStatus.READY) {
            appendSystem("Load a model first.")
            return
        }

        appendMessage(ChatMessage(id = nextMessageId++, role = Role.USER, text = prompt))
        val assistantId = nextMessageId++
        appendMessage(ChatMessage(id = assistantId, role = Role.ASSISTANT, streaming = true))
        _state.value = _state.value.copy(input = "", isGenerating = true)

        agentJob = viewModelScope.launch {
            try {
                agent.run(prompt).collect { update -> apply(assistantId, update) }
            } catch (e: Exception) {
                Log.e(TAG, "agent run failed", e)
                updateMessage(assistantId) { it.copy(text = it.text + "\n\n" + e.message) }
            } finally {
                updateMessage(assistantId) { it.copy(streaming = false) }
                _state.value = _state.value.copy(isGenerating = false)
                refreshFiles()
            }
        }
    }

    fun stopGeneration() {
        engine.stop()
        agentJob?.cancel()
    }

    fun clearConversation() {
        stopGeneration()
        viewModelScope.launch {
            engine.resetHistory()
            _state.value = _state.value.copy(messages = emptyList())
        }
    }

    private fun apply(assistantId: Long, update: AgentUpdate) {
        when (update) {
            is AgentUpdate.Token -> updateMessage(assistantId) { it.copy(text = it.text + update.text) }

            is AgentUpdate.ToolStarted -> updateMessage(assistantId) {
                it.copy(tools = it.tools + ToolTrace(update.name, update.summary))
            }

            is AgentUpdate.ToolFinished -> updateMessage(assistantId) { message ->
                val index = message.tools.indexOfLast { it.name == update.name && it.ok == null }
                if (index < 0) return@updateMessage message
                val updated = message.tools.toMutableList()
                updated[index] = updated[index].copy(ok = update.ok, detail = update.detail)
                message.copy(tools = updated)
            }

            is AgentUpdate.PreviewReady -> _state.value = _state.value.copy(
                previewUrl = update.url,
                devServerRunning = true,
                status = "Preview at " + update.url,
            )

            is AgentUpdate.Failed -> updateMessage(assistantId) {
                it.copy(text = it.text + "\n\n" + update.reason)
            }

            AgentUpdate.Done -> Unit
        }
    }

    // --- files -------------------------------------------------------------------

    fun refreshFiles() {
        _state.value = _state.value.copy(
            files = runCatching { workspace.listRecursively() }.getOrDefault(emptyList()),
        )
    }

    fun openFile(path: String) {
        val content = runCatching { workspace.read(path) }.getOrElse { "" }
        _state.value = _state.value.copy(openFile = OpenFile(path, content))
    }

    fun onEditorChange(content: String) {
        val open = _state.value.openFile ?: return
        _state.value = _state.value.copy(openFile = open.copy(content = content, dirty = true))
    }

    fun saveOpenFile() {
        val open = _state.value.openFile ?: return
        runCatching { workspace.write(open.path, open.content) }
            .onSuccess {
                _state.value = _state.value.copy(
                    openFile = open.copy(dirty = false),
                    status = "Saved " + open.path,
                )
            }
            .onFailure { appendSystem("Could not save " + open.path + ": " + it.message) }
        refreshFiles()
    }

    fun createFile(path: String) {
        if (path.isBlank()) return
        runCatching { workspace.write(path, "") }
            .onFailure { appendSystem("Could not create " + path + ": " + it.message) }
        refreshFiles()
        openFile(path)
    }

    fun deleteFile(path: String) {
        runCatching { workspace.delete(path) }
            .onFailure { appendSystem("Could not delete " + path + ": " + it.message) }
        if (_state.value.openFile?.path == path) {
            _state.value = _state.value.copy(openFile = null)
        }
        refreshFiles()
    }

    // --- dev server ---------------------------------------------------------------

    fun startDevServer() {
        viewModelScope.launch {
            val server = devServer.start(projectRoot)
            _state.value = _state.value.copy(
                status = server.error ?: ("Serving " + server.url),
                previewUrl = server.url ?: _state.value.previewUrl,
            )
        }
    }

    fun stopDevServer() {
        devServer.stop()
        _state.value = _state.value.copy(status = "Dev server stopped")
    }

    // --- dictation ------------------------------------------------------------------

    fun startDictation() {
        if (_state.value.isListening) {
            stopDictation()
            return
        }
        _state.value = _state.value.copy(isListening = true, status = "Listening")
        speech.start(object : SpeechToText.Listener {
            override fun onPartial(text: String) {
                _state.value = _state.value.copy(input = text)
            }

            override fun onFinal(text: String) {
                // Dictation fills the box; sending stays a deliberate tap.
                _state.value = _state.value.copy(input = text, isListening = false, status = "")
            }

            override fun onError(message: String) {
                _state.value = _state.value.copy(isListening = false, status = message)
            }

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(isListening = false)
            }
        })
    }

    fun stopDictation() {
        speech.stop()
        _state.value = _state.value.copy(isListening = false, status = "")
    }

    // --- plumbing -------------------------------------------------------------------

    private fun appendSystem(text: String) =
        appendMessage(ChatMessage(id = nextMessageId++, role = Role.SYSTEM, text = text))

    private fun appendMessage(message: ChatMessage) {
        _state.value = _state.value.copy(messages = _state.value.messages + message)
    }

    private fun updateMessage(id: Long, transform: (ChatMessage) -> ChatMessage) {
        _state.value = _state.value.copy(
            messages = _state.value.messages.map { if (it.id == id) transform(it) else it },
        )
    }

    override fun onCleared() {
        stopDictation()
        engine.stop()
        engine.shutdown()
        super.onCleared()
    }

    private companion object {
        const val TAG = "StudioViewModel"
    }
}
