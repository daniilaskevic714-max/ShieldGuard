package com.example.data.scanner

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.model.FileInspectionResult
import com.example.data.model.PermissionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object FileInspector {

    suspend fun inspectUri(
        context: Context,
        uri: Uri,
        blacklistedHashes: Set<String>
    ): FileInspectionResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val resolver = context.contentResolver
        var displayName = "selected_file"
        var reportedSize = -1L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        reportedSize = cursor.getLong(sizeIdx)
                    }
                }
            }

        val mimeType = resolver.getType(uri)
        val looksLikeApk = displayName.endsWith(".apk", ignoreCase = true) ||
            mimeType == "application/vnd.android.package-archive"

        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val md5Digest = MessageDigest.getInstance("MD5")
        var totalBytes = 0L

        // Only spool to temp cache if it's an APK (capped at 200 MB) so PackageManager can parse it
        val tempApkFile: File? = if (looksLikeApk) {
            File(context.cacheDir, "inspect_${System.currentTimeMillis()}.apk")
        } else {
            null
        }

        try {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(65_536)
                val out = tempApkFile?.let { FileOutputStream(it) }
                try {
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        sha256Digest.update(buffer, 0, read)
                        md5Digest.update(buffer, 0, read)
                        totalBytes += read
                        if (out != null && totalBytes <= 200L * 1024L * 1024L) {
                            out.write(buffer, 0, read)
                        }
                    }
                } finally {
                    out?.close()
                }
            } ?: throw IllegalStateException("Не удалось открыть выбранный файл")

            val sha256 = sha256Digest.digest().joinToString("") { "%02x".format(it) }
            val md5 = md5Digest.digest().joinToString("") { "%02x".format(it) }
            val isBlacklisted = blacklistedHashes.contains(sha256)

            var apkPackageName: String? = null
            var apkVersion: String? = null
            var apkMinSdk: Int? = null
            var apkTargetSdk: Int? = null
            var apkCertSha256: String? = null
            var apkPermissions: List<PermissionItem> = emptyList()

            if (tempApkFile != null && tempApkFile.exists() && tempApkFile.length() > 0) {
                val archiveInfo = parseApkArchive(context.packageManager, tempApkFile.absolutePath)
                if (archiveInfo != null) {
                    apkPackageName = archiveInfo.packageName
                    apkVersion = archiveInfo.versionName ?: "1.0"
                    val appInfo = archiveInfo.applicationInfo
                    if (appInfo != null) {
                        apkTargetSdk = appInfo.targetSdkVersion
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            apkMinSdk = appInfo.minSdkVersion
                        }
                    }
                    apkCertSha256 = extractArchiveCertSha256(archiveInfo)
                    apkPermissions = (archiveInfo.requestedPermissions ?: emptyArray())
                        .filterNotNull()
                        .map { AppSecurityAnalyzer.describePermission(it, isGranted = false) }
                        .sortedByDescending { it.severityWeight }
                }
            }

            FileInspectionResult(
                displayName = displayName,
                sizeBytes = if (reportedSize > 0) reportedSize else totalBytes,
                sha256 = sha256,
                md5 = md5,
                isBlacklisted = isBlacklisted,
                isApk = looksLikeApk && apkPackageName != null,
                apkPackageName = apkPackageName,
                apkVersion = apkVersion,
                apkMinSdk = apkMinSdk,
                apkTargetSdk = apkTargetSdk,
                apkCertSha256 = apkCertSha256,
                apkPermissions = apkPermissions,
                inspectionDurationMs = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            )
        } finally {
            tempApkFile?.delete()
        }
    }

    private fun parseApkArchive(pm: PackageManager, path: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val flags = PackageManager.GET_PERMISSIONS.toLong() or
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(
                    path,
                    PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(
                    path,
                    PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractArchiveCertSha256(pkgInfo: PackageInfo): String? {
        val sig: Signature? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.firstOrNull()
        }
        return sig?.let { AppSecurityAnalyzer.sha256Hex(it.toByteArray()) }
    }
}
