package com.example.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.example.data.model.AppAuditInfo
import com.example.data.model.InstallOrigin
import com.example.data.model.PermissionItem
import com.example.data.model.SensitivityLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object AppSecurityAnalyzer {

    private data class CachedHash(
        val key: String,
        val sha256: String
    )

    // Keyed by packageName -> CachedHash(lastUpdateTime:size, sha256)
    private val apkHashCache = ConcurrentHashMap<String, CachedHash>()

    private data class PermSpec(
        val shortName: String,
        val descriptionRu: String,
        val severity: Int // 1 = sensitive, 2 = critical
    )

    private val trackedPermissions: Map<String, PermSpec> = mapOf(
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to PermSpec(
            "BIND_ACCESSIBILITY_SERVICE",
            "Чтение содержимого экрана и управление нажатиями",
            2
        ),
        "android.permission.BIND_DEVICE_ADMIN" to PermSpec(
            "BIND_DEVICE_ADMIN",
            "Права администратора устройства",
            2
        ),
        "android.permission.SYSTEM_ALERT_WINDOW" to PermSpec(
            "SYSTEM_ALERT_WINDOW",
            "Отображение окон поверх других приложений",
            2
        ),
        "android.permission.REQUEST_INSTALL_PACKAGES" to PermSpec(
            "REQUEST_INSTALL_PACKAGES",
            "Запрос установки сторонних APK-пакетов",
            2
        ),
        "android.permission.WRITE_SETTINGS" to PermSpec(
            "WRITE_SETTINGS",
            "Изменение системных настроек",
            1
        ),
        "android.permission.PACKAGE_USAGE_STATS" to PermSpec(
            "PACKAGE_USAGE_STATS",
            "Отслеживание активности других приложений",
            1
        ),
        "android.permission.READ_SMS" to PermSpec(
            "READ_SMS",
            "Чтение входящих SMS-сообщений",
            2
        ),
        "android.permission.RECEIVE_SMS" to PermSpec(
            "RECEIVE_SMS",
            "Перехват новых SMS",
            2
        ),
        "android.permission.SEND_SMS" to PermSpec(
            "SEND_SMS",
            "Отправка SMS-сообщений",
            2
        ),
        "android.permission.READ_CALL_LOG" to PermSpec(
            "READ_CALL_LOG",
            "Доступ к журналу вызовов",
            1
        ),
        "android.permission.RECORD_AUDIO" to PermSpec(
            "RECORD_AUDIO",
            "Запись звука с микрофона",
            1
        ),
        "android.permission.CAMERA" to PermSpec(
            "CAMERA",
            "Доступ к камере",
            1
        ),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to PermSpec(
            "ACCESS_BACKGROUND_LOCATION",
            "Отслеживание геолокации в фоне",
            2
        ),
        "android.permission.ACCESS_FINE_LOCATION" to PermSpec(
            "ACCESS_FINE_LOCATION",
            "Точная геолокация (GPS)",
            1
        ),
        "android.permission.READ_CONTACTS" to PermSpec(
            "READ_CONTACTS",
            "Чтение адресной книги",
            1
        ),
        "android.permission.READ_PHONE_STATE" to PermSpec(
            "READ_PHONE_STATE",
            "Чтение статуса телефона и идентификаторов",
            1
        )
    )

    fun describePermission(fullName: String, isGranted: Boolean): PermissionItem {
        val spec = trackedPermissions[fullName]
        return if (spec != null) {
            PermissionItem(
                fullName = fullName,
                shortName = spec.shortName,
                descriptionRu = spec.descriptionRu,
                isGranted = isGranted,
                severityWeight = spec.severity
            )
        } else {
            PermissionItem(
                fullName = fullName,
                shortName = fullName.removePrefix("android.permission."),
                descriptionRu = "Стандартное разрешение",
                isGranted = isGranted,
                severityWeight = 0
            )
        }
    }

    suspend fun auditInstalledApps(
        context: Context,
        sensitivity: SensitivityLevel,
        blacklistedHashes: Set<String>
    ): List<AppAuditInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val packages = loadInstalledPackagesSafely(pm)
        val platformCertSha256 = getPlatformCertSha256(pm)

        val result = ArrayList<AppAuditInfo>(packages.size)
        for (pkgInfo in packages) {
            val appInfo = pkgInfo.applicationInfo ?: continue
            val pkgName = pkgInfo.packageName ?: continue
            if (pkgName == selfPkg) continue

            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val label = try {
                appInfo.loadLabel(pm).toString().ifBlank { pkgName }
            } catch (_: Exception) {
                pkgName
            }

            val installerPkg = getInstallerPackageCompat(pm, pkgName)
            val installOrigin = classifyOrigin(isSystemApp, installerPkg)

            // Only fetch system-signing check for system-dir or updated-system apps or fast cache; avoid calling getPackageInfo per app unless needed
            val isSystemSigned = isSystemApp && (
                pkgName.startsWith("com.android.") ||
                    pkgName.startsWith("com.google.android.") ||
                    (platformCertSha256 != null && (appInfo.sourceDir?.startsWith("/system/") == true || appInfo.sourceDir?.startsWith("/product/") == true))
                )

            val isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val allowBackup = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
            val targetSdk = appInfo.targetSdkVersion
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            val apkSizeBytes = appInfo.sourceDir?.let { path ->
                try {
                    File(path).length()
                } catch (_: Exception) {
                    0L
                }
            } ?: 0L

            val cacheKey = "${pkgInfo.lastUpdateTime}:$apkSizeBytes"
            val cachedHash = apkHashCache[pkgName]?.takeIf { it.key == cacheKey }?.sha256
            val isBlacklisted = cachedHash != null && blacklistedHashes.contains(cachedHash)

            val permissionItems = buildPermissionItems(pkgInfo)
            val (riskScore, riskReasons) = evaluateRisk(
                isSystemApp = isSystemApp,
                isDebuggable = isDebuggable,
                allowBackup = allowBackup,
                targetSdk = targetSdk,
                installOrigin = installOrigin,
                permissions = permissionItems,
                isBlacklisted = isBlacklisted,
                sensitivity = sensitivity
            )

            result.add(
                AppAuditInfo(
                    appName = label,
                    packageName = pkgName,
                    version = pkgInfo.versionName ?: "1.0",
                    versionCode = versionCode,
                    targetSdk = targetSdk,
                    lastUpdateTime = pkgInfo.lastUpdateTime,
                    apkSizeBytes = apkSizeBytes,
                    isSystemApp = isSystemApp,
                    isSystemSigned = isSystemSigned,
                    isDebuggable = isDebuggable,
                    allowBackup = allowBackup,
                    installOrigin = installOrigin,
                    installerPackage = installerPkg,
                    permissions = permissionItems,
                    riskScore = riskScore,
                    riskReasons = riskReasons,
                    sha256Hash = cachedHash,
                    isSignatureBlacklisted = isBlacklisted
                )
            )
        }

        result.sortedWith(compareByDescending<AppAuditInfo> { it.riskScore }.thenBy { it.appName.lowercase() })
    }

    /**
     * Computes real SHA-256 hashes of non-system (and updated system) APKs in parallel
     * with bounded concurrency, leveraging the in-memory cache for unchanged packages.
     */
    suspend fun scanUserApksWithHashes(
        context: Context,
        apps: List<AppAuditInfo>,
        blacklistedHashes: Set<String>,
        sensitivity: SensitivityLevel,
        onProgress: (scanned: Int, total: Int, currentAppName: String) -> Unit
    ): List<AppAuditInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val targets = apps.filter { !it.isSystemApp }
        if (targets.isEmpty()) return@withContext apps

        val semaphore = Semaphore(4)
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val total = targets.size

        val updatedMap = coroutineScope {
            targets.map { app ->
                async {
                    semaphore.withPermit {
                        val sourceDir = try {
                            pm.getApplicationInfo(app.packageName, 0).sourceDir
                        } catch (_: Exception) {
                            null
                        }

                        val cacheKey = "${app.lastUpdateTime}:${app.apkSizeBytes}"
                        val cached = apkHashCache[app.packageName]?.takeIf { it.key == cacheKey }?.sha256
                        val hash = cached ?: if (sourceDir != null) {
                            computeFileSha256(File(sourceDir))?.also { computed ->
                                apkHashCache[app.packageName] = CachedHash(cacheKey, computed)
                            }
                        } else {
                            null
                        }

                        val isBlacklisted = hash != null && blacklistedHashes.contains(hash)
                        val (newScore, newReasons) = evaluateRisk(
                            isSystemApp = app.isSystemApp,
                            isDebuggable = app.isDebuggable,
                            allowBackup = app.allowBackup,
                            targetSdk = app.targetSdk,
                            installOrigin = app.installOrigin,
                            permissions = app.permissions,
                            isBlacklisted = isBlacklisted,
                            sensitivity = sensitivity
                        )

                        val done = completedCount.incrementAndGet()
                        onProgress(done, total, "${app.appName} (${app.packageName})")

                        app.packageName to app.copy(
                            sha256Hash = hash,
                            isSignatureBlacklisted = isBlacklisted,
                            riskScore = newScore,
                            riskReasons = newReasons
                        )
                    }
                }
            }.awaitAll().toMap()
        }

        apps.map { updatedMap[it.packageName] ?: it }
            .sortedWith(compareByDescending<AppAuditInfo> { it.riskScore }.thenBy { it.appName.lowercase() })
    }

    private fun buildPermissionItems(pkgInfo: PackageInfo): List<PermissionItem> {
        val names = pkgInfo.requestedPermissions ?: return emptyList()
        val flags = pkgInfo.requestedPermissionsFlags

        val items = ArrayList<PermissionItem>(names.size)
        for (i in names.indices) {
            val permName = names[i] ?: continue
            val isGranted = flags != null &&
                i < flags.size &&
                (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            val item = describePermission(permName, isGranted)
            // Keep tracked sensitive/critical permissions first, plus granted ones
            if (item.severityWeight > 0 || isGranted) {
                items.add(item)
            }
        }

        return items.sortedWith(
            compareByDescending<PermissionItem> { it.severityWeight }
                .thenByDescending { it.isGranted }
                .thenBy { it.shortName }
        )
    }

    private fun evaluateRisk(
        isSystemApp: Boolean,
        isDebuggable: Boolean,
        allowBackup: Boolean,
        targetSdk: Int,
        installOrigin: InstallOrigin,
        permissions: List<PermissionItem>,
        isBlacklisted: Boolean,
        sensitivity: SensitivityLevel
    ): Pair<Int, List<String>> {
        if (isBlacklisted) {
            return 100 to listOf("SHA-256 файла совпал с базой вредоносных сигнатур")
        }

        val reasons = ArrayList<String>()
        var score = 0

        if (!isSystemApp) {
            when (installOrigin) {
                InstallOrigin.SIDELOADED -> {
                    score += 20
                    reasons.add("Установлено вручную (сторонний APK, вне магазина)")
                }
                InstallOrigin.UNKNOWN -> {
                    score += 12
                    reasons.add("Источник установки не определен")
                }
                else -> Unit
            }

            if (isDebuggable) {
                score += 30
                reasons.add("Сборка с флагом отладки (FLAG_DEBUGGABLE)")
            }

            if (targetSdk in 1..27) {
                score += 10
                reasons.add("Устаревший targetSdk ($targetSdk) — обход современных ограничений Android")
            }

            if (allowBackup && sensitivity == SensitivityLevel.HIGH) {
                score += 5
                reasons.add("Разрешено резервное копирование данных приложения (allowBackup)")
            }
        }

        val multiplier = when (sensitivity) {
            SensitivityLevel.LOW -> 0.65f
            SensitivityLevel.MEDIUM -> 1.0f
            SensitivityLevel.HIGH -> 1.35f
        }

        var permPoints = 0
        val grantedCritical = permissions.filter { it.isGranted && it.severityWeight == 2 }
        val grantedSensitive = permissions.filter { it.isGranted && it.severityWeight == 1 }
        val declaredCritical = permissions.filter { !it.isGranted && it.severityWeight == 2 }

        if (grantedCritical.isNotEmpty()) {
            permPoints += (grantedCritical.size * 18)
            reasons.add("Выданы критические права: ${grantedCritical.joinToString { it.shortName }}")
        }
        if (grantedSensitive.isNotEmpty()) {
            permPoints += (grantedSensitive.size * 7)
            if (grantedSensitive.size >= 2 || sensitivity == SensitivityLevel.HIGH) {
                reasons.add("Выданы доступы к личным данным: ${grantedSensitive.joinToString { it.shortName }}")
            }
        }
        if (declaredCritical.isNotEmpty() && !isSystemApp) {
            permPoints += (declaredCritical.size * 6)
            reasons.add("Запрашивает особые права: ${declaredCritical.joinToString { it.shortName }}")
        }

        // System apps don't get flagged purely for standard OS permissions unless debuggable
        val weightedPermScore = if (isSystemApp) {
            (permPoints * 0.25f).toInt().coerceAtMost(25)
        } else {
            (permPoints * multiplier).toInt().coerceAtMost(65)
        }

        score += weightedPermScore

        return score.coerceIn(0, 99) to reasons
    }

    private fun loadInstalledPackagesSafely(pm: PackageManager): List<PackageInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
        } catch (_: Exception) {
            // Fallback for Android 7-12 devices hitting Binder TransactionTooLargeException on huge app lists
            try {
                val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(0)
                }
                apps.mapNotNull { appInfo ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(
                                appInfo.packageName,
                                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun getInstallerPackageCompat(pm: PackageManager, packageName: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun classifyOrigin(isSystemApp: Boolean, installerPkg: String?): InstallOrigin {
        if (isSystemApp) return InstallOrigin.SYSTEM_PREINSTALLED
        return when (installerPkg) {
            "com.android.vending" -> InstallOrigin.GOOGLE_PLAY
            "com.sec.android.app.samsungapps",
            "com.xiaomi.market",
            "com.huawei.appmarket",
            "com.oppo.market",
            "com.vivo.appstore",
            "com.amazon.venezia" -> InstallOrigin.VENDOR_STORE
            null,
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller" -> InstallOrigin.SIDELOADED
            else -> InstallOrigin.UNKNOWN
        }
    }

    private fun getPlatformCertSha256(pm: PackageManager): String? {
        return try {
            val sig: Signature? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        "android",
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo("android", PackageManager.GET_SIGNING_CERTIFICATES)
                }
                pkg.signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo("android", PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            }
            sig?.let { sha256Hex(it.toByteArray()) }
        } catch (_: Exception) {
            null
        }
    }

    fun computeFileSha256(file: File): String? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65_536) // 64KB buffer for fast streaming
            FileInputStream(file).use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
