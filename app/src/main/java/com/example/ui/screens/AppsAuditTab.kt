package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppAuditInfo
import com.example.data.model.InstallOrigin
import com.example.ui.ShieldViewModel
import com.example.ui.components.AppIconThumbnail
import com.example.ui.components.SecurityCard
import com.example.ui.components.SystemActions
import com.example.ui.components.formatBytes
import com.example.ui.components.riskColorFor
import com.example.ui.theme.ShieldAmber
import com.example.ui.theme.ShieldCrimson
import com.example.ui.theme.ShieldCyan
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.ShieldOutline
import com.example.ui.theme.ShieldSurface
import com.example.ui.theme.ShieldSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private enum class AppFilterMode(val label: String) {
    ALL("Все"),
    RISKY("С риском"),
    SIDELOADED("Сторонние APK"),
    TRUSTED("Доверенные")
}

@Composable
fun AppsAuditTab(viewModel: ShieldViewModel) {
    val context = LocalContext.current
    val auditedApps by viewModel.auditedApps.collectAsStateWithLifecycle()
    val trustedApps by viewModel.trustedAppsList.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(AppFilterMode.ALL) }
    var includeSystemApps by remember { mutableStateOf(false) }

    val trustedPackages = remember(trustedApps) {
        trustedApps.mapTo(HashSet()) { it.packageName }
    }

    val filteredApps = remember(auditedApps, trustedPackages, searchQuery, filterMode, includeSystemApps) {
        auditedApps.filter { app ->
            if (!includeSystemApps && app.isSystemApp) return@filter false
            val isTrusted = trustedPackages.contains(app.packageName)
            val matchesFilter = when (filterMode) {
                AppFilterMode.ALL -> true
                AppFilterMode.RISKY -> !isTrusted && app.riskScore >= 45
                AppFilterMode.SIDELOADED -> app.installOrigin == InstallOrigin.SIDELOADED
                AppFilterMode.TRUSTED -> isTrusted
            }
            if (!matchesFilter) return@filter false
            if (searchQuery.isBlank()) return@filter true
            app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск по названию или package name...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ShieldCyan) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Очистить поиск", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ShieldCyan,
                unfocusedBorderColor = ShieldOutline,
                focusedContainerColor = ShieldSurface,
                unfocusedContainerColor = ShieldSurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppFilterMode.entries.forEach { mode ->
                FilterChip(
                    selected = filterMode == mode,
                    onClick = { filterMode = mode },
                    label = { Text(mode.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ShieldCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ShieldCyan
                    )
                )
            }
            FilterChip(
                selected = includeSystemApps,
                onClick = { includeSystemApps = !includeSystemApps },
                label = { Text("Системные", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ShieldAmber.copy(alpha = 0.2f),
                    selectedLabelColor = ShieldAmber
                )
            )
        }

        Text(
            text = "Показано приложений: ${filteredApps.size}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        if (filteredApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "По выбранным фильтрам ничего не найдено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                items(
                    items = filteredApps,
                    key = { it.packageName },
                    contentType = { "app_audit_row" }
                ) { app ->
                    val isTrusted = trustedPackages.contains(app.packageName)
                    AppAuditRowCard(
                        app = app,
                        isTrusted = isTrusted,
                        onOpenSettings = { SystemActions.openAppDetails(context, app.packageName) },
                        onUninstall = { SystemActions.requestUninstall(context, app.packageName) },
                        onToggleTrust = {
                            if (isTrusted) {
                                viewModel.removeTrustedAppByPackage(app.packageName)
                            } else {
                                viewModel.addTrustedApp(app.packageName, app.appName)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppAuditRowCard(
    app: AppAuditInfo,
    isTrusted: Boolean,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit,
    onToggleTrust: () -> Unit
) {
    var expanded by remember(app.packageName) { mutableStateOf(false) }
    val accent = riskColorFor(app.riskScore, isTrusted = isTrusted)

    SecurityCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        borderColor = if (expanded) accent.copy(alpha = 0.7f) else ShieldOutline
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconThumbnail(packageName = app.packageName, tintFallback = accent, size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${app.installOrigin.label} • Выдано опасных прав: ${app.grantedDangerousCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isTrusted) "ДОВЕРЕН" else "${app.riskScore}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Подробнее",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                HorizontalDivider(color = ShieldOutline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Версия: ${app.version} (${app.versionCode}) • targetSdk ${app.targetSdk} • ${formatBytes(app.apkSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                app.sha256Hash?.let { hash ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "APK SHA-256: $hash",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (app.isSignatureBlacklisted) ShieldCrimson else TextMuted
                    )
                }

                if (app.riskReasons.isNotEmpty() && !isTrusted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    app.riskReasons.forEach { reason ->
                        Text(
                            text = "• $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = accent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "РАЗРЕШЕНИЯ (${app.permissions.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (app.permissions.isEmpty()) {
                    Text(
                        text = "Чувствительные разрешения не запрашиваются.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ShieldSurfaceElevated, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        app.permissions.take(12).forEach { perm ->
                            val permColor = when {
                                perm.isGranted && perm.severityWeight == 2 -> ShieldCrimson
                                perm.isGranted && perm.severityWeight == 1 -> ShieldAmber
                                perm.severityWeight > 0 -> TextSecondary
                                else -> TextMuted
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(permColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = perm.shortName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = permColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = if (perm.isGranted) "ВЫДАНО" else "не выдано",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (perm.isGranted) permColor else TextMuted
                                )
                            }
                        }
                        if (app.permissions.size > 12) {
                            Text(
                                text = "+ ещё ${app.permissions.size - 12} разрешений",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Права", fontSize = 11.sp)
                    }
                    if (!app.isSystemApp) {
                        OutlinedButton(
                            onClick = onUninstall,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ShieldCrimson.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ShieldCrimson),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Удалить", fontSize = 11.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = onToggleTrust,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, (if (isTrusted) ShieldAmber else ShieldEmerald).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isTrusted) ShieldAmber else ShieldEmerald
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Icon(
                            imageVector = if (isTrusted) Icons.Filled.RemoveModerator else Icons.Filled.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isTrusted) "Убрать" else "Доверять", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
