package com.example.data.database

import androidx.room.*
import com.example.data.model.BlockedWebsite
import com.example.data.model.QuarantinedItem
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
    @Query("SELECT * FROM scan_reports ORDER BY timestamp DESC")
    fun getAllScanReports(): Flow<List<ScanReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanReport(report: ScanReport): Long

    @Query("DELETE FROM scan_reports")
    suspend fun deleteAllReports()

    // Quarantined Items
    @Query("SELECT * FROM quarantined_items ORDER BY detectedDate DESC")
    fun getAllQuarantinedItems(): Flow<List<QuarantinedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuarantinedItem(item: QuarantinedItem): Long

    @Delete
    suspend fun deleteQuarantinedItem(item: QuarantinedItem)

    @Query("DELETE FROM quarantined_items WHERE id = :id")
    suspend fun deleteQuaranteedById(id: Long)

    // Blocked Websites
    @Query("SELECT * FROM blocked_websites ORDER BY addedDate DESC")
    fun getAllBlockedWebsites(): Flow<List<BlockedWebsite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedWebsite(website: BlockedWebsite)

    @Delete
    suspend fun deleteBlockedWebsite(website: BlockedWebsite)
}
