package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.ShieldGuardDatabase
import com.example.data.model.BlockedWebsite
import com.example.data.model.QuarantinedItem
import com.example.data.model.ScanReport
import com.example.data.model.TrustedApp
import com.example.data.repository.AppSecurityInfo
import com.example.data.repository.CacheData
import com.example.data.repository.SecurityRepository
import com.example.service.ShieldGuardService
import com.example.widget.ShieldGuardWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ShieldGuardDatabase.getDatabase(application)
    private val repository = SecurityRepository(application, db.shieldGuardDao())

    // Database streams
    val reportsList: StateFlow<List<ScanReport>> = repository.allReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quarantineList: StateFlow<List<QuarantinedItem>> = repository.quarantinedItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val websitesList: StateFlow<List<BlockedWebsite>> = repository.blockedWebsites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trustedAppsList: StateFlow<List<TrustedApp>> = repository.trustedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Scanning state
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress = _scanProgress.asStateFlow()

    private val _scannedFilesCount = MutableStateFlow(0)
    val scannedFilesCount = _scannedFilesCount.asStateFlow()

    private val _currentScannedFile = MutableStateFlow("")
    val currentScannedFile = _currentScannedFile.asStateFlow()

    private val _activeThreatsFound = MutableStateFlow<List<QuarantinedItem>>(emptyList())
    val activeThreatsFound = _activeThreatsFound.asStateFlow()

    // Cache clean state
    private val _cacheState = MutableStateFlow<CacheData?>(null)
    val cacheState = _cacheState.asStateFlow()

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning = _isCleaning.asStateFlow()

    private val _clearedAmount = MutableStateFlow<Float?>(null)
    val clearedAmount = _clearedAmount.asStateFlow()

    // App permission list state
    private val _installedApps = MutableStateFlow<List<AppSecurityInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    // Settings States
    val isRealTimeScannerActive = MutableStateFlow(true)
    val isRiskNotificationsEnabled = MutableStateFlow(true)
    val areBackgroundScansEnabled = MutableStateFlow(true)
    val isLowBatteryModeActive = MutableStateFlow(false)
    val scannerSensitivity = MutableStateFlow("Medium") // "Low", "Medium", "High"

    init {
        viewModelScope.launch {
            repository.initDefaultBlockedWebsites()
            refreshCacheUsage()
            refreshAppPermissions()
            
            // Auto start service with respect to setting
            if (isRealTimeScannerActive.value) {
                ShieldGuardService.startService(application)
            }
        }
    }

    // Refresh Cache
    fun refreshCacheUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.getCalculatedCacheUsage()
            _cacheState.value = res
        }
    }

    // Refresh Permissions audit list
    fun refreshAppPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getInstalledAppsWithPermissions(scannerSensitivity.value)
            _installedApps.value = list
        }
    }

    fun setScannerSensitivity(level: String) {
        scannerSensitivity.value = level
        refreshAppPermissions()
    }

    // Interactive Cache Clean
    fun cleanCache() {
        viewModelScope.launch {
            _isCleaning.value = true
            // Simulate clean-up duration
            delay(1800)
            val cleared = withContext(Dispatchers.IO) {
                repository.clearAppCache()
            }
            _clearedAmount.value = cleared
            
            // Add a log of file cache clean-up
            repository.addScanReport(
                ScanReport(
                    scanType = "Cache Cleaning",
                    filesScannedCount = 42,
                    threatsFoundCount = 0,
                    cacheCleanedMb = cleared,
                    isInternetSourced = false,
                    status = "Cleaned"
                )
            )

            refreshCacheUsage()
            _isCleaning.value = false
            ShieldGuardWidgetProvider.forceUpdate(getApplication())
        }
    }

    // Interactive Scan
    fun runScan(isDeep: Boolean) {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = 0f
        _scannedFilesCount.value = 0
        _activeThreatsFound.value = emptyList()

        viewModelScope.launch {
            val filesToScan = 350 + (if (isDeep) 650 else 0)
            val stepSize = if (isLowBatteryModeActive.value) 20 else 5 // batch files to save battery & cpu cycles
            val speedFactor = if (isLowBatteryModeActive.value) 80L else 40L // scan slower/lower CPU draw if optimization active

            val dummyPaths = listOf(
                "/storage/emulated/0/Download/update_free_vpn.apk",
                "/storage/emulated/0/Download/system_miner_service.bin",
                "/storage/emulated/0/Android/data/com.android.chrome/cache/temp.tmp",
                "/system/bin/app_process",
                "/storage/emulated/0/DCIM/Camera/IMG_2026.jpg",
                "/storage/emulated/0/Documents/tax_credentials.xlsx",
                "/storage/emulated/0/Download/adware_blocker_pro.apk",
                "/storage/emulated/0/Download/trojan_dropper_stub.exe",
                "/storage/emulated/0/Pictures/exif_infected.png"
            )

            var scanned = 0
            while (scanned < filesToScan) {
                delay(speedFactor)
                scanned += stepSize
                _scannedFilesCount.value = scanned
                _scanProgress.value = scanned.toFloat() / filesToScan.toFloat()

                // Display file being examined
                val pathTemplate = dummyPaths[Random.nextInt(dummyPaths.size)]
                _currentScannedFile.value = "$pathTemplate.${Random.nextInt(100, 999)}"
            }

            // Determine if a threat was found (simulated malware finding to guarantee fully working offline quarantine + alert actions)
            val foundMalware = mutableListOf<QuarantinedItem>()
            
            // Fast scan finds 1, Deep scan finds 2 threats
            foundMalware.add(
                QuarantinedItem(
                    fileName = "adware_booster_block.apk",
                    filePath = "/storage/emulated/0/Download/adware_booster_block.apk",
                    threatType = "Adware.Aggressive",
                    isAutomaticallyRemoved = false,
                    sizeBytes = 4_512_000
                )
            )

            if (isDeep) {
                foundMalware.add(
                    QuarantinedItem(
                        fileName = "telegram_spy_agent.bin",
                        filePath = "/storage/emulated/0/Download/telegram_spy_agent.bin",
                        threatType = "Spyware.InfoStealer",
                        isAutomaticallyRemoved = false,
                        sizeBytes = 1_230_000
                    )
                )
            }

            _activeThreatsFound.value = foundMalware

            // Write report to DB
            repository.addScanReport(
                ScanReport(
                    scanType = if (isDeep) "Deep Scan" else "Fast Scan",
                    filesScannedCount = filesToScan,
                    threatsFoundCount = foundMalware.size,
                    cacheCleanedMb = 0f,
                    isInternetSourced = false,
                    status = if (foundMalware.isNotEmpty()) "Action Required" else "Secure"
                )
            )

            _isScanning.value = false
            
            // Update Widgets
            ShieldGuardWidgetProvider.forceUpdate(getApplication())
        }
    }

    // Move to Quarantine (manual or automatically triggered)
    fun quarantineThreat(threat: QuarantinedItem) {
        viewModelScope.launch {
            repository.addQuarantine(threat.copy(isAutomaticallyRemoved = true))
            _activeThreatsFound.value = _activeThreatsFound.value.filter { it.fileName != threat.fileName }
            
            // Post notification if alerts are enabled
            if (isRiskNotificationsEnabled.value) {
                ShieldGuardService.simulateDownloadScan(
                    context = getApplication(),
                    fileName = threat.fileName,
                    containsVirus = true
                )
            }
        }
    }

    // Permanently Delete Quarantined Target File
    fun deleteQuarantine(item: QuarantinedItem) {
        viewModelScope.launch {
            repository.deleteQuarantineItem(item)
        }
    }

    // Restore File from Quarantine
    fun restoreQuarantine(item: QuarantinedItem) {
        viewModelScope.launch {
            // Remove from Quarantine log
            repository.removeQuarantine(item.id)
            
            // Add a log indicating file restoration
            repository.addScanReport(
                ScanReport(
                    scanType = "File Restoration",
                    filesScannedCount = 1,
                    threatsFoundCount = 0,
                    cacheCleanedMb = 0f,
                    isInternetSourced = false,
                    status = "File Restored"
                )
            )
        }
    }

    // Add Blocked Malicious Website
    fun addWebRule(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch {
            repository.addBlockedWebsite(domain.trim().lowercase())
        }
    }

    // Remove Blocked Malicious Website
    fun removeWebRule(website: BlockedWebsite) {
        viewModelScope.launch {
            repository.removeBlockedWebsite(website)
        }
    }

    // Check custom Domain search simulation
    private val _webTestResult = MutableStateFlow<String?>(null)
    val webTestResult = _webTestResult.asStateFlow()

    fun testWebDomain(domain: String) {
        viewModelScope.launch {
            val suspicious = repository.isWebsiteSuspicious(domain)
            _webTestResult.value = if (suspicious) {
                "🔴 БЛОКИРОВАН: Этот сайт найден в нашей базе вредоносного ПО и рекламных трекеров!"
            } else {
                "🟢 БЕЗОПАСЕН: Сайт чист и разрешен в вашем брандмауэре."
            }
        }
    }

    fun clearWebTestResult() {
        _webTestResult.value = null
    }

    // Clear history reports completely
    fun clearAllReports() {
        viewModelScope.launch {
            repository.clearReports()
        }
    }

    // Toggle real time scanning
    fun toggleRealTimeScanning(active: Boolean) {
        isRealTimeScannerActive.value = active
        val app = getApplication<Application>()
        if (active) {
            ShieldGuardService.startService(app)
        } else {
            ShieldGuardService.stopService(app)
        }
    }

    // Simulations for risk notifications
    fun runSuspiciousAppTrigger(appName: String, pkg: String) {
        ShieldGuardService.triggerAppEntranceWarning(getApplication(), appName, pkg)
    }

    fun runMaliciousDownloadTrigger(fileName: String) {
        ShieldGuardService.simulateDownloadScan(getApplication(), fileName, containsVirus = true)
    }

    fun runSafeDownloadTrigger(fileName: String) {
        ShieldGuardService.simulateDownloadScan(getApplication(), fileName, containsVirus = false)
    }

    // Trusted Apps whitelist handlers
    fun addTrustedApp(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.addTrustedApp(packageName, appName)
            refreshAppPermissions()
        }
    }

    fun removeTrustedAppByPackage(packageName: String) {
        viewModelScope.launch {
            repository.removeTrustedAppByPackage(packageName)
            refreshAppPermissions()
        }
    }

    fun removeTrustedApp(app: TrustedApp) {
        viewModelScope.launch {
            repository.removeTrustedApp(app)
            refreshAppPermissions()
        }
    }
}
