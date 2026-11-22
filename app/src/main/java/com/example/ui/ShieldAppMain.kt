package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AppsAuditTab
import com.example.ui.screens.DatabaseLogsTab
import com.example.ui.screens.OverviewTab
import com.example.ui.theme.ShieldCyan
import com.example.ui.theme.ShieldObsidian
import com.example.ui.theme.ShieldOutline
import com.example.ui.theme.ShieldSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class NavDestination(
    val index: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val destinations = listOf(
    NavDestination(0, "Обзор", Icons.Filled.Security, Icons.Outlined.Security, "nav_overview"),
    NavDestination(1, "Приложения", Icons.Filled.Apps, Icons.Outlined.Apps, "nav_apps"),
    NavDestination(2, "База и логи", Icons.Filled.Storage, Icons.Outlined.Storage, "nav_logs")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldAppMain(viewModel: ShieldViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ShieldObsidian)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ShieldObsidian,
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(ShieldCyan.copy(alpha = 0.14f), CircleShape)
                                    .border(1.dp, ShieldCyan.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "ShieldGuard",
                                    tint = ShieldCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ShieldGuard",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Аудитор безопасности и разрешений",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ShieldSurface,
                        titleContentColor = TextPrimary
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = ShieldSurface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        destinations.forEach { dest ->
                            val selected = selectedTab == dest.index
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = dest.index },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ShieldCyan,
                                    selectedTextColor = ShieldCyan,
                                    indicatorColor = ShieldCyan.copy(alpha = 0.14f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = ShieldSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .border(1.dp, ShieldOutline.copy(alpha = 0.4f))
                    ) {
                        Spacer(modifier = Modifier.size(12.dp))
                        destinations.forEach { dest ->
                            val selected = selectedTab == dest.index
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = dest.index },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, fontSize = 11.sp) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ShieldCyan,
                                    selectedTextColor = ShieldCyan,
                                    indicatorColor = ShieldCyan.copy(alpha = 0.14f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .widthIn(max = 840.dp)
                ) {
                    when (selectedTab) {
                        0 -> OverviewTab(
                            viewModel = viewModel,
                            onOpenAppsTab = { selectedTab = 1 }
                        )
                        1 -> AppsAuditTab(viewModel = viewModel)
                        else -> DatabaseLogsTab(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
