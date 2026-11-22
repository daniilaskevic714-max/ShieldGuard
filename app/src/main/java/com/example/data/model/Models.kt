package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scan_reports")
data class ScanReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val scanType: String, // "Быстрый аудит", "Проверка APK", "Инспекция файла"
    val appsScannedCount: Int,
    val findingsCount: Int,
    val durationMs: Long,
    val status: String // "Чисто", "Есть риски", "Найдена угроза"
)

@Entity(tableName = "trusted_apps")
data class TrustedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val addedDate: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "global_blacklist",
    indices = [Index(value = ["sha256_hash"], unique = true)]
)
data class GlobalBlacklist(
    @PrimaryKey val sha256_hash: String,
    val addedDate: Long = System.currentTimeMillis()
)

enum class SensitivityLevel(val key: String, val label: String) {
    LOW("Low", "Базовый"),
    MEDIUM("Medium", "Стандартный"),
    HIGH("High", "Строгий")
}

enum class InstallOrigin(val label: String) {
    GOOGLE_PLAY("Google Play"),
    SYSTEM_PREINSTALLED("Система"),
    VENDOR_STORE("Магазин OEM"),
    SIDELOADED("Сторонний APK"),
    UNKNOWN("Неизвестно")
}

@Immutable
data class PermissionItem(
    val fullName: String,
    val shortName: String,
    val descriptionRu: String,
    val isGranted: Boolean,
    val severityWeight: Int // 0 = normal, 1 = sensitive, 2 = critical
)

@Immutable
data class AppAuditInfo(
    val appName: String,
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val targetSdk: Int,
    val lastUpdateTime: Long,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean,
    val isSystemSigned: Boolean,
    val isDebuggable: Boolean,
    val allowBackup: Boolean,
    val installOrigin: InstallOrigin,
    val installerPackage: String?,
    val permissions: List<PermissionItem>,
    val riskScore: Int,
    val riskReasons: List<String>,
    val sha256Hash: String? = null,
    val isSignatureBlacklisted: Boolean = false
) {
    val grantedDangerousCount: Int
        get() = permissions.count { it.isGranted && it.severityWeight > 0 }
}

@Immutable
data class PostureCheckItem(
    val id: String,
    val title: String,
    val valueText: String,
    val description: String,
    val isPassed: Boolean,
    val isWarningOnly: Boolean = false,
    val settingsAction: String? = null
)

@Immutable
data class DevicePostureReport(
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String,
    val deviceModel: String,
    val checks: List<PostureCheckItem>,
    val score: Int
)

@Immutable
data class FileInspectionResult(
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
    val md5: String,
    val isBlacklisted: Boolean,
    val isApk: Boolean,
    val apkPackageName: String? = null,
    val apkVersion: String? = null,
    val apkMinSdk: Int? = null,
    val apkTargetSdk: Int? = null,
    val apkCertSha256: String? = null,
    val apkPermissions: List<PermissionItem> = emptyList(),
    val inspectionDurationMs: Long = 0L
)

@Immutable
data class SignatureSyncState(
    val count: Int = 0,
    val lastSyncTimeMs: Long = 0L,
    val isSyncing: Boolean = false,
    val lastError: String? = null
)
