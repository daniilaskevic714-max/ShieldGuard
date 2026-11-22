package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ShieldAmber
import com.example.ui.theme.ShieldCrimson
import com.example.ui.theme.ShieldCyan
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.ShieldOutline
import com.example.ui.theme.ShieldSurface
import com.example.ui.theme.ShieldSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurityCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ShieldOutline,
    containerColor: Color = ShieldSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun PostureScoreGauge(
    score: Int,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = when {
        isScanning -> ShieldCyan
        score >= 80 -> ShieldEmerald
        score >= 55 -> ShieldAmber
        else -> ShieldCrimson
    }

    val animatedFraction by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 450),
        label = "ScoreSweep"
    )

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Only run infinite animation while actively scanning to save GPU/battery
        if (isScanning) {
            val infiniteTransition = rememberInfiniteTransition(label = "ScanArc")
            val sweepStart by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ScanRotation"
            )
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .drawBehind {
                        drawArc(
                            color = ShieldOutline.copy(alpha = 0.45f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = ShieldCyan,
                            startAngle = sweepStart,
                            sweepAngle = 110f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .drawBehind {
                        drawArc(
                            color = ShieldOutline.copy(alpha = 0.45f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedFraction,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isScanning) "..." else "$score",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = if (isScanning) "АНАЛИЗ" else "ИЗ 100",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

private object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(120)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }
}

@Composable
fun AppIconThumbnail(
    packageName: String,
    tintFallback: Color,
    size: Dp = 40.dp
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                loadPackageIconBitmap(context, packageName)
            }
            if (loaded != null) {
                AppIconCache.put(packageName, loaded)
                bitmap = loaded
            }
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = packageName,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(ShieldSurfaceElevated)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(tintFallback.copy(alpha = 0.12f), CircleShape)
                .border(1.dp, tintFallback.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = packageName,
                tint = tintFallback,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

private fun loadPackageIconBitmap(context: Context, packageName: String): ImageBitmap? {
    return try {
        val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap.asImageBitmap()
        } else {
            val w = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(96) ?: 96
            val h = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(96) ?: 96
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

object SystemActions {
    fun openAppDetails(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchSafely(context, intent)
    }

    fun requestUninstall(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchSafely(context, intent)
    }

    fun openSettingsScreen(context: Context, action: String) {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!launchSafely(context, intent)) {
            launchSafely(
                context,
                Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
    }

    private fun launchSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

fun formatTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Не выполнялось"
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes Б"
        bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f КБ", bytes / 1024f)
        else -> String.format(Locale.getDefault(), "%.2f МБ", bytes / (1024f * 1024f))
    }
}

fun riskColorFor(score: Int, isTrusted: Boolean = false): Color {
    return when {
        isTrusted -> ShieldEmerald
        score >= 70 -> ShieldCrimson
        score >= 45 -> ShieldAmber
        else -> ShieldEmerald
    }
}
