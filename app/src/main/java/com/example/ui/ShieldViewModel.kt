package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.database.ShieldGuardDatabase
import com.example.data.model.AppAuditInfo
import com.example.data.model.DevicePostureReport
import com.example.data.model.FileInspectionResult
import com.example.data.model.ScanReport
import com.example.data.model.SensitivityLevel
import com.example.data.model.SignatureSyncState
import com.example.data.model.TrustedApp
import com.example.data.repository.SecurityRepository
import com.example.service.SignatureUpdateWorker
import com.example.widget.ShieldGuardWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ShieldGuardDatabase.getDatabase(application)
    private val repository = SecurityRepository(application, db.shieldGuardDao())
    private val prefs = application.getSharedPreferences(SignatureUpdateWorker.PREFS_NAME, Context.MODE_PRIVATE)

    val reportsList: StateFlow<List<ScanReport>> = repository.allReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trustedAppsList: StateFlow<List<TrustedApp>> = repository.trustedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSyncingSignatures = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(prefs.getString(SignatureUpdateWorker.KEY_LAST_SYNC_ERROR, null))
    private val _lastSyncTimeMs = MutableStateFlow(prefs.getLong(SignatureUpdateWorker.KEY_LAST_SYNC_TIME, 0L))

    val signatureSyncState: StateFlow<SignatureSyncState> = combine(
        repository.blacklistedCount,
        _lastSyncTimeMs,
        _isSyncingSignatures,
        _syncError
    ) { count, lastSync, syncing, err ->
        SignatureSyncState(
            count = count,
            lastSyncTimeMs = lastSync,
            isSyncing = syncing,
            lastError = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SignatureSyncState())

    private val _auditedApps = MutableStateFlow<List<AppAuditInfo>>(emptyList())
    val auditedApps: StateFlow<List<AppAuditInfo>> = _auditedApps.asStateFlow()

    private val _postureReport = MutableStateFlow<DevicePostureReport?>(null)
    val postureReport: StateFlow<DevicePostureReport?> = _postureReport.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _scannedCount = MutableStateFlow(0)
    val scannedCount: StateFlow<Int> = _scannedCount.asStateFlow()

    private val _totalToScan = MutableStateFlow(0)
    val totalToScan: StateFlow<Int> = _totalToScan.asStateFlow()

    private val _currentScanTarget = MutableStateFlow("")
    val currentScanTarget: StateFlow<String> = _currentScanTarget.asStateFlow()

    private val _lastScanBanner = MutableStateFlow<String?>(null)
    val lastScanBanner: StateFlow<String?> = _lastScanBanner.asStateFlow()

    private val _isInspectingFile = MutableStateFlow(false)
    val isInspectingFile: StateFlow<Boolean> = _isInspectingFile.asStateFlow()

    private val _fileInspectionResult = MutableStateFlow<FileInspectionResult?>(null)
    val fileInspectionResult: StateFlow<FileInspectionResult?> = _fileInspectionResult.asStateFlow()

    private val _fileInspectionError = MutableStateFlow<String?>(null)
    val fileInspectionError: StateFlow<String?> = _fileInspectionError.asStateFlow()

    private val _sensitivity = MutableStateFlow(
        SensitivityLevel.entries.find {
            it.key == prefs.getString("scanner_sensitivity", SensitivityLevel.MEDIUM.key)
        } ?: SensitivityLevel.MEDIUM
    )
    val sensitivity: StateFlow<SensitivityLevel> = _sensitivity.asStateFlow()

    private var activeScanJob: Job? = null

    init {
        refreshAuditInventory()
    }

    fun refreshAuditInventory() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val blacklist = repository.getBlacklistedHashSet()
            val trustedSet = repository.trustedApps.first().mapTo(HashSet()) { it.packageName }
            val apps = repository.auditInstalledApps(_sensitivity.value, blacklist)
            val posture = repository.inspectDevicePosture(apps, trustedSet)

            _auditedApps.value = apps
            _postureReport.value = posture
            _isLoadingApps.value = false
            ShieldGuardWidgetProvider.updateWidgetScore(getApplication(), posture.score)
        }
    }

    fun runQuickAudit() {
        if (_isScanning.value) return
        activeScanJob = viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            _isScanning.value = true
            _scanProgress.value = 0.2f
            _currentScanTarget.value = "Анализ разрешений и параметров ОС..."

            val blacklist = repository.getBlacklistedHashSet()
            val trustedSet = repository.trustedApps.first().mapTo(HashSet()) { it.packageName }
            val apps = repository.auditInstalledApps(_sensitivity.value, blacklist)
            _scanProgress.value = 0.8f

            val posture = repository.inspectDevicePosture(apps, trustedSet)
            val duration = (System.currentTimeMillis() - start).coerceAtLeast(1L)

            val flaggedCount = apps.count {
                (it.isSignatureBlacklisted || (!it.isSystemApp && it.riskScore >= 50)) &&
                    !trustedSet.contains(it.packageName)
            }
            val blacklistHits = apps.count { it.isSignatureBlacklisted }
            val status = when {
                blacklistHits > 0 -> "Найдена угроза"
                flaggedCount > 0 -> "Есть риски"
                else -> "Чисто"
            }

            repository.addScanReport(
                ScanReport(
                    scanType = "Быстрый аудит",
                    appsScannedCount = apps.size,
                    findingsCount = flaggedCount,
                    durationMs = duration,
                    status = status
                )
            )

            _auditedApps.value = apps
            _postureReport.value = posture
            _scanProgress.value = 1f
            _lastScanBanner.value = "Быстрый аудит завершён за ${duration} мс • Приложений: ${apps.size}, рисков: $flaggedCount"
            _isScanning.value = false
            ShieldGuardWidgetProvider.updateWidgetScore(getApplication(), posture.score)
        }
    }

    fun runFullApkHashScan() {
        if (_isScanning.value) return
        activeScanJob = viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            _isScanning.value = true
            _scanProgress.value = 0f
            _scannedCount.value = 0
            _currentScanTarget.value = "Подготовка списка APK..."

            val blacklist = repository.getBlacklistedHashSet()
            val trustedSet = repository.trustedApps.first().mapTo(HashSet()) { it.packageName }

            val baseApps = _auditedApps.value.ifEmpty {
                repository.auditInstalledApps(_sensitivity.value, blacklist)
            }
            val userAppCount = baseApps.count { !it.isSystemApp }
            _totalToScan.value = userAppCount

            val hashedApps = repository.runFullApkHashScan(
                currentApps = baseApps,
                blacklistedHashes = blacklist,
                sensitivity = _sensitivity.value
            ) { scanned, total, label ->
                _scannedCount.value = scanned
                _totalToScan.value = total
                _scanProgress.value = if (total > 0) scanned.toFloat() / total.toFloat() else 1f
                _currentScanTarget.value = label
            }

            val posture = repository.inspectDevicePosture(hashedApps, trustedSet)
            val duration = (System.currentTimeMillis() - start).coerceAtLeast(1L)

            val blacklistHits = hashedApps.count { it.isSignatureBlacklisted }
            val flaggedCount = hashedApps.count {
                (it.isSignatureBlacklisted || (!it.isSystemApp && it.riskScore >= 50)) &&
                    !trustedSet.contains(it.packageName)
            }
            val status = when {
                blacklistHits > 0 -> "Найдена угроза"
                flaggedCount > 0 -> "Есть риски"
                else -> "Чисто"
            }

            repository.addScanReport(
                ScanReport(
                    scanType = "Проверка APK (SHA-256)",
                    appsScannedCount = userAppCount,
                    findingsCount = flaggedCount,
                    durationMs = duration,
                    status = status
                )
            )

            _auditedApps.value = hashedApps
            _postureReport.value = posture
            _scanProgress.value = 1f
            _lastScanBanner.value = "Проверено $userAppCount APK (SHA-256) за ${duration} мс • Совпадений с базой угроз: $blacklistHits"
            _isScanning.value = false
            ShieldGuardWidgetProvider.updateWidgetScore(getApplication(), posture.score)
        }
    }

    fun inspectPickedFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isInspectingFile.value = true
            _fileInspectionError.value = null
            try {
                val blacklist = repository.getBlacklistedHashSet()
                val result = repository.inspectFileUri(uri, blacklist)
                _fileInspectionResult.value = result

                repository.addScanReport(
                    ScanReport(
                        scanType = "Инспекция файла",
                        appsScannedCount = 1,
                        findingsCount = if (result.isBlacklisted) 1 else 0,
                        durationMs = result.inspectionDurationMs,
                        status = if (result.isBlacklisted) "Найдена угроза" else "Чисто"
                    )
                )
            } catch (e: Exception) {
                _fileInspectionError.value = e.localizedMessage ?: "Ошибка чтения файла"
            } finally {
                _isInspectingFile.value = false
            }
        }
    }

    fun clearFileInspection() {
        _fileInspectionResult.value = null
        _fileInspectionError.value = null
    }

    fun dismissScanBanner() {
        _lastScanBanner.value = null
    }

    fun setSensitivity(level: SensitivityLevel) {
        if (_sensitivity.value == level) return
        _sensitivity.value = level
        prefs.edit().putString("scanner_sensitivity", level.key).apply()
        refreshAuditInventory()
    }

    fun addTrustedApp(packageName: String, appName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrustedApp(packageName, appName)
            val trustedSet = repository.trustedApps.first().mapTo(HashSet()) { it.packageName }
            _postureReport.value = repository.inspectDevicePosture(_auditedApps.value, trustedSet)
        }
    }

    fun removeTrustedAppByPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeTrustedAppByPackage(packageName)
            val trustedSet = repository.trustedApps.first().mapTo(HashSet()) { it.packageName }
            _postureReport.value = repository.inspectDevicePosture(_auditedApps.value, trustedSet)
        }
    }

    fun removeTrustedApp(app: TrustedApp) {
        removeTrustedAppByPackage(app.packageName)
    }

    fun clearAllReports() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearReports()
        }
    }

    fun updateSignatureDatabase() {
        if (_isSyncingSignatures.value) return
        _isSyncingSignatures.value = true
        _syncError.value = null

        val request = OneTimeWorkRequestBuilder<SignatureUpdateWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        val wm = WorkManager.getInstance(getApplication())
        wm.enqueueUniqueWork("SignatureUpdate", ExistingWorkPolicy.REPLACE, request)

        viewModelScope.launch {
            val finalInfo = wm.getWorkInfoByIdFlow(request.id)
                .first { it != null && it.state.isFinished }
            when (finalInfo?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    _isSyncingSignatures.value = false
                    _lastSyncTimeMs.value = prefs.getLong(SignatureUpdateWorker.KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                    _syncError.value = null
                    refreshAuditInventory()
                }
                else -> {
                    _isSyncingSignatures.value = false
                    _syncError.value = finalInfo?.outputData?.getString("error")
                        ?: prefs.getString(SignatureUpdateWorker.KEY_LAST_SYNC_ERROR, null)
                        ?: "Не удалось загрузить базу сигнатур"
                }
            }
        }
    }
}
