package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppAuditInfo
import com.example.data.model.FileInspectionResult
import com.example.data.model.InstallOrigin
import com.example.data.model.PostureCheckItem
import com.example.ui.ShieldViewModel
import com.example.ui.components.AppIconThumbnail
import com.example.ui.components.PostureScoreGauge
import com.example.ui.components.SecurityCard
import com.example.ui.components.SystemActions
import com.example.ui.components.formatBytes
import com.example.ui.components.riskColorFor
import com.example.ui.theme.ShieldAmber
import com.example.ui.theme.ShieldCrimson
import com.example.ui.theme.ShieldCyan
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.ShieldObsidian
import com.example.ui.theme.ShieldOutline
import com.example.ui.theme.ShieldSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun OverviewTab(
    viewModel: ShieldViewModel,
    onOpenAppsTab: () -> Unit
) {
    val context = LocalContext.current
    val posture by viewModel.postureReport.collectAsStateWithLifecycle()
    val auditedApps by viewModel.auditedApps.collectAsStateWithLifecycle()
    val trustedApps by viewModel.trustedAppsList.collectAsStateWithLifecycle()
    val signatureState by viewModel.signatureSyncState.collectAsStateWithLifecycle()

    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scannedCount by viewModel.scannedCount.collectAsStateWithLifecycle()
    val totalToScan by viewModel.totalToScan.collectAsStateWithLifecycle()
    val currentScanTarget by viewModel.currentScanTarget.collectAsStateWithLifecycle()
    val lastScanBanner by viewModel.lastScanBanner.collectAsStateWithLifecycle()

    val isInspectingFile by viewModel.isInspectingFile.collectAsStateWithLifecycle()
    val fileInspectionResult by viewModel.fileInspectionResult.collectAsStateWithLifecycle()
    val fileInspectionError by viewModel.fileInspectionError.collectAsStateWithLifecycle()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectPickedFile(uri)
        }
    }

    val trustedPackages = remember(trustedApps) {
        trustedApps.mapTo(HashSet()) { it.packageName }
    }

    val highRiskApps = remember(auditedApps, trustedPackages) {
        auditedApps.filter {
            !trustedPackages.contains(it.packageName) &&
                (it.isSignatureBlacklisted || (!it.isSystemApp && it.riskScore >= 50))
        }.take(6)
    }

    val userAppsCount = remember(auditedApps) { auditedApps.count { !it.isSystemApp } }
    val sideloadedCount = remember(auditedApps) {
        auditedApps.count { !it.isSystemApp && it.installOrigin == InstallOrigin.SIDELOADED }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("overview_list"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        // 1. Hero Posture Card
        item(key = "hero_posture") {
            val score = posture?.score ?: 100
            val borderColor = when {
                isScanning -> ShieldCyan
                score >= 80 -> ShieldEmerald
                score >= 55 -> ShieldAmber
                else -> ShieldCrimson
            }
            SecurityCard(borderColor = borderColor.copy(alpha = 0.6f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PostureScoreGauge(
                        score = score,
                        isScanning = isScanning || isLoadingApps
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isLoadingApps -> "Загрузка пакетов..."
                                isScanning -> "Выполняется проверка..."
                                score >= 80 -> "Система в порядке"
                                score >= 55 -> "Требует внимания"
                                else -> "Обнаружены риски"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = posture?.let {
                                "${it.deviceModel} • Android ${it.androidVersion} (API ${it.apiLevel})"
                            } ?: "Чтение параметров ОС...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Патч безопасности: ${posture?.securityPatch ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ShieldCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = ShieldOutline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell(label = "Сторонние APK", value = "$sideloadedCount", accent = if (sideloadedCount > 0) ShieldAmber else ShieldEmerald)
                    StatCell(label = "Польз. прилож.", value = "$userAppsCount", accent = ShieldCyan)
                    StatCell(label = "Сигнатуры БД", value = "${signatureState.count}", accent = if (signatureState.count > 0) ShieldEmerald else TextSecondary)
                }
            }
        }

        // 2. Scan Banner (real result after audit)
        item(key = "scan_banner") {
            AnimatedVisibility(visible = lastScanBanner != null) {
                lastScanBanner?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ShieldEmerald.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .border(1.dp, ShieldEmerald.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = ShieldEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissScanBanner() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Закрыть",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Active Scan Progress Card (only mounted while scanning)
        item(key = "scan_progress") {
            AnimatedVisibility(visible = isScanning) {
                SecurityCard(borderColor = ShieldCyan) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = ShieldCyan
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Хеширование APK и проверка разрешений",
                                style = MaterialTheme.typography.titleSmall,
                                color = ShieldCyan
                            )
                            if (totalToScan > 0) {
                                Text(
                                    text = "$scannedCount / $totalToScan пакетов",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = currentScanTarget,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ShieldSurfaceElevated, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { scanProgress.coerceIn(0f, 1f) },
                        color = ShieldCyan,
                        trackColor = ShieldSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        // 4. Real Scan Action Buttons
        item(key = "scan_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.runQuickAudit() },
                    enabled = !isScanning && !isLoadingApps,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShieldCyan,
                        contentColor = ShieldObsidian
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)
                        .testTag("quick_audit_button")
                ) {
                    Icon(Icons.Filled.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Быстрый аудит", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = { viewModel.runFullApkHashScan() },
                    enabled = !isScanning && !isLoadingApps,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ShieldEmerald.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ShieldEmerald),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)
                        .testTag("full_apk_scan_button")
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Проверка APK", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 5. Real SAF File / APK Inspector
        item(key = "file_inspector") {
            SecurityCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Инспектор файлов и APK",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Вычисляет SHA-256, MD5, проверяет подпись и права APK через системный проводник.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = ShieldCyan,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    enabled = !isInspectingFile,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ShieldCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ShieldCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("inspect_file_button")
                ) {
                    if (isInspectingFile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ShieldCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Вычисление хеша...")
                    } else {
                        Text("Выбрать файл или .apk для проверки", fontWeight = FontWeight.SemiBold)
                    }
                }

                fileInspectionError?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = ShieldCrimson)
                }

                fileInspectionResult?.let { result ->
                    Spacer(modifier = Modifier.height(12.dp))
                    FileInspectionResultView(
                        result = result,
                        onDismiss = { viewModel.clearFileInspection() }
                    )
                }
            }
        }

        // 6. Real Flagged Apps Section
        if (highRiskApps.isNotEmpty()) {
            item(key = "flagged_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ПРИЛОЖЕНИЯ С ПОВЫШЕННЫМ РИСКОМ (${highRiskApps.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = ShieldAmber
                    )
                    TextButton(onClick = onOpenAppsTab) {
                        Text("Все приложения", color = ShieldCyan, fontSize = 12.sp)
                    }
                }
            }

            items(
                items = highRiskApps,
                key = { "flagged_${it.packageName}" },
                contentType = { "flagged_app" }
            ) { app ->
                FlaggedAppSummaryCard(
                    app = app,
                    onOpenSettings = { SystemActions.openAppDetails(context, app.packageName) },
                    onUninstall = { SystemActions.requestUninstall(context, app.packageName) },
                    onTrust = { viewModel.addTrustedApp(app.packageName, app.appName) }
                )
            }
        }

        // 7. Device OS Security Posture Checklist
        posture?.let { report ->
            item(key = "posture_header") {
                Text(
                    text = "СОСТОЯНИЕ БЕЗОПАСНОСТИ УСТРОЙСТВА",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            items(
                items = report.checks,
                key = { "posture_${it.id}" },
                contentType = { "posture_check" }
            ) { check ->
                PostureCheckRow(
                    item = check,
                    onOpenSettings = check.settingsAction?.let { action ->
                        { SystemActions.openSettingsScreen(context, action) }
                    }
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FileInspectionResultView(
    result: FileInspectionResult,
    onDismiss: () -> Unit
) {
    val accent = if (result.isBlacklisted) ShieldCrimson else ShieldEmerald
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ShieldSurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (result.isBlacklisted) Icons.Filled.GppBad else Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (result.isBlacklisted) "СОВПАДЕНИЕ С БАЗОЙ УГРОЗ!" else "Угроз по базе сигнатур не найдено",
                    style = MaterialTheme.typography.titleSmall,
                    color = accent
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Скрыть", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Файл: ${result.displayName} (${formatBytes(result.sizeBytes)})", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("SHA-256: ${result.sha256}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text("MD5: ${result.md5}", style = MaterialTheme.typography.labelSmall, color = TextMuted)

        if (result.isApk) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = ShieldOutline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Пакет: ${result.apkPackageName} v${result.apkVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = ShieldCyan,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "SDK: min ${result.apkMinSdk ?: "?"} / target ${result.apkTargetSdk ?: "?"} • Разрешений: ${result.apkPermissions.size}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            result.apkCertSha256?.let { cert ->
                Text(
                    text = "Сертификат (SHA-256): ${cert.take(24)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            val criticalPerms = result.apkPermissions.filter { it.severityWeight > 0 }
            if (criticalPerms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Опасные разрешения: ${criticalPerms.joinToString { it.shortName }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ShieldAmber
                )
            }
        }
    }
}

@Composable
private fun FlaggedAppSummaryCard(
    app: AppAuditInfo,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit,
    onTrust: () -> Unit
) {
    val accent = riskColorFor(app.riskScore)
    SecurityCard(borderColor = accent.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconThumbnail(packageName = app.packageName, tintFallback = accent, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${app.installOrigin.label} • ${app.packageName}", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = "${app.riskScore}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        if (app.riskReasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.riskReasons.first(),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 42.dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Настройки", fontSize = 11.sp)
            }
            if (!app.isSystemApp) {
                OutlinedButton(
                    onClick = onUninstall,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ShieldCrimson.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ShieldCrimson),
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Удалить", fontSize = 11.sp)
                }
            }
            TextButton(
                onClick = onTrust,
                modifier = Modifier.heightIn(min = 42.dp)
            ) {
                Text("Доверять", color = ShieldEmerald, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PostureCheckRow(
    item: PostureCheckItem,
    onOpenSettings: (() -> Unit)?
) {
    val accent = when {
        item.isPassed -> ShieldEmerald
        item.isWarningOnly -> ShieldAmber
        else -> ShieldCrimson
    }
    SecurityCard(
        modifier = if (onOpenSettings != null) {
            Modifier.fillMaxWidth().clickable { onOpenSettings() }
        } else {
            Modifier.fillMaxWidth()
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.isPassed -> Icons.Filled.Shield
                        item.isWarningOnly -> Icons.Filled.Warning
                        else -> Icons.Filled.GppBad
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(item.valueText, style = MaterialTheme.typography.labelMedium, color = accent)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (onOpenSettings != null && !item.isPassed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Нажмите, чтобы открыть системные настройки →", style = MaterialTheme.typography.labelSmall, color = ShieldCyan)
                }
            }
        }
    }
}
