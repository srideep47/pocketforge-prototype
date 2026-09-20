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
import com.srideep.pocketforge.model.CatalogModel
import com.srideep.pocketforge.model.DownloadProgress
import com.srideep.pocketforge.model.ModelDownloader
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

    /** Totals across every turn of one agent run, for the throughput line. */
    private var runTokens = 0
    private var runDecodeMicros = 0L
    private var runPromptTokens = 0
    private var runPrefillMicros = 0L

    /** Downloads in flight, and the id of whatever the engine currently holds. */
    private val downloads = mutableMapOf<String, DownloadProgress>()
    private val downloadJobs = mutableMapOf<String, Job>()
    private var loadedModelId: String? = null

    /** Where a model directory is expected: /Android/data/&lt;pkg&gt;/files/models/&lt;name&gt;. */
    private val modelsDir: File =
        File(application.getExternalFilesDir(null) ?: application.filesDir, "models")
    private val downloader = ModelDownloader(modelsDir)

    init {
        modelsDir.mkdirs()
        // Seeded once, on first run only. Keying off "is the project empty" instead meant
        // New project wiped the files and the next launch put them straight back, which
        // both undoes the action and leaves the agent editing a page it did not write.
        val seededMarker = File(application.filesDir, "projects/.seeded")
        if (!seededMarker.exists()) {
            ProjectTemplates.starter("My Site").forEach { (path, content) ->
                workspace.write(path, content)
            }
            seededMarker.parentFile?.mkdirs()
            seededMarker.writeText("1")
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
        // The Node process is intentionally disposable, but the project is durable. Restore the
        // live preview after an activity/process recreation so a finished site remains usable
        // without loading the model or asking it to start infrastructure again.
        if (workspace.exists("index.html")) {
            viewModelScope.launch {
                runCatching { devServer.start(projectRoot) }
                    .onFailure { Log.w(TAG, "could not restore preview", it) }
            }
        }
    }

    // --- model -------------------------------------------------------------------

    fun refreshModels() {
        _state.value = _state.value.copy(models = catalogEntries())
    }

    /** Rebuilds the menu rows from disk, preserving any download in flight. */
    private fun catalogEntries(): List<ModelEntry> = CatalogModel.entries.map { model ->
        val inFlight = downloads[model.id]
        val state = when {
            inFlight != null -> ModelInstallState.DOWNLOADING
            model.id == loadedModelId -> ModelInstallState.LOADED
            model.isInstalledIn(modelsDir) -> ModelInstallState.DOWNLOADED
            else -> ModelInstallState.NOT_DOWNLOADED
        }
        ModelEntry(
            id = model.id,
            displayName = model.displayName,
            subtitle = model.subtitle,
            approxBytes = model.approxBytes,
            state = state,
            progress = inFlight?.fraction ?: 0f,
            progressLabel = inFlight?.let { progress ->
                humanSize(progress.bytesDone) + " of " + humanSize(progress.bytesTotal)
            }.orEmpty(),
        )
    }

    fun downloadModel(id: String) {
        val model = CatalogModel.byId(id) ?: return
        if (downloadJobs.containsKey(id)) return

        downloads[id] = DownloadProgress(id, "", 0L, model.approxBytes)
        refreshModels()

        downloadJobs[id] = viewModelScope.launch {
            try {
                downloader.download(model).collect { progress ->
                    downloads[id] = progress
                    refreshModels()
                }
                downloads.remove(id)
                downloadJobs.remove(id)
                refreshModels()
                _state.value = _state.value.copy(status = model.displayName + " downloaded")
            } catch (e: Exception) {
                Log.e(TAG, "download failed for " + id, e)
                downloads.remove(id)
                downloadJobs.remove(id)
                refreshModels()
                _state.value = _state.value.copy(
                    status = "Download failed: " + (e.message ?: "unknown error"),
                )
            }
        }
    }

    fun cancelDownload(id: String) {
        downloadJobs.remove(id)?.cancel()
        downloads.remove(id)
        refreshModels()
        _state.value = _state.value.copy(status = "Download paused")
    }

    /** Frees the disk a model takes. Partial downloads resume, so this is the way out. */
    fun deleteModel(id: String) {
        val model = CatalogModel.byId(id) ?: return
        cancelDownload(id)
        if (loadedModelId == model.id) {
            loadedModelId = null
            viewModelScope.launch { engine.release() }
            _state.value = _state.value.copy(modelStatus = ModelStatus.MISSING, modelName = null)
        }
        downloader.delete(model)
        refreshModels()
        _state.value = _state.value.copy(status = model.displayName + " removed")
    }

    fun loadModel(id: String) {
        val model = CatalogModel.byId(id) ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                modelStatus = ModelStatus.LOADING,
                modelName = model.shortName,
                status = "Loading " + model.displayName,
            )
            val loaded = runCatching {
                engine.load(
                    ModelConfig(
                        modelDir = model.directoryIn(modelsDir),
                        tmpDir = File(getApplication<Application>().cacheDir, "mnn"),
                        // Exports disagree on this: Qwen ships tokenizer.txt, Gemma 4
                        // ships tokenizer.mtok.
                        tokenizerFile = model.tokenizerFile,
                    ),
                )
            }.getOrElse { error ->
                Log.e(TAG, "model load failed", error)
                false
            }
            loadedModelId = if (loaded) model.id else null
            _state.value = _state.value.copy(
                modelStatus = if (loaded) ModelStatus.READY else ModelStatus.FAILED,
                status = if (loaded) {
                    model.displayName + " ready · 32k context"
                } else {
                    "Could not load " + model.displayName
                },
            )
            refreshModels()
        }
    }

    /** Megabytes until a download is big enough for gigabytes to mean anything. */
    private fun humanSize(bytes: Long): String = if (bytes < 1_000_000_000L) {
        String.format(java.util.Locale.US, "%d MB", bytes / 1_000_000L)
    } else {
        String.format(java.util.Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
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

        runTokens = 0
        runDecodeMicros = 0L
        runPromptTokens = 0
        runPrefillMicros = 0L

        agentJob = viewModelScope.launch {
            try {
                agent.run(prompt).collect { update -> apply(assistantId, update) }
            } catch (e: Exception) {
                Log.e(TAG, "agent run failed", e)
                updateMessage(assistantId) { it.copy(text = it.text + "\n\n" + e.message) }
            } finally {
                updateMessage(assistantId) { it.copy(streaming = false) }
                _state.value = _state.value.copy(
                    isGenerating = false,
                    status = throughputSummary(),
                )
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

            is AgentUpdate.TurnStats -> {
                runTokens += update.stats.generatedTokens
                runDecodeMicros += update.stats.decodeMicros
                runPromptTokens += update.stats.promptTokens
                runPrefillMicros += update.stats.prefillMicros
            }

            is AgentUpdate.Progress -> _state.value = _state.value.copy(
                status = "Generating… " + update.charsGenerated + " chars",
            )

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

    private fun throughputSummary(): String {
        if (runTokens <= 0 || runDecodeMicros <= 0L) return ""
        val decode = runTokens * 1_000_000.0 / runDecodeMicros
        val prefill = if (runPrefillMicros > 0L) {
            runPromptTokens * 1_000_000.0 / runPrefillMicros
        } else {
            0.0
        }
        val summary = String.format(
            java.util.Locale.US,
            "%.1f tok/s decode · %.0f tok/s prefill · %d tokens generated",
            decode,
            prefill,
            runTokens,
        )
        // Also logged so a throughput sweep can be scripted off logcat instead of
        // screenshotting the status line.
        Log.i(TAG, "throughput: " + summary)
        return summary
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

    fun createFolder(path: String) {
        if (path.isBlank()) return
        runCatching { workspace.createDirectory(path) }
            .onFailure { appendSystem("Could not create " + path + ": " + it.message) }
        refreshFiles()
    }

    fun renameFile(from: String, to: String) {
        runCatching { workspace.rename(from, to) }
            .onSuccess {
                // Keep the editor pointed at the file the user is looking at.
                if (_state.value.openFile?.path == from) openFile(to)
                _state.value = _state.value.copy(status = "Renamed to " + to)
            }
            .onFailure { appendSystem("Could not rename " + from + ": " + it.message) }
        refreshFiles()
    }

    fun deleteFile(path: String) {
        runCatching { workspace.delete(path) }
            .onFailure { appendSystem("Could not delete " + path + ": " + it.message) }
        if (_state.value.openFile?.path == path) {
            _state.value = _state.value.copy(openFile = null)
        }
        refreshFiles()
    }

    /**
     * Clears the project and the conversation so the next prompt starts from nothing.
     *
     * Wiping files alone is not enough: MNN keeps the conversation's KV cache on the
     * model, so without resetting it the agent still remembers the page it wrote and
     * keeps editing that instead of starting over. The dev server goes too, since it is
     * serving a directory that is about to be empty.
     */
    fun newProject() {
        stopGeneration()
        devServer.stop()
        viewModelScope.launch {
            workspace.list().forEach { entry ->
                runCatching { workspace.delete(entry.relativePath) }
            }
            engine.resetHistory()
            _state.value = _state.value.copy(
                messages = emptyList(),
                input = "",
                openFile = null,
                previewUrl = null,
                devServerRunning = false,
                status = "New project",
            )
            refreshFiles()
        }
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
