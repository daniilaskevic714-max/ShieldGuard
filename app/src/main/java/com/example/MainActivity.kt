package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.ShieldAppMain
import com.example.ui.ShieldViewModel
import com.example.ui.theme.ShieldGuardTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ShieldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        handleExtras(intent)

        setContent {
            ShieldGuardTheme {
                ShieldAppMain(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExtras(intent)
    }

    private fun handleExtras(intent: Intent?) {
        if (intent?.getBooleanExtra("WIDGET_TRIGGER_SCAN", false) == true) {
            viewModel.runQuickAudit()
        }
    }
}
