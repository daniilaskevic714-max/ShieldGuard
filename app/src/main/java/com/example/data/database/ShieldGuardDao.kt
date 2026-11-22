package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GlobalBlacklist
import com.example.data.model.ScanReport
import com.example.data.model.TrustedApp
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldGuardDao {

    // Trusted Apps
    @Query("SELECT * FROM trusted_apps ORDER BY addedDate DESC")
    fun getAllTrustedApps(): Flow<List<TrustedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrustedApp(app: TrustedApp)

    @Delete
    suspend fun deleteTrustedApp(app: TrustedApp)

    @Query("DELETE FROM trusted_apps WHERE packageName = :packageName")
    suspend fun deleteTrustedAppByPackage(packageName: String)

    // Scan Reports
    @Query("SELECT * FROM scan_reports ORDER BY timestamp DESC LIMIT 50")
    fun getAllScanReports(): Flow<List<ScanReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanReport(report: ScanReport): Long

    @Query("DELETE FROM scan_reports")
    suspend fun deleteAllReports()

    // Global Blacklist (Abuse.ch MalwareBazaar SHA-256 hashes)
    @Query("SELECT COUNT(*) FROM global_blacklist")
    fun getBlacklistedCountFlow(): Flow<Int>

    @Query("SELECT sha256_hash FROM global_blacklist")
    suspend fun getAllBlacklistedHashStrings(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlacklistedHashes(hashes: List<GlobalBlacklist>)

    @Query("DELETE FROM global_blacklist")
    suspend fun clearAllBlacklistedHashes()

    @Query("SELECT EXISTS(SELECT 1 FROM global_blacklist WHERE sha256_hash = :hash)")
    suspend fun isHashBlacklisted(hash: String): Boolean
}
