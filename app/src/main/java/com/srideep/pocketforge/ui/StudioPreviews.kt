package com.srideep.pocketforge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.srideep.pocketforge.chat.ChatMessage
import com.srideep.pocketforge.chat.ModelEntry
import com.srideep.pocketforge.chat.ModelInstallState
import com.srideep.pocketforge.chat.ModelStatus
import com.srideep.pocketforge.chat.OpenFile
import com.srideep.pocketforge.chat.Role
import com.srideep.pocketforge.chat.RunMetrics
import com.srideep.pocketforge.chat.StudioUiState
import com.srideep.pocketforge.chat.ToolTrace
import com.srideep.pocketforge.ui.theme.PocketForgeTheme
import com.srideep.pocketforge.workspace.WorkspaceEntry

private val NoOpActions = StudioActions(
    onInputChange = {},
    onSend = {},
    onStop = {},
    onMic = {},
    onToggleHandsFree = {},
    onCamera = {},
    onPickImage = {},
    onClearImage = {},
    onOpenFile = {},
    onEditorChange = {},
    onSaveFile = {},
    onCreateFile = {},
    onCreateFolder = {},
    onRenameFile = { _, _ -> },
    onDeleteFile = {},
    onRefreshFiles = {},
    onNewProject = {},
    onStartServer = {},
    onStopServer = {},
    onLoadModel = {},
    onDownloadModel = {},
    onCancelDownload = {},
    onDeleteModel = {},
    onRefreshModels = {},
)

private val SampleFiles = listOf(
    WorkspaceEntry("index.html", "index.html", isDirectory = false, sizeBytes = 4280L),
    WorkspaceEntry("styles.css", "styles.css", isDirectory = false, sizeBytes = 1840L),
    WorkspaceEntry("app.js", "app.js", isDirectory = false, sizeBytes = 2190L),
)

@Preview(
    name = "0. PocketForge Brand Logo & Adaptive Emblem",
    showBackground = true,
    widthDp = 320,
    heightDp = 320,
)
@Composable
fun PreviewBrandLogo() {
    PocketForgeTheme(darkTheme = true) {
        Surface {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                PocketForgeLogo(size = 180.dp)
            }
        }
    }
}

@Preview(
    name = "1. Empty Chat (Hero & Starters)",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
fun PreviewEmptyChat() {
    PocketForgeTheme(darkTheme = true) {
        StudioScreen(
            state = StudioUiState(
                modelStatus = ModelStatus.READY,
                modelName = "Qwen 3.5 · 4B",
                status = "Qwen 3.5 · 4B ready · 32k context",
                files = emptyList(),
            ),
            actions = NoOpActions,
        )
    }
}

@Preview(
    name = "2. Generating With Steps & Live File Writer",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
fun PreviewGeneratingWithSteps() {
    PocketForgeTheme(darkTheme = true) {
        StudioScreen(
            state = StudioUiState(
                modelStatus = ModelStatus.READY,
                modelName = "Qwen 3.5 · 4B",
                isGenerating = true,
                status = "Generating… 2480 chars",
                files = SampleFiles,
                metrics = RunMetrics(
                    running = true,
                    elapsedMs = 8400L,
                    timeToFirstTokenMs = 680L,
                    visionMs = 540L,
                    prefillTokensPerSecond = 164.0,
                    decodeTokensPerSecond = 26.4,
                    generatedTokens = 620,
                    peakRamMb = 3180,
                    airplaneMode = true,
                    offline = true,
                    thermalStatus = 1,
                ),
                messages = listOf(
                    ChatMessage(
                        id = 1L,
                        role = Role.USER,
                        text = "Build this hand-drawn retro coffee shop menu & order calculator.",
                        imagePath = "preview://sketch_coffee.jpg",
                    ),
                    ChatMessage(
                        id = 2L,
                        role = Role.ASSISTANT,
                        streaming = true,
                        tools = listOf(
                            ToolTrace(
                                name = "read_sketch",
                                summary = "SmolVLM2 vision",
                                ok = true,
                                detail = "Header 'BREW LAB', 2-column espresso card grid, quantity stepper, and sticky bottom total bar.",
                            ),
                            ToolTrace(
                                name = "create_file",
                                summary = "index.html",
                                ok = null,
                            ),
                        ),
                    ),
                ),
            ),
            actions = NoOpActions,
        )
    }
}

@Preview(
    name = "3. Finished Run With Self-Check & Metrics (360dp Compact)",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
)
@Composable
fun PreviewFinishedRunWithMetrics() {
    PocketForgeTheme(darkTheme = true) {
        StudioScreen(
            state = StudioUiState(
                modelStatus = ModelStatus.READY,
                modelName = "Qwen 3.5 · 4B",
                isGenerating = false,
                devServerRunning = true,
                previewUrl = "http://127.0.0.1:4173",
                status = "Qwen 3.5 · 4B ready · 25.8 tok/s · 940 tokens",
                files = SampleFiles,
                metrics = RunMetrics(
                    running = false,
                    elapsedMs = 15200L,
                    timeToFirstTokenMs = 640L,
                    visionMs = 920L,
                    prefillTokensPerSecond = 158.0,
                    decodeTokensPerSecond = 25.8,
                    generatedTokens = 940,
                    peakRamMb = 3240,
                    airplaneMode = false,
                    offline = true,
                    thermalStatus = 1,
                ),
                messages = listOf(
                    ChatMessage(
                        id = 1L,
                        role = Role.USER,
                        text = "A dark landing page for a coffee shop",
                    ),
                    ChatMessage(
                        id = 2L,
                        role = Role.ASSISTANT,
                        streaming = false,
                        text = "Built **BrewLab Roasters** in `index.html` and fixed the hero CTA contrast flagged by the vision check.",
                        tools = listOf(
                            ToolTrace("create_file", "index.html", ok = true),
                            ToolTrace("start_dev_server", "http://127.0.0.1:4173", ok = true),
                            ToolTrace(
                                name = "check_render",
                                summary = "fixing: hero CTA button text clipped on 360dp viewport",
                                ok = true,
                                detail = "hero CTA button text clipped on 360dp viewport",
                            ),
                            ToolTrace("edit_file", "index.html", ok = true),
                        ),
                    ),
                ),
            ),
            actions = NoOpActions,
        )
    }
}

@Preview(
    name = "4. Model Sheet (Coder vs Vision Helper)",
    showBackground = true,
    widthDp = 412,
)
@Composable
fun PreviewModelSheet() {
    PocketForgeTheme(darkTheme = true) {
        Surface {
            ModelSheetContent(
                models = listOf(
                    ModelEntry(
                        id = "qwen3.5-4b",
                        displayName = "Qwen 3.5 · 4B Instruct",
                        subtitle = "Fast 4-bit MNN coder · 32k context · tool calling",
                        approxBytes = 2_600_000_000L,
                        state = ModelInstallState.LOADED,
                    ),
                    ModelEntry(
                        id = "qwen3.5-1.7b",
                        displayName = "Qwen 3.5 · 1.7B Instruct",
                        subtitle = "Ultra-lightweight coder for 6 GB RAM devices",
                        approxBytes = 1_200_000_000L,
                        state = ModelInstallState.DOWNLOADED,
                    ),
                    ModelEntry(
                        id = "gemma4-4b",
                        displayName = "Gemma 4 · 4B Code",
                        subtitle = "Alternative local HTML/JS generator",
                        approxBytes = 2_800_000_000L,
                        state = ModelInstallState.DOWNLOADING,
                        progress = 0.64f,
                        progressLabel = "1.79 GB of 2.80 GB",
                    ),
                    ModelEntry(
                        id = "smolvlm2-vision",
                        displayName = "SmolVLM2 · 0.8B Vision",
                        subtitle = "Reads hand-drawn sketches & inspects rendered pages",
                        approxBytes = 580_000_000L,
                        state = ModelInstallState.DOWNLOADED,
                        isVision = true,
                    ),
                ),
                onDownload = {},
                onCancel = {},
                onLoad = {},
                onDelete = {},
            )
        }
    }
}

@Preview(
    name = "5. Code Editor With File Tabs (Light Theme)",
    showBackground = true,
    widthDp = 412,
    heightDp = 760,
)
@Composable
fun PreviewCodeEditorLight() {
    PocketForgeTheme(darkTheme = false) {
        Surface {
            EditorPane(
                openFile = OpenFile(
                    path = "index.html",
                    content = """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                          <meta charset="UTF-8" />
                          <title>BrewLab Offline</title>
                        </head>
                        <body class="dark-roast">
                          <main id="app">
                            <h1>Crafted on Honor Magic 7 Pro</h1>
                          </main>
                        </body>
                        </html>
                    """.trimIndent(),
                    dirty = true,
                ),
                onContentChange = {},
                onSave = {},
                files = SampleFiles,
            )
        }
    }
}
