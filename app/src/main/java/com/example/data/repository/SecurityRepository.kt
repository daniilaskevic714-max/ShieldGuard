package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.database.ShieldGuardDao
import com.example.data.model.AppAuditInfo
import com.example.data.model.DevicePostureReport
import com.example.data.model.FileInspectionResult
import com.example.data.model.ScanReport
import com.example.data.model.SensitivityLevel
import com.example.data.model.TrustedApp
import com.example.data.scanner.AppSecurityAnalyzer
import com.example.data.scanner.DevicePostureAuditor
import com.example.data.scanner.FileInspector
import kotlinx.coroutines.flow.Flow

class SecurityRepository(
    private val context: Context,
    private val dao: ShieldGuardDao
) {
    val allReports: Flow<List<ScanReport>> = dao.getAllScanReports()
    val trustedApps: Flow<List<TrustedApp>> = dao.getAllTrustedApps()
    val blacklistedCount: Flow<Int> = dao.getBlacklistedCountFlow()

    suspend fun getBlacklistedHashSet(): Set<String> {
        return dao.getAllBlacklistedHashStrings().toHashSet()
    }

    suspend fun addTrustedApp(packageName: String, appName: String) {
        dao.insertTrustedApp(TrustedApp(packageName = packageName, appName = appName))
    }

    suspend fun removeTrustedAppByPackage(packageName: String) {
        dao.deleteTrustedAppByPackage(packageName)
    }

    suspend fun removeTrustedApp(app: TrustedApp) {
        dao.deleteTrustedApp(app)
    }

    suspend fun addScanReport(report: ScanReport): Long = dao.insertScanReport(report)

    suspend fun clearReports() = dao.deleteAllReports()

    suspend fun auditInstalledApps(
        sensitivity: SensitivityLevel,
        blacklistedHashes: Set<String>
    ): List<AppAuditInfo> {
        return AppSecurityAnalyzer.auditInstalledApps(
            context = context,
            sensitivity = sensitivity,
            blacklistedHashes = blacklistedHashes
        )
    }

    suspend fun runFullApkHashScan(
        currentApps: List<AppAuditInfo>,
        blacklistedHashes: Set<String>,
        sensitivity: SensitivityLevel,
        onProgress: (scanned: Int, total: Int, currentLabel: String) -> Unit
    ): List<AppAuditInfo> {
        return AppSecurityAnalyzer.scanUserApksWithHashes(
            context = context,
            apps = currentApps,
            blacklistedHashes = blacklistedHashes,
            sensitivity = sensitivity,
            onProgress = onProgress
        )
    }

    fun inspectDevicePosture(
        auditedApps: List<AppAuditInfo>,
        trustedPackages: Set<String>
    ): DevicePostureReport {
        return DevicePostureAuditor.inspect(context, auditedApps, trustedPackages)
    }

    suspend fun inspectFileUri(uri: Uri, blacklistedHashes: Set<String>): FileInspectionResult {
        return FileInspector.inspectUri(context, uri, blacklistedHashes)
    }
}
