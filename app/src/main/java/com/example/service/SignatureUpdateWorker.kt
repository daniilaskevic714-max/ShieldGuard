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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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
        const val KEY_LAST_SYNC_SOURCE = "last_sig_source"
        const val KEY_ABUSECH_AUTH_KEY = "abusech_auth_key"

        private const val ABUSE_API_V1_URL = "https://mb-api.abuse.ch/api/v1/"
        private val PUBLIC_RAW_FEEDS = listOf(
            "https://raw.githubusercontent.com/romainmarcoux/malicious-hash/main/full-hash-sha256-aa.txt",
            "https://raw.githubusercontent.com/amitambekar510/Malicious-Hash-Threat-List/main/malicious_SHA256_hashes_aa.txt"
        )
        private const val MAX_HASHES_PER_SYNC = 15_000
        private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val authKey = prefs.getString(KEY_ABUSECH_AUTH_KEY, null)?.trim().orEmpty()

        return@withContext try {
            val (hashes, sourceLabel) = if (authKey.isNotEmpty()) {
                fetchFromMalwareBazaarApi(authKey) to "Abuse.ch MalwareBazaar API v1"
            } else {
                fetchFromPublicFeeds() to "Public Threat Feed (GitHub)"
            }

            if (hashes.isEmpty()) {
                val err = "Источник не вернул валидных SHA-256 хешей"
                prefs.edit().putString(KEY_LAST_SYNC_ERROR, err).apply()
                return@withContext Result.failure(workDataOf("error" to err))
            }

            val database = ShieldGuardDatabase.getDatabase(applicationContext)
            val dao = database.shieldGuardDao()
            database.withTransaction {
                hashes.chunked(500).forEach { chunk ->
                    dao.insertBlacklistedHashes(chunk)
                }
            }

            prefs.edit()
                .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                .putInt(KEY_LAST_FETCHED_COUNT, hashes.size)
                .putString(KEY_LAST_SYNC_SOURCE, sourceLabel)
                .remove(KEY_LAST_SYNC_ERROR)
                .apply()

            Result.success(workDataOf("fetched_count" to hashes.size, "source" to sourceLabel))
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Ошибка сетевого подключения"
            prefs.edit().putString(KEY_LAST_SYNC_ERROR, err).apply()
            Result.failure(workDataOf("error" to err))
        }
    }

    private fun fetchFromMalwareBazaarApi(authKey: String): List<GlobalBlacklist> {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ABUSE_API_V1_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Auth-Key", authKey)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "ShieldGuard-Android/1.0")
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write("query=get_recent&selector=100")
                writer.flush()
            }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Abuse.ch API вернул HTTP $code")
            }

            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val status = json.optString("query_status", "unknown")
            if (status != "ok") {
                throw IllegalStateException("Abuse.ch API статус: $status")
            }

            val dataArray = json.optJSONArray("data") ?: return emptyList()
            val result = ArrayList<GlobalBlacklist>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                val sha = dataArray.optJSONObject(i)?.optString("sha256_hash")?.trim().orEmpty()
                if (SHA256_REGEX.matches(sha)) {
                    result.add(GlobalBlacklist(sha256_hash = sha.lowercase()))
                }
            }
            return result
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchFromPublicFeeds(): List<GlobalBlacklist> {
        var lastError: Exception? = null
        for (feedUrl in PUBLIC_RAW_FEEDS) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ShieldGuard-Android/1.0")
                }

                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    lastError = IllegalStateException("Сервер фида вернул HTTP $code")
                    continue
                }

                val parsed = ArrayList<GlobalBlacklist>(4096)
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    while (parsed.size < MAX_HASHES_PER_SYNC) {
                        val rawLine = reader.readLine() ?: break
                        val trimmed = rawLine.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                        if (SHA256_REGEX.matches(trimmed)) {
                            parsed.add(GlobalBlacklist(sha256_hash = trimmed.lowercase()))
                        }
                    }
                }

                if (parsed.isNotEmpty()) {
                    return parsed
                }
            } catch (e: Exception) {
                lastError = e
            } finally {
                connection?.disconnect()
            }
        }
        throw lastError ?: IllegalStateException("Не удалось загрузить публичный фид угроз")
    }
}
