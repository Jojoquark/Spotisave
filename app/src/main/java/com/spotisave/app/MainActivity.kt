package com.spotisave.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.spotisave.app.ui.screens.MainScreen
import com.spotisave.app.ui.theme.SpotisaveTheme
import com.spotisave.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleOAuthIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            val isWhiteMode by viewModel.isWhiteMode.collectAsState()
            SpotisaveTheme(darkTheme = !isWhiteMode) {
                MainScreen(viewModel = viewModel)
            }
        }
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "spotisave" && uri.host == "callback") {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            if (!code.isNullOrBlank()) {
                viewModel.handleAuthCode(code)
            } else if (!error.isNullOrBlank()) {
                viewModel.handleAuthError(error)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }
}
