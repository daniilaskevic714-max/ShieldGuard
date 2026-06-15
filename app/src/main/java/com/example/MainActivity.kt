package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ui.ShieldAppMain
import com.example.ui.ShieldViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private val viewModel: ShieldViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Request vital notifications permission so scan alerts appear instantly
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
      }
    }

    // Handle intent extras from widget or notification actions
    handleExtras(intent)

    setContent {
      MyApplicationTheme {
        ShieldAppMain(viewModel = viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleExtras(intent)
  }

  private fun handleExtras(intent: Intent?) {
    if (intent != null) {
      val runWidgetScan = intent.getBooleanExtra("WIDGET_TRIGGER_SCAN", false)
      if (runWidgetScan) {
        viewModel.runScan(isDeep = false)
      }
    }
  }
}
