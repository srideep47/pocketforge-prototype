package com.srideep.pocketforge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srideep.pocketforge.chat.StudioViewModel
import com.srideep.pocketforge.ui.StudioActions
import com.srideep.pocketforge.ui.StudioScreen
import com.srideep.pocketforge.ui.theme.PocketForgeTheme

class MainActivity : ComponentActivity() {

    /** Dictation is the only thing that needs a runtime permission, so it is asked for inline. */
    private val requestMicrophone = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) pendingDictation?.invoke() ; pendingDictation = null }

    private var pendingDictation: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PocketForgeTheme {
                val viewModel: StudioViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                StudioScreen(
                    state = state,
                    actions = StudioActions(
                        onInputChange = viewModel::onInputChange,
                        onSend = viewModel::send,
                        onStop = viewModel::stopGeneration,
                        onMic = { withMicrophone(viewModel::startDictation) },
                        onOpenFile = viewModel::openFile,
                        onEditorChange = viewModel::onEditorChange,
                        onSaveFile = viewModel::saveOpenFile,
                        onCreateFile = viewModel::createFile,
                        onCreateFolder = viewModel::createFolder,
                        onRenameFile = viewModel::renameFile,
                        onDeleteFile = viewModel::deleteFile,
                        onRefreshFiles = viewModel::refreshFiles,
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
