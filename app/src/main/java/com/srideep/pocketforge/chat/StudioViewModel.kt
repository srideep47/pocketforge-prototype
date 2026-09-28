package com.srideep.pocketforge.chat

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.srideep.pocketforge.KeepAliveService
import com.srideep.pocketforge.agent.AgentLoop
import com.srideep.pocketforge.agent.AgentTools
import com.srideep.pocketforge.agent.AgentUpdate
import com.srideep.pocketforge.engine.mnn.MnnLlmEngine
import com.srideep.pocketforge.engine.mnn.ModelConfig
import com.srideep.pocketforge.metrics.DeviceMetrics
import com.srideep.pocketforge.model.CatalogModel
import com.srideep.pocketforge.model.DownloadProgress
import com.srideep.pocketforge.model.ModelDownloader
import com.srideep.pocketforge.model.ModelRole
import com.srideep.pocketforge.preview.PageSnapshot
import com.srideep.pocketforge.runtime.node.DevServerClient
import com.srideep.pocketforge.vision.ImageInput
import com.srideep.pocketforge.vision.PreparedImage
import com.srideep.pocketforge.vision.VisionSidecar
import com.srideep.pocketforge.voice.SpeechToText
import com.srideep.pocketforge.workspace.ProjectTemplates
import com.srideep.pocketforge.workspace.Workspace
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single state holder for the studio: chat + agent, the workspace files, and the dev
 * server. There is one model and one project on screen at a time, so one ViewModel keeps
 * the three tabs in step without an event bus between them.
 */
class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = MnnLlmEngine()
    private val devServer = DevServerClient(application)
    private val speech = SpeechToText(application)
    private val device = DeviceMetrics(application)

    /** Reads photos for coders that cannot see images. Loaded on first use, ~0.5 GB. */
    private val sidecar = VisionSidecar()

    private val projectRoot = File(application.filesDir, "projects/site")

    /** Photos live outside the project so they are never served or edited as site files. */
    private val attachmentsDir = File(application.filesDir, "attachments")
    private var attachment: PreparedImage? = null
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
    private var runVisionMicros = 0L

    /** MNN's vision counter is cumulative until a reset, so turns are measured as deltas. */
    private var lastVisionMicros = 0L
    private var runStartedAt = 0L
    private var firstTokenAt = 0L
    private var metricsJob: Job? = null

    /** Downloads in flight, and the id of whatever the engine currently holds. */
    private val downloads = mutableMapOf<String, DownloadProgress>()
    private val downloadJobs = mutableMapOf<String, Job>()
    private var loadedModelId: String? = null

    /** What the loaded model's chat template was set up with; the toggle alone may be ahead of it. */
    private var loadedWithThinking = false

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
            ProjectTemplates.starter(STARTER_NAME).forEach { (path, content) ->
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
            isVision = model.role == ModelRole.VISION,
            sideloadOnly = model.sideloadOnly,
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
            KeepAliveService.stop(getApplication())
            viewModelScope.launch { engine.release() }
            _state.value = _state.value.copy(modelStatus = ModelStatus.MISSING, modelName = null)
        }
        downloader.delete(model)
        refreshModels()
        _state.value = _state.value.copy(status = model.displayName + " removed")
    }

    fun loadModel(id: String) {
        val model = CatalogModel.byId(id) ?: return
        val thinking = _state.value.thinking
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
                        threadNum = model.threadNum,
                        prefillChunk = model.prefillChunk,
                        maxAllTokens = model.contextTokens,
                        kvcacheMmap = model.kvCacheOnStorage,
                        enableThinking = thinking,
                    ),
                )
            }.getOrElse { error ->
                Log.e(TAG, "model load failed", error)
                false
            }
            loadedModelId = if (loaded) model.id else null
            loadedWithThinking = loaded && thinking
            if (loaded) {
                KeepAliveService.start(getApplication(), model.displayName + " loaded")
            } else {
                KeepAliveService.stop(getApplication())
            }
            _state.value = _state.value.copy(
                modelStatus = if (loaded) ModelStatus.READY else ModelStatus.FAILED,
                status = if (loaded) {
                    model.displayName + " ready · " + model.contextTokens / 1000 + "k context" +
                        if (thinking) " · thinking" else ""
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
        val image = attachment
        val typed = _state.value.input.trim()
        if ((typed.isEmpty() && image == null) || _state.value.isGenerating) return
        if (_state.value.modelStatus != ModelStatus.READY) {
            appendSystem("Load a model first.")
            return
        }
        val prompt = typed.ifEmpty { "Build this as a working page." }
        // A photo means "build what this shows", so it starts a page rather than editing one.
        val currentPage = if (image == null) pageToEdit() else null

        appendMessage(
            ChatMessage(
                id = nextMessageId++,
                role = Role.USER,
                text = prompt,
                imagePath = image?.file?.absolutePath,
            ),
        )
        val assistantId = nextMessageId++
        appendMessage(ChatMessage(id = assistantId, role = Role.ASSISTANT, streaming = true))
        attachment = null
        _state.value = _state.value.copy(input = "", attachedImage = null, isGenerating = true)

        runTokens = 0
        runDecodeMicros = 0L
        runPromptTokens = 0
        runPrefillMicros = 0L
        runVisionMicros = 0L
        lastVisionMicros = engine.lastStats().visionMicros
        startMetrics()

        agentJob = viewModelScope.launch {
            try {
                var imageTag = image?.mnnTag
                var sketch: String? = null
                if (image != null && !coderSeesImages()) {
                    sketch = readSketch(assistantId, image) ?: return@launch
                    imageTag = null
                }
                val pageBefore = currentIndex()
                val wrote = runAgent(
                    assistantId,
                    agent.run(prompt, currentPage, imageTag, sketch, thinking = loadedWithThinking),
                )
                if (wrote && currentIndex() != pageBefore) checkRender(assistantId)
            } catch (e: Exception) {
                Log.e(TAG, "agent run failed", e)
                updateMessage(assistantId) { it.copy(text = it.text + "\n\n" + e.message) }
            } finally {
                // trimEnd: a fix round that ends silently leaves the separator it was given.
                updateMessage(assistantId) { it.copy(text = it.text.trimEnd(), streaming = false) }
                finishMetrics()
                _state.value = _state.value.copy(
                    isGenerating = false,
                    status = throughputSummary(),
                )
                refreshFiles()
            }
        }
    }

    private fun coderSeesImages(): Boolean =
        loadedModelId?.let { CatalogModel.byId(it) }?.seesImages == true

    /**
     * Has the vision sidecar read the photo, shown as a step in the chat, and returns its
     * description, or null (with the reason already shown) if it could not.
     */
    private suspend fun readSketch(assistantId: Long, image: PreparedImage): String? {
        val vision = CatalogModel.vision
        if (!vision.isInstalledIn(modelsDir)) {
            updateMessage(assistantId) {
                it.copy(text = "This model cannot see images. Download " + vision.displayName + " from the model menu to build from photos.")
            }
            return null
        }
        updateMessage(assistantId) { it.copy(tools = it.tools + ToolTrace("read_sketch", vision.shortName + " vision")) }
        _state.value = _state.value.copy(status = "Reading the sketch…")
        val result = runCatching {
            loadSidecar(vision)
            sidecar.read(image.mnnTag)
        }
        val reading = result.getOrElse { error ->
            Log.e(TAG, "sketch reading failed", error)
            finishTool(assistantId, "read_sketch", ok = false, detail = error.message ?: "failed")
            updateMessage(assistantId) { it.copy(text = "Could not read the photo: " + (error.message ?: "unknown error")) }
            return null
        }
        runVisionMicros += reading.visionMicros
        finishTool(assistantId, "read_sketch", ok = true, detail = reading.description)
        Log.i(TAG, "sketch read in " + reading.elapsedMs + " ms:\n" + reading.description)
        return reading.description
    }

    /** Streams one agent run into the message; true if it ended with index.html written. */
    private suspend fun runAgent(assistantId: Long, run: Flow<AgentUpdate>): Boolean {
        var done = false
        run.collect { update ->
            if (update == AgentUpdate.Done) done = true
            apply(assistantId, update)
        }
        return done
    }

    private fun currentIndex(): String? = runCatching { workspace.read("index.html") }.getOrNull()

    /**
     * Visual self-check: screenshots the page the agent just wrote, asks the vision sidecar
     * whether it looks right, and gives the agent one chance to fix what it names.
     *
     * No coder ever sees its own output, so text clipped by a fixed height or a button under
     * another one used to reach the user unnoticed. Coders that see images still go through
     * the sidecar: they would need the screenshot in a new prompt, and the sidecar is cheaper.
     * It is skipped, not offered, when the vision model is not installed. One fix round at
     * most: a 0.8B critic is noisy, and chasing its every remark would loop or make the page
     * worse.
     */
    private suspend fun checkRender(assistantId: Long) {
        val vision = CatalogModel.vision
        if (!vision.isInstalledIn(modelsDir)) return
        val url = _state.value.previewUrl ?: return
        updateMessage(assistantId) { it.copy(tools = it.tools + ToolTrace("check_render", vision.shortName + " vision")) }
        _state.value = _state.value.copy(status = "Checking the page…")
        val check = try {
            val shot = PageSnapshot.capture(getApplication(), url, attachmentsDir)
                ?: error("could not render the page")
            loadSidecar(vision)
            sidecar.inspect(shot.mnnTag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The page is already written; a check that cannot run just ends the run as before.
            Log.w(TAG, "render check failed", e)
            finishTool(assistantId, "check_render", ok = false, detail = e.message ?: "failed")
            return
        }
        runVisionMicros += check.visionMicros
        publishMetrics(running = true)
        Log.i(TAG, "render checked in " + check.elapsedMs + " ms:\n" + check.answers)
        val verdict = check.verdict
        finishTool(
            assistantId,
            "check_render",
            ok = true,
            detail = verdict.summary(),
            summary = if (verdict.hasProblems) "fixing: " + verdict.summary() else "looks right",
        )
        if (!verdict.hasProblems) return

        val page = pageToEdit() ?: return
        _state.value = _state.value.copy(status = "Fixing what the check found…")
        updateMessage(assistantId) { if (it.text.isBlank()) it else it.copy(text = it.text + "\n\n") }
        runAgent(assistantId, agent.run(verdict.fixRequest(), page, thinking = loadedWithThinking))
    }

    private suspend fun loadSidecar(vision: CatalogModel) {
        val config = VisionSidecar.configFor(
            modelDir = vision.directoryIn(modelsDir),
            tmpDir = File(getApplication<Application>().cacheDir, "mnn-vision"),
            tokenizerFile = vision.tokenizerFile,
        )
        check(sidecar.ensureLoaded(config)) { "could not load " + vision.displayName }
    }

    private fun finishTool(
        assistantId: Long,
        name: String,
        ok: Boolean,
        detail: String,
        summary: String? = null,
    ) = updateMessage(assistantId) { message ->
        val index = message.tools.indexOfLast { it.name == name && it.ok == null }
        if (index < 0) return@updateMessage message
        val updated = message.tools.toMutableList()
        updated[index] = updated[index].copy(ok = ok, detail = detail, summary = summary ?: updated[index].summary)
        message.copy(tools = updated)
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
                runVisionMicros += (update.stats.visionMicros - lastVisionMicros).coerceAtLeast(0L)
                lastVisionMicros = update.stats.visionMicros
                publishMetrics(running = true)
            }

            is AgentUpdate.Progress -> {
                if (firstTokenAt == 0L) {
                    firstTokenAt = SystemClock.elapsedRealtime()
                    publishMetrics(running = true)
                }
                _state.value = _state.value.copy(
                    status = "Generating… " + update.charsGenerated + " chars",
                )
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

    /**
     * The page a text request should change, or null to build a fresh one.
     *
     * The untouched first-run starter counts as "nothing yet": treating it as the page to edit
     * would turn "a landing page for a coffee shop" into a patch of the placeholder.
     */
    private fun pageToEdit(): String? {
        if (!workspace.exists("index.html")) return null
        val page = runCatching { workspace.read("index.html") }.getOrNull() ?: return null
        val starter = ProjectTemplates.starter(STARTER_NAME)["index.html"]
        return page.takeUnless { it.isBlank() || it.trim() == starter?.trim() }
    }

    // --- photo input -------------------------------------------------------------------

    /** Prepares a camera capture or gallery pick to go out with the next message. */
    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            val prepared = runCatching {
                withContext(Dispatchers.IO) { ImageInput.prepare(getApplication(), uri, attachmentsDir) }
            }.getOrElse { error ->
                Log.e(TAG, "could not read image", error)
                appendSystem("Could not read that image: " + (error.message ?: "unknown error"))
                return@launch
            }
            attachment = prepared
            _state.value = _state.value.copy(
                attachedImage = prepared.file.absolutePath,
                status = "Photo attached · " + prepared.visionWidth + "x" + prepared.visionHeight,
            )
        }
    }

    fun clearAttachment() {
        attachment = null
        _state.value = _state.value.copy(attachedImage = null)
    }

    // --- metrics -----------------------------------------------------------------------

    private fun startMetrics() {
        runStartedAt = SystemClock.elapsedRealtime()
        firstTokenAt = 0L
        publishMetrics(running = true)
        metricsJob?.cancel()
        // Wall clock and memory move between MNN's per-turn counters, so they get a ticker.
        metricsJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                publishMetrics(running = true)
            }
        }
    }

    private fun finishMetrics() {
        metricsJob?.cancel()
        metricsJob = null
        val final = publishMetrics(running = false)
        // One line per run, so a benchmark sweep can be scripted off logcat.
        Log.i(
            TAG,
            String.format(
                java.util.Locale.US,
                "run_metrics ttft_ms=%d vision_ms=%d prefill_tps=%.1f decode_tps=%.1f tokens=%d " +
                    "elapsed_ms=%d peak_ram_mb=%d offline=%b airplane=%b thermal=%d",
                final.timeToFirstTokenMs ?: -1L,
                final.visionMs,
                final.prefillTokensPerSecond,
                final.decodeTokensPerSecond,
                final.generatedTokens,
                final.elapsedMs,
                final.peakRamMb,
                final.offline,
                final.airplaneMode,
                final.thermalStatus,
            ),
        )
    }

    private fun publishMetrics(running: Boolean): RunMetrics {
        val snapshot = device.snapshot()
        val metrics = RunMetrics(
            running = running,
            elapsedMs = SystemClock.elapsedRealtime() - runStartedAt,
            timeToFirstTokenMs = firstTokenAt.takeIf { it > 0L }?.minus(runStartedAt),
            visionMs = runVisionMicros / 1000L,
            prefillTokensPerSecond = if (runPrefillMicros > 0L) {
                runPromptTokens * 1_000_000.0 / runPrefillMicros
            } else {
                0.0
            },
            decodeTokensPerSecond = if (runDecodeMicros > 0L) runTokens * 1_000_000.0 / runDecodeMicros else 0.0,
            generatedTokens = runTokens,
            peakRamMb = snapshot.peakRamMb,
            airplaneMode = snapshot.airplaneMode,
            offline = snapshot.offline,
            thermalStatus = snapshot.thermalStatus,
        )
        _state.value = _state.value.copy(metrics = metrics)
        return metrics
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
                // Dictation fills the box; sending stays a deliberate tap unless the user
                // switched on hands-free, where the point is to change the page by voice alone.
                _state.value = _state.value.copy(input = text, isListening = false, status = "")
                if (_state.value.handsFree && text.isNotBlank()) send()
            }

            override fun onError(message: String) {
                _state.value = _state.value.copy(isListening = false, status = message)
            }

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(isListening = false)
            }
        })
    }

    /**
     * Thinking is part of the chat template MNN sets up at load, so a loaded model is reloaded
     * to pick it up. The weights stay in the page cache, which makes that a few seconds.
     */
    fun toggleThinking() {
        if (_state.value.isGenerating) return
        val on = !_state.value.thinking
        _state.value = _state.value.copy(thinking = on)
        val loaded = loadedModelId
        if (loaded != null && _state.value.modelStatus == ModelStatus.READY) {
            loadModel(loaded)
        } else {
            _state.value = _state.value.copy(status = if (on) "Thinking on" else "Thinking off")
        }
    }

    fun toggleHandsFree() {
        val on = !_state.value.handsFree
        _state.value = _state.value.copy(
            handsFree = on,
            status = if (on) "Hands-free: speech sends automatically" else "Hands-free off",
        )
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
        sidecar.shutdown()
        KeepAliveService.stop(getApplication())
        super.onCleared()
    }

    private companion object {
        const val TAG = "StudioViewModel"
        const val STARTER_NAME = "My Site"
    }
}
