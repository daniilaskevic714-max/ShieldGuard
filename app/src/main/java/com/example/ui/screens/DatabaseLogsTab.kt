package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ScanReport
import com.example.data.model.SensitivityLevel
import com.example.ui.ShieldViewModel
import com.example.ui.components.SecurityCard
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.ShieldAmber
import com.example.ui.theme.ShieldCrimson
import com.example.ui.theme.ShieldCyan
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.ShieldObsidian
import com.example.ui.theme.ShieldOutline
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun DatabaseLogsTab(viewModel: ShieldViewModel) {
    val signatureState by viewModel.signatureSyncState.collectAsStateWithLifecycle()
    val savedAuthKey by viewModel.abuseChAuthKey.collectAsStateWithLifecycle()
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    val trustedApps by viewModel.trustedAppsList.collectAsStateWithLifecycle()
    val reports by viewModel.reportsList.collectAsStateWithLifecycle()

    var showKeyConfig by remember { mutableStateOf(false) }
    var keyDraft by remember(savedAuthKey) { mutableStateOf(savedAuthKey) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        // 1. Malware Signature Database Sync
        item(key = "signature_db") {
            SecurityCard(borderColor = ShieldCyan.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, tint = ShieldCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "База сигнатур угроз (SHA-256)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    TextButton(onClick = { showKeyConfig = !showKeyConfig }) {
                        Text(
                            text = if (savedAuthKey.isNotEmpty()) "API Key ✓" else "API Key",
                            color = ShieldCyan,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Локальных хешей: ${signatureState.count}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (signatureState.count > 0) ShieldEmerald else ShieldAmber
                )
                Text(
                    text = "Источник: ${if (savedAuthKey.isNotEmpty()) "Abuse.ch API v1 (Auth-Key)" else signatureState.sourceName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "Последнее обновление: ${formatTimestamp(signatureState.lastSyncTimeMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (showKeyConfig) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        label = { Text("Abuse.ch Auth-Key (необязательно)", fontSize = 12.sp) },
                        placeholder = { Text("Без ключа используется публичный фид GitHub", fontSize = 11.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ShieldCyan,
                            unfocusedBorderColor = ShieldOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            keyDraft = ""
                            viewModel.saveAbuseChAuthKey("")
                            showKeyConfig = false
                        }) {
                            Text("Сбросить", color = TextSecondary, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            viewModel.saveAbuseChAuthKey(keyDraft)
                            showKeyConfig = false
                        }) {
                            Text("Сохранить ключ", color = ShieldEmerald, fontSize = 12.sp)
                        }
                    }
                }

                signatureState.lastError?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ошибка синхронизации: $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = ShieldCrimson
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.updateSignatureDatabase() },
                    enabled = !signatureState.isSyncing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShieldCyan,
                        contentColor = ShieldObsidian
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("update_signatures_button")
                ) {
                    if (signatureState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ShieldObsidian
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Загрузка хешей угроз...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Обновить базу сигнатур", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Audit Sensitivity Level
        item(key = "sensitivity_card") {
            SecurityCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = ShieldCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Строгость оценки разрешений", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Определяет вес опасных разрешений при расчёте индекса риска приложений.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SensitivityLevel.entries.forEach { level ->
                        val selected = sensitivity == level
                        val accent = when (level) {
                            SensitivityLevel.LOW -> ShieldEmerald
                            SensitivityLevel.MEDIUM -> ShieldCyan
                            SensitivityLevel.HIGH -> ShieldAmber
                        }
                        OutlinedButton(
                            onClick = { viewModel.setSensitivity(level) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (selected) accent else ShieldOutline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (selected) accent else TextSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("sensitivity_${level.key.lowercase()}")
                        ) {
                            Text(level.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        // 3. Trusted Apps Whitelist
        item(key = "trusted_list") {
            SecurityCard {
                Text("Доверенные приложения (${trustedApps.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Исключения, добавленные вами в разделе «Приложения», не учитываются как риски.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (trustedApps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = ShieldOutline.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(6.dp))
                    trustedApps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = ShieldEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            IconButton(onClick = { viewModel.removeTrustedApp(app) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Убрать из доверенных", tint = ShieldCrimson)
                            }
                        }
                    }
                }
            }
        }

        // 4. Scan History Header
        item(key = "reports_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ЖУРНАЛ ПРОВЕРОК (${reports.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                if (reports.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAllReports() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = ShieldCrimson, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Очистить", color = ShieldCrimson, fontSize = 12.sp)
                    }
                }
            }
        }

        if (reports.isEmpty()) {
            item(key = "reports_empty") {
                SecurityCard {
                    Text(
                        text = "Журнал пуст. Запустите быстрый аудит, проверку APK или инспекцию файла на вкладке «Обзор».",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(
                items = reports,
                key = { "report_${it.id}" },
                contentType = { "scan_report" }
            ) { report ->
                ScanReportRow(report = report)
            }
        }
    }
}

@Composable
private fun ScanReportRow(report: ScanReport) {
    val accent = when (report.status) {
        "Чисто" -> ShieldEmerald
        "Есть риски" -> ShieldAmber
        else -> ShieldCrimson
    }
    SecurityCard(borderColor = accent.copy(alpha = 0.4f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (report.status) {
                        "Чисто" -> Icons.Filled.CheckCircle
                        "Есть риски" -> Icons.Filled.Warning
                        else -> Icons.Filled.GppBad
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(report.scanType, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            Text(report.status, style = MaterialTheme.typography.labelMedium, color = accent)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Объектов: ${report.appsScannedCount} • Найдено рисков: ${report.findingsCount} • Время: ${report.durationMs} мс",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = formatTimestamp(report.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}
