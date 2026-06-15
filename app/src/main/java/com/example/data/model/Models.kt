package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_reports")
data class ScanReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val scanType: String, // "Fast", "Deep", "Real-time"
    val filesScannedCount: Int,
    val threatsFoundCount: Int,
    val cacheCleanedMb: Float,
    val isInternetSourced: Boolean,
    val status: String // "Secure", "Threats Found", "Cleaned"
)

@Entity(tableName = "quarantined_items")
data class QuarantinedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val threatType: String, // "Trojan", "Adware", "Spyware", "Ransomware"
    val detectedDate: Long = System.currentTimeMillis(),
    val isAutomaticallyRemoved: Boolean,
    val sizeBytes: Long
)

@Entity(tableName = "blocked_websites")
data class BlockedWebsite(
    @PrimaryKey val url: String,
    val threatType: String = "Phishing & Adware",
    val addedDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "trusted_apps")
data class TrustedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val addedDate: Long = System.currentTimeMillis()
)

