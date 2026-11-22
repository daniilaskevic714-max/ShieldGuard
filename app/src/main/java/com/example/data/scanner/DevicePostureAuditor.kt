package com.example.data.scanner

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.data.model.AppAuditInfo
import com.example.data.model.DevicePostureReport
import com.example.data.model.InstallOrigin
import com.example.data.model.PostureCheckItem

object DevicePostureAuditor {

    fun inspect(context: Context, auditedApps: List<AppAuditInfo>, trustedPackages: Set<String>): DevicePostureReport {
        val resolver = context.contentResolver
        val checks = ArrayList<PostureCheckItem>(6)
        var score = 100

        // 1. Device Lock Screen (PIN / Pattern / Password / Biometric)
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = try {
            keyguard?.isDeviceSecure ?: false
        } catch (_: Exception) {
            false
        }
        if (!isDeviceSecure) score -= 25
        checks.add(
            PostureCheckItem(
                id = "lock_screen",
                title = "Блокировка экрана",
                valueText = if (isDeviceSecure) "Защищено" else "Не настроена",
                description = if (isDeviceSecure) {
                    "Устройство защищено системным паролем, графическим ключом или биометрией."
                } else {
                    "Отсутствует защита экрана блокировки. Любой может получить доступ к данным."
                },
                isPassed = isDeviceSecure,
                settingsAction = Settings.ACTION_SECURITY_SETTINGS
            )
        )

        // 2. USB Debugging (ADB)
        val adbEnabled = try {
            Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
        if (adbEnabled) score -= 15
        checks.add(
            PostureCheckItem(
                id = "adb_debug",
                title = "Отладка по USB (ADB)",
                valueText = if (adbEnabled) "Включена" else "Отключена",
                description = if (adbEnabled) {
                    "Активный режим ADB позволяет устанавливать пакеты и читать логи по USB."
                } else {
                    "Отладка по USB отключена, интерфейс ADB недоступен."
                },
                isPassed = !adbEnabled,
                isWarningOnly = true,
                settingsAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            )
        )

        // 3. Enabled Accessibility Services (Major Android banking trojan vector)
        val activeAccessibility = try {
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')
                ?.mapNotNull { it.substringBefore('/').takeIf { pkg -> pkg.isNotBlank() } }
                ?.distinct()
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val nonSystemAccessibility = activeAccessibility.filter { pkg ->
            !pkg.startsWith("com.android.") &&
                !pkg.startsWith("com.google.android.") &&
                !pkg.startsWith("com.samsung.") &&
                !trustedPackages.contains(pkg)
        }
        val accessibilityClean = nonSystemAccessibility.isEmpty()
        if (!accessibilityClean) score -= 15
        checks.add(
            PostureCheckItem(
                id = "accessibility_services",
                title = "Службы спец. возможностей",
                valueText = if (activeAccessibility.isEmpty()) {
                    "Нет активных"
                } else if (accessibilityClean) {
                    "Только системные (${activeAccessibility.size})"
                } else {
                    "Сторонние: ${nonSystemAccessibility.size}"
                },
                description = if (accessibilityClean) {
                    "Посторонние сервисы чтения экрана и перехвата нажатий не обнаружены."
                } else {
                    "Активные сторонние службы: ${nonSystemAccessibility.joinToString(", ")}"
                },
                isPassed = accessibilityClean,
                isWarningOnly = true,
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
        )

        // 4. Build Keys Integrity (release-keys vs test-keys)
        val buildTags = Build.TAGS ?: ""
        val isReleaseKeys = buildTags.contains("release-keys")
        if (!isReleaseKeys) score -= 10
        checks.add(
            PostureCheckItem(
                id = "build_tags",
                title = "Подпись прошивки ОС",
                valueText = if (isReleaseKeys) "Release Keys" else buildTags.ifBlank { "Нестандартная" },
                description = if (isReleaseKeys) {
                    "Система подписана официальными ключами производителя (release-keys)."
                } else {
                    "Обнаружена неофициальная или тестовая сборка прошивки ($buildTags)."
                },
                isPassed = isReleaseKeys,
                isWarningOnly = true
            )
        )

        // 5. Debuggable Non-System Apps
        val debuggableUserApps = auditedApps.filter {
            !it.isSystemApp && it.isDebuggable && !trustedPackages.contains(it.packageName)
        }
        val noDebuggableApps = debuggableUserApps.isEmpty()
        if (!noDebuggableApps) score -= 15
        checks.add(
            PostureCheckItem(
                id = "debuggable_apps",
                title = "Отладочные приложения (Debuggable)",
                valueText = if (noDebuggableApps) "Не найдены" else "Найдено: ${debuggableUserApps.size}",
                description = if (noDebuggableApps) {
                    "Установленные приложения собраны в релизном режиме без флага отладки."
                } else {
                    "Приложения с флагом FLAG_DEBUGGABLE: ${debuggableUserApps.joinToString { it.appName }}"
                },
                isPassed = noDebuggableApps
            )
        )

        // 6. Blacklisted APK Hashes
        val blacklistedApps = auditedApps.filter { it.isSignatureBlacklisted }
        val noBlacklisted = blacklistedApps.isEmpty()
        if (!noBlacklisted) score -= 35
        checks.add(
            PostureCheckItem(
                id = "signature_matches",
                title = "Совпадения по базе сигнатур",
                valueText = if (noBlacklisted) "0 совпадений" else "Угроз: ${blacklistedApps.size}",
                description = if (noBlacklisted) {
                    "Хеши проверенных APK не найдены в базе вредоносных сигнатур."
                } else {
                    "Обнаружены совпадения SHA-256: ${blacklistedApps.joinToString { it.appName }}"
                },
                isPassed = noBlacklisted
            )
        )

        // Deduct slight score for high-risk sideloaded apps
        val highRiskSideloaded = auditedApps.count {
            !it.isSystemApp &&
                it.installOrigin == InstallOrigin.SIDELOADED &&
                it.riskScore >= 60 &&
                !trustedPackages.contains(it.packageName)
        }
        if (highRiskSideloaded > 0) {
            score -= (highRiskSideloaded * 5).coerceAtMost(15)
        }

        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH.ifBlank { "Неизвестно" }
        } else {
            "API ${Build.VERSION.SDK_INT}"
        }

        return DevicePostureReport(
            androidVersion = Build.VERSION.RELEASE ?: "?",
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = securityPatch,
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            checks = checks,
            score = score.coerceIn(0, 100)
        )
    }
}
