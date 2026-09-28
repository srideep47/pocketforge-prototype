package com.srideep.pocketforge

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srideep.pocketforge.chat.StudioViewModel
import com.srideep.pocketforge.ui.StudioActions
import com.srideep.pocketforge.ui.StudioScreen
import com.srideep.pocketforge.ui.theme.PocketForgeTheme
import java.io.File

class MainActivity : ComponentActivity() {

    /** Dictation is the only thing that needs a runtime permission, so it is asked for inline. */
    private val requestMicrophone = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) pendingDictation?.invoke() ; pendingDictation = null }

    private var pendingDictation: (() -> Unit)? = null

    private var viewModelRef: StudioViewModel? = null

    /**
     * The system camera writes straight into a file we own. Going through the camera app
     * rather than CameraX means no CAMERA permission prompt: the capture intent does not
     * need one as long as the manifest does not declare it.
     */
    private var pendingCapture: Uri? = null
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCapture
        pendingCapture = null
        if (saved && uri != null) viewModelRef?.attachImage(uri)
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModelRef?.attachImage(uri)
    }

    /** The keep-alive notification; the service runs either way, this only makes it visible. */
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            PocketForgeTheme {
                val viewModel: StudioViewModel = viewModel()
                viewModelRef = viewModel
                val state by viewModel.state.collectAsStateWithLifecycle()
                // A run takes minutes on a phone; the screen locking mid-run hides the result,
                // and in a demo it looks like the app stalled.
                val view = LocalView.current
                DisposableEffect(state.isGenerating) {
                    view.keepScreenOn = state.isGenerating
                    onDispose { view.keepScreenOn = false }
                }

                StudioScreen(
                    state = state,
                    actions = StudioActions(
                        onInputChange = viewModel::onInputChange,
                        onSend = viewModel::send,
                        onStop = viewModel::stopGeneration,
                        onMic = { withMicrophone(viewModel::startDictation) },
                        onToggleHandsFree = viewModel::toggleHandsFree,
                        onToggleThinking = viewModel::toggleThinking,
                        onAddToHomeScreen = viewModel::addToHomeScreen,
                        onCamera = ::capturePhoto,
                        onPickImage = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onClearImage = viewModel::clearAttachment,
                        onOpenFile = viewModel::openFile,
                        onEditorChange = viewModel::onEditorChange,
                        onSaveFile = viewModel::saveOpenFile,
                        onCreateFile = viewModel::createFile,
                        onCreateFolder = viewModel::createFolder,
                        onRenameFile = viewModel::renameFile,
                        onDeleteFile = viewModel::deleteFile,
                        onRefreshFiles = viewModel::refreshFiles,
                        onNewProject = viewModel::newProject,
                        onStartServer = viewModel::startDevServer,
                        onStopServer = viewModel::stopDevServer,
                        onLoadModel = viewModel::loadModel,
                        onDownloadModel = viewModel::downloadModel,
                        onCancelDownload = viewModel::cancelDownload,
                        onDeleteModel = viewModel::deleteModel,
                        onRefreshModels = viewModel::refreshModels,
                    ),
                )
            }
        }
    }

    private fun capturePhoto() {
        val dir = File(cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_" + System.currentTimeMillis() + ".jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        pendingCapture = uri
        takePicture.launch(uri)
    }

    private fun withMicrophone(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingDictation = action
            requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
