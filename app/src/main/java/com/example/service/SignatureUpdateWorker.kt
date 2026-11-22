package com.example.service

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.database.ShieldGuardDatabase
import com.example.data.model.GlobalBlacklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SignatureUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val PREFS_NAME = "shield_guard_prefs"
        const val KEY_LAST_SYNC_TIME = "last_sig_sync_time"
        const val KEY_LAST_SYNC_ERROR = "last_sig_sync_error"
        const val KEY_LAST_FETCHED_COUNT = "last_sig_fetched_count"
        private const val FEED_URL = "https://bazaar.abuse.ch/export/txt/sha256/recent/"
        private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ShieldGuard-Android/1.0")
            }

            val statusCode = connection.responseCode
            if (statusCode != HttpURLConnection.HTTP_OK) {
                val err = "Сервер сигнатур вернул HTTP $statusCode"
                prefs.edit().putString(KEY_LAST_SYNC_ERROR, err).apply()
                return@withContext Result.failure(workDataOf("error" to err))
            }

            val parsedHashes = ArrayList<GlobalBlacklist>(1024)
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim() ?: continue
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    if (SHA256_REGEX.matches(trimmed)) {
                        parsedHashes.add(GlobalBlacklist(sha256_hash = trimmed.lowercase()))
                    }
                }
            }

            if (parsedHashes.isEmpty()) {
                val err = "Получен пустой ответ от Abuse.ch"
                prefs.edit().putString(KEY_LAST_SYNC_ERROR, err).apply()
                return@withContext Result.failure(workDataOf("error" to err))
            }

            val database = ShieldGuardDatabase.getDatabase(applicationContext)
            val dao = database.shieldGuardDao()
            database.withTransaction {
                // Insert in chunks to stay fast and avoid SQLite parameter limits on older Android
                parsedHashes.chunked(500).forEach { chunk ->
                    dao.insertBlacklistedHashes(chunk)
                }
            }

            prefs.edit()
                .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                .putInt(KEY_LAST_FETCHED_COUNT, parsedHashes.size)
                .remove(KEY_LAST_SYNC_ERROR)
                .apply()

            Result.success(workDataOf("fetched_count" to parsedHashes.size))
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Нет подключения к сети"
            prefs.edit().putString(KEY_LAST_SYNC_ERROR, err).apply()
            Result.failure(workDataOf("error" to err))
        } finally {
            connection?.disconnect()
        }
    }
}
