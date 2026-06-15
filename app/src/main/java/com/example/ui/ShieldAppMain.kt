package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BlockedWebsite
import com.example.data.model.QuarantinedItem
import com.example.data.model.ScanReport
import com.example.data.repository.AppSecurityInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldAppMain(viewModel: ShieldViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Listen to notification screen landing redirection
    LaunchedEffect(key1 = true) {
        // Redirection parameters can be picked up if passed from MainActivity intent extras
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Shield",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ShieldGuard Antivirus",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Security, contentDescription = "Защита") },
                    label = { Text("Защита", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Apps, contentDescription = "Аудит") },
                    label = { Text("Приложения", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.History, contentDescription = "Карантин") },
                    label = { Text("Карантин", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Опции") },
                    label = { Text("Настройки", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> ScannerHubTab(viewModel)
                1 -> SecurityAuditTab(viewModel)
                2 -> QuarantineLogsTab(viewModel)
                3 -> ConfigurationTab(viewModel)
            }
        }
    }
}

// --------------------------------- DASHBOARD & SCANNER TAB ---------------------------------
@Composable
fun ScannerHubTab(viewModel: ShieldViewModel) {
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scannedFilesCount by viewModel.scannedFilesCount.collectAsStateWithLifecycle()
    val currentScannedFile by viewModel.currentScannedFile.collectAsStateWithLifecycle()
    val activeThreatsFound by viewModel.activeThreatsFound.collectAsStateWithLifecycle()
    
    val cacheState by viewModel.cacheState.collectAsStateWithLifecycle()
    val isCleaning by viewModel.isCleaning.collectAsStateWithLifecycle()
    val clearedAmount by viewModel.clearedAmount.collectAsStateWithLifecycle()
    
    val isLowBatteryModeActive by viewModel.isLowBatteryModeActive.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Status Shield Indicator Card
        item {
            ShieldPulseIndicator(
                isScanning = isScanning,
                progress = scanProgress,
                threatsFoundCount = activeThreatsFound.size,
                scannedCount = scannedFilesCount,
                isLowBattery = isLowBatteryModeActive
            )
        }

        // Active Scanning info Panel
        if (isScanning) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Сканирование файлов в реальном времени...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentScannedFile,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }

        // Actions panel (Scanning buttons)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.runScan(isDeep = false) },
                    enabled = !isScanning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("fast_scan_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.FlashOn, contentDescription = "Быстрое сканирование")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Быстрое")
                }

                Button(
                    onClick = { viewModel.runScan(isDeep = true) },
                    enabled = !isScanning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("deep_scan_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = "Глубокая проверка")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Глубокое")
                }
            }
        }

        // Threats discovered inside the current running Scan
        if (activeThreatsFound.isNotEmpty()) {
            item {
                Text(
                    text = "⚠️ Обнаружены угрозы (${activeThreatsFound.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            items(activeThreatsFound) { threat ->
                Card(
                     modifier = Modifier.fillMaxWidth(),
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                     border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = "Malware", tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(threat.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Тип угрозы: ${threat.threatType}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                                Text("Путь: ${threat.filePath}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { viewModel.quarantineThreat(threat) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.Security, contentDescription = "Карантин")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("В Карантин (Реком.)")
                            }
                        }
                    }
                }
            }
        }

        // Cache Cleaner Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Очистка кэша и мусора",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Повышение производительности и удаление следов",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Trash icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val computedData = cacheState
                    if (computedData != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Найдено кэш-файлов:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(computedData.formattedMb, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }

                            Button(
                                onClick = { viewModel.cleanCache() },
                                enabled = !isCleaning && computedData.rawBytes > 0,
                                modifier = Modifier.testTag("clean_cache_button")
                            ) {
                                if (isCleaning) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CleaningServices, contentDescription = "🧹")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Очистить")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Cache items breakdown details
                        computedData.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(item.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(item.formattedSize, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }

                    AnimatedVisibility(visible = clearedAmount != null) {
                        clearedAmount?.let { amount ->
                            Text(
                                text = "✨ Успешно освобождено ${String.format("%.2f MB", amount)}!",
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShieldPulseIndicator(
    isScanning: Boolean,
    progress: Float,
    threatsFoundCount: Int,
    scannedCount: Int,
    isLowBattery: Boolean
) {
    val animateColor by animateColorAsState(
        targetValue = when {
            threatsFoundCount > 0 -> MaterialTheme.colorScheme.error
            isScanning -> MaterialTheme.colorScheme.primary
            else -> Color(0xFF4CAF50)
        },
        label = "ShieldColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = animateColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, animateColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .drawBehind {
                        if (isScanning && !isLowBattery) {
                            // Pulsing radar ripple circles under shield icon
                            drawCircle(
                                color = animateColor.copy(alpha = 0.15f),
                                radius = size.minDimension / 1.5f + (progress * 15f)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = animateColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(100.dp)
                ) {
                    Icon(
                        imageVector = when {
                            threatsFoundCount > 0 -> Icons.Filled.ReportProblem
                            isScanning -> Icons.Filled.WifiProtectedSetup
                            else -> Icons.Filled.VerifiedUser
                        },
                        contentDescription = "Shield Guard Indicator Logo",
                        tint = animateColor,
                        modifier = Modifier
                            .size(52.dp)
                            .padding(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when {
                    threatsFoundCount > 0 -> "НАЙДЕНЫ УГРОЗЫ!"
                    isScanning -> "СКАНИРОВАНИЕ... ${scannedCount} файлов"
                    else -> "СИСТЕМА БЕЗОПАСНА"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = animateColor,
                textAlign = TextAlign.Center
            )

            Text(
                text = when {
                    threatsFoundCount > 0 -> "Срочно отправьте подозрительные файлы в карантин или удалите"
                    isScanning -> "Проверка установленных APK пакетов, КЭШ данных, и загрузок..."
                    else -> "Защита в реальном времени включена. Проверено загрузок: $scannedCount"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}


// --------------------------------- WEB & APP PERMISSIONS TAB ---------------------------------
@Composable
fun SecurityAuditTab(viewModel: ShieldViewModel) {
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val websitesList by viewModel.websitesList.collectAsStateWithLifecycle()
    val webTestResult by viewModel.webTestResult.collectAsStateWithLifecycle()
    val trustedAppsList by viewModel.trustedAppsList.collectAsStateWithLifecycle()
    
    var showAddWebPrompt by remember { mutableStateOf(false) }
    var newWebDomain by remember { mutableStateOf("") }
    var testWebDomain by remember { mutableStateOf("") }

    var auditSearchQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // App Audits Title & Search
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🛡️ Мониторинг разрешений приложений", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Выявление скрытых угроз: рекламного ПО, троянов и трекеров.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = auditSearchQuery,
                        onValueChange = { auditSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск установленного приложения...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true
                    )
                }
            }
        }

        val filteredApps = installedApps.filter {
            it.appName.contains(auditSearchQuery, ignoreCase = true) || it.packageName.contains(auditSearchQuery, ignoreCase = true)
        }

        items(filteredApps) { app ->
            val isTrusted = trustedAppsList.any { it.packageName == app.packageName }
            AppSecurityRowItem(
                app = app,
                isTrusted = isTrusted,
                onToggleTrust = {
                    if (isTrusted) {
                        viewModel.removeTrustedAppByPackage(app.packageName)
                    } else {
                        viewModel.addTrustedApp(app.packageName, app.appName)
                    }
                },
                onSimulateEntrance = { viewModel.runSuspiciousAppTrigger(app.appName, app.packageName) }
            )
        }

        // Web Filter Module
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🌐 Блокировка опасных сайтов (Firewall)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Блокирует подозрительные, фишинговые и рекламные веб-ресурсы локально.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Web tester helper
                    OutlinedTextField(
                        value = testWebDomain,
                        onValueChange = {
                            testWebDomain = it
                            if (it.isBlank()) viewModel.clearWebTestResult()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Проверить подозрительный сайт (например, adware-miner.org)") },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.testWebDomain(testWebDomain) }) {
                                Icon(Icons.Filled.NetworkCheck, contentDescription = "Проверить")
                            }
                        },
                        singleLine = true
                    )

                    AnimatedVisibility(visible = webTestResult != null) {
                        webTestResult?.let { result ->
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.inverseOnSurface, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text(result, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Blacklisted sites list
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("База заблокированных сайтов (${websitesList.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showAddWebPrompt = !showAddWebPrompt }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Website rule")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Добавить сайт")
                        }
                    }

                    if (showAddWebPrompt) {
                        OutlinedTextField(
                            value = newWebDomain,
                            onValueChange = { newWebDomain = it },
                            placeholder = { Text("Ведите домен, например: spammer-site.ru") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (newWebDomain.isNotBlank()) {
                                        viewModel.addWebRule(newWebDomain)
                                        newWebDomain = ""
                                        showAddWebPrompt = false
                                    }
                                }) {
                                    Icon(Icons.Filled.Check, contentDescription = "Подтвердить")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    websitesList.forEach { site ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Cancel, contentDescription = "Blocked icon", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(site.url, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(site.threatType, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { viewModel.removeWebRule(site) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete target rule", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppSecurityRowItem(
    app: AppSecurityInfo,
    isTrusted: Boolean,
    onToggleTrust: () -> Unit,
    onSimulateEntrance: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val statusColor = when {
        isTrusted -> Color(0xFF4CAF50)
        app.riskScore >= 75 -> MaterialTheme.colorScheme.error
        app.riskScore >= 50 -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        border = if (isTrusted) {
            BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
        } else if (app.riskScore >= 70) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        } else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(statusColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isTrusted) {
                            Icons.Filled.Verified
                        } else if (app.riskScore >= 60) {
                            Icons.Filled.BugReport
                        } else {
                            Icons.Filled.Android
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isTrusted) "Доверенное" else "${app.riskScore}% Риск",
                        color = statusColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                    Text(
                        if (app.isSystemApp) "Системное" else "Пользовательское",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isTrusted) "Статус: Добавлено в белый список (Одобрено пользователем)" else "Выявленный характер угрозы: ${app.threatType}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text("Версия: ${app.version}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    if (app.isInstalledFromPlayStore || app.isSystemSigned) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFF4CAF50).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (app.isInstalledFromPlayStore) {
                                    "Репутация: Установлено из Google Play Store (Исключено из угроз)"
                                } else {
                                    "Репутация: Доверенная системная подпись (Исключено из угроз)"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Запрашиваемые разрешения службы приложения:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    if (app.permissions.isEmpty()) {
                        Text("Особо опасных системных разрешений не найдено.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        app.permissions.forEach { perm ->
                            Text(
                                "• ${perm.removePrefix("android.permission.")}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (perm.contains("SMS") || perm.contains("ALERT") || perm.contains("READ_CONTACTS")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSimulateEntrance,
                            modifier = Modifier.weight(1.1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Симулировать", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Проверить", fontSize = 11.sp)
                        }

                        Button(
                            onClick = onToggleTrust,
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTrusted) MaterialTheme.colorScheme.error.copy(alpha = 0.82f) else Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(
                                imageVector = if (isTrusted) Icons.Filled.RemoveModerator else Icons.Filled.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTrusted) "Убрать доверие" else "В белый список",
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


// --------------------------------- QUARANTINE & LOGS TAB ---------------------------------
@Composable
fun QuarantineLogsTab(viewModel: ShieldViewModel) {
    val reportsList by viewModel.reportsList.collectAsStateWithLifecycle()
    val quarantineList by viewModel.quarantineList.collectAsStateWithLifecycle()
    
    var viewSelection by remember { mutableStateOf(0) } // 0: Quarantine, 1: Scan History Logs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = viewSelection == 0,
                onClick = { viewSelection = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.HealthAndSafety, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Карантин (${quarantineList.size})")
                }
            }
            SegmentedButton(
                selected = viewSelection == 1,
                onClick = { viewSelection = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("История отчетов")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewSelection == 0) {
            // Quarantine list
            if (quarantineList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AllInbox, contentDescription = "Empty quarantine", modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("В карантине пусто", fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("Все загрузки и установленные файлы чисты.", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(quarantineList) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Угроза: ${item.threatType}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        Text("Дата: ${formatDate(item.detectedDate)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Путь: ${item.filePath}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { viewModel.restoreQuarantine(item) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Filled.SettingsBackupRestore, contentDescription = "Restore")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Восстановить")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.deleteQuarantine(item) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Filled.DeleteForever, contentDescription = "Delete permanently")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Удалить насовсем")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Scan History Reports
            if (reportsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Empty reports", modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Нет записей проверок", fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("Запустите сканирование на главной, чтобы наполнить лог.", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.clearAllReports() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear all logs")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Очистить историю")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(reportsList) { report ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when (report.status) {
                                                "Secure" -> Icons.Filled.CheckCircle
                                                "Cleaned" -> Icons.Filled.AutoFixHigh
                                                else -> Icons.Filled.ErrorOutline
                                            },
                                            contentDescription = null,
                                            tint = when (report.status) {
                                                "Secure" -> Color(0xFF4CAF50)
                                                "Cleaned" -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(report.scanType, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = when (report.status) {
                                                    "Secure" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                                    "Cleaned" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            report.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (report.status) {
                                                "Secure" -> Color(0xFF4CAF50)
                                                "Cleaned" -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Проверено файлов", fontSize = 10.sp, color = Color.Gray)
                                        Text("${report.filesScannedCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Найдено угроз (вирусов)", fontSize = 10.sp, color = Color.Gray)
                                        Text("${report.threatsFoundCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (report.threatsFoundCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                    }
                                    if (report.cacheCleanedMb > 0f) {
                                        Column {
                                            Text("Освобождено кэша", fontSize = 10.sp, color = Color.Gray)
                                            Text(String.format("%.2f MB", report.cacheCleanedMb), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Дата выполнения: ${formatDate(report.timestamp)}",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --------------------------------- CONFIGURATION & REALT-TIME ALERTS TAB ---------------------------------
@Composable
fun ConfigurationTab(viewModel: ShieldViewModel) {
    val rScannerActive by viewModel.isRealTimeScannerActive.collectAsStateWithLifecycle()
    val rWebAlrtEnabled by viewModel.isRiskNotificationsEnabled.collectAsStateWithLifecycle()
    val rBackAutoScan by viewModel.areBackgroundScansEnabled.collectAsStateWithLifecycle()
    val isLowBatteryModeActive by viewModel.isLowBatteryModeActive.collectAsStateWithLifecycle()

    var customMalwareFileName by remember { mutableStateOf("premium_cheats_crack.apk") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚙️ Настройки и Безопасность", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Тонкая настройка работы антивирусного защитника ShieldGuard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Realtime switch toggler
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Сканирование файлов в реальном времени", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Фоновая служба мониторит все загружаемые файлы в папке загрузок.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = rScannerActive,
                            onCheckedChange = { viewModel.toggleRealTimeScanning(it) },
                            modifier = Modifier.testTag("real_time_scan_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Customizable Notification Alerts Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Предупреждающие уведомления безопасности", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Показывать немедленные всплывающие алерты об обнаружении adware или входе в опасный софт.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = rWebAlrtEnabled,
                            onCheckedChange = { viewModel.isRiskNotificationsEnabled.value = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Battery efficiency switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Энергосбережение и низкий расход заряда", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Оптимизировать частоту опроса процессора при сканировании, увеличивая время работы батареи.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isLowBatteryModeActive,
                            onCheckedChange = { viewModel.isLowBatteryModeActive.value = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Scanner Sensitivity Option
                    val currentSensitivity by viewModel.scannerSensitivity.collectAsStateWithLifecycle()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("🛡️ Уровень чувствительности сканера", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Регулируйте строгость поведенческого анализа и допуски для системных инструментов, чтобы предотвратить ложные срабатывания.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Low" to "Низкий", "Medium" to "Средний", "High" to "Высокий").forEach { (level, title) ->
                                val isSelected = currentSensitivity == level
                                val containerColor = if (isSelected) {
                                    when (level) {
                                        "Low" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                        "High" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    }
                                } else {
                                    Color.Transparent
                                }
                                val contentColor = if (isSelected) {
                                    when (level) {
                                        "Low" -> Color(0xFF2E7D32)
                                        "High" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val borderColor = if (isSelected) contentColor else MaterialTheme.colorScheme.outline
                                
                                OutlinedButton(
                                    onClick = { viewModel.setScannerSensitivity(level) },
                                    modifier = Modifier.weight(1f).testTag("sensitivity_${level.lowercase()}"),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = containerColor,
                                        contentColor = contentColor
                                    ),
                                    border = BorderStroke(1.dp, borderColor)
                                ) {
                                    Text(title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Whitelist Block (Trusted Applications)
        item {
            val trustedApps by viewModel.trustedAppsList.collectAsStateWithLifecycle()
            var showAddAppDialog by remember { mutableStateOf(false) }
            var manualAppName by remember { mutableStateOf("") }
            var manualPkgName by remember { mutableStateOf("") }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🛡️ Доверенные приложения / Белый список", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Исключенные приложения не будут помечаться как угроза при проверке.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    if (trustedApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Список пуст", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text("Добавьте приложения через вкладку «Приложения» или вручную.", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        trustedApps.forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(app.appName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { viewModel.removeTrustedApp(app) }) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Удалить из белого списка",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!showAddAppDialog) {
                        OutlinedButton(
                            onClick = { showAddAppDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Добавить приложение вручную", fontSize = 12.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text("Добавить в белый список", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualAppName,
                                onValueChange = { manualAppName = it },
                                label = { Text("Название приложения", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = manualPkgName,
                                onValueChange = { manualPkgName = it },
                                label = { Text("Имя пакета (например, com.my.app)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    showAddAppDialog = false
                                    manualAppName = ""
                                    manualPkgName = ""
                                }) {
                                    Text("Отмена")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (manualAppName.isNotBlank() && manualPkgName.isNotBlank()) {
                                            viewModel.addTrustedApp(manualPkgName.trim(), manualAppName.trim())
                                            manualAppName = ""
                                            manualPkgName = ""
                                            showAddAppDialog = false
                                        }
                                    },
                                    enabled = manualAppName.isNotBlank() && manualPkgName.isNotBlank()
                                ) {
                                    Text("Добавить")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live threat alerts simulation (To prove compliance under constraints instantly)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Terminal, contentDescription = "Sim logo", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Лаборатория угроз (Интерактивные проверки)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Используйте эти кнопки, чтобы смоделировать наступление реальных угроз и мгновенно увидеть блокировку и алерты до запуска.", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = customMalwareFileName,
                        onValueChange = { customMalwareFileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Имя тестового файла для скачивания") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.runMaliciousDownloadTrigger(customMalwareFileName) },
                            modifier = Modifier.weight(1f).testTag("simulate_malicious_download"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Скачать Вирус", fontSize = 11.sp)
                        }

                        ElevatedButton(
                            onClick = { viewModel.runSafeDownloadTrigger("clean_doc_archive.pdf") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.elevatedButtonColors(contentColor = Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Filled.FileDownloadOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Скачать Чистый", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.runSuspiciousAppTrigger("Калькулятор Плюс и СМС отправщик", "com.suspicious.calculator.adware") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Filled.Dangerous, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Симулировать вход в Опасное Приложение (Мгновенный Алерт)")
                    }
                }
            }
        }
    }
}

// Global Help formatting date
private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "Только что"
    }
}
