package com.medtech.reviewhub.ui.motivation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medtech.reviewhub.domain.ContextKind
import com.medtech.reviewhub.domain.Motivation
import com.medtech.reviewhub.domain.MotivationBank
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.MascotFace
import java.io.File

/**
 * "A Little Motivation for You" dashboard widget + contextual encouragement
 * banner + quote share helpers (text share always; pastel-card image share is
 * best-effort - see README limitations).
 */

private val StarPink = Color(0xFFF48FB1)
private val StarLavender = Color(0xFFB39DDB)

@Composable
fun DailyQuoteCard(
    bank: MotivationBank,
    dayOfYear: Int,
    offset: Int,
    favoriteQuotes: Set<String>,
    onShowAnother: () -> Unit,
    onRandom: () -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val context = LocalContext.current
    val quote = if (offset == 0) Motivation.dailyQuote(bank, dayOfYear)
    else Motivation.rotatedQuote(bank, dayOfYear, offset)
    val isFavorite = quote in favoriteQuotes

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Box {
            StarDecorations()
            Column {
                Text(
                    "A Little Motivation for You",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    quote,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onToggleFavorite(quote) }) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove favorite" else "Save favorite",
                            tint = if (isFavorite) StarPink else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { shareQuoteText(context, quote) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share quote")
                    }
                    IconButton(onClick = { shareQuoteImage(context, quote) }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Share as image")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRandom) { Text("Random") }
                    TextButton(onClick = onShowAnother) { Text("Show another") }
                }
            }
        }
    }
}

/** Tiny pastel star decorations in the card corners (decorative). */
@Composable
private fun StarDecorations() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val star = { cx: Float, cy: Float, r: Float, color: Color ->
            val path = Path().apply {
                moveTo(cx, cy - r)
                quadraticBezierTo(cx + r * 0.2f, cy - r * 0.2f, cx + r, cy)
                quadraticBezierTo(cx + r * 0.2f, cy + r * 0.2f, cx, cy + r)
                quadraticBezierTo(cx - r * 0.2f, cy + r * 0.2f, cx - r, cy)
                quadraticBezierTo(cx - r * 0.2f, cy - r * 0.2f, cx, cy - r)
                close()
            }
            drawPath(path, color)
        }
        star(size.width - 24f, 18f, 9f, StarPink)
        star(size.width - 58f, 40f, 6f, StarLavender)
        star(18f, size.height - 18f, 7f, StarLavender)
    }
}

/** Contextual encouragement banner (mascot + quote) shown after study events. */
@Composable
fun EncouragementBanner(
    kind: ContextKind,
    seed: Long,
    detail: String = "",
    bank: MotivationBank,
    mascotKind: com.medtech.reviewhub.domain.MascotKind,
    onDismiss: () -> Unit
) {
    val quote = Motivation.contextQuote(bank, kind, seed)
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MascotFace(kind = mascotKind, mood = com.medtech.reviewhub.domain.MascotMood.ENCOURAGING, sizeDp = 48)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(quote, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onDismiss) { Text("Thanks") }
        }
    }
}

// ---------------------------------------------------------------------------
// Sharing: text always works; pastel-card image share is best-effort.
// ---------------------------------------------------------------------------

fun shareQuoteText(context: Context, quote: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "\"$quote\"\n\n- MedTech Review Hub")
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share quote")) }
}

/**
 * Renders a simple pastel card with the quote and shares it as a PNG. Falls
 * back silently to text share when image export fails on a device (see the
 * documented limitation in README).
 */
fun shareQuoteImage(context: Context, quote: String) {
    val ok = runCatching {
        val bitmap = renderQuoteCard(context, quote)
        val dir = File(context.cacheDir, "quotes").apply { mkdirs() }
        val file = File(dir, "quote_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "\"$quote\"\n\n- MedTech Review Hub")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share quote card"))
        true
    }.getOrDefault(false)
    if (!ok) shareQuoteText(context, quote)
}

/** Simple pastel card bitmap with wrapped quote text + tiny stars. */
internal fun renderQuoteCard(context: Context, quote: String): Bitmap {
    val w = 1080
    val h = 1080
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(253, 231, 241))

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(255, 247, 250)
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(60f, 60f, w - 60f, h - 60f), 60f, 60f, cardPaint)

    val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(244, 143, 177)
        style = Paint.Style.FILL
    }
    fun star(cx: Float, cy: Float, r: Float) {
        val path = android.graphics.Path().apply {
            moveTo(cx, cy - r)
            quadTo(cx + r * 0.2f, cy - r * 0.2f, cx + r, cy)
            quadTo(cx + r * 0.2f, cy + r * 0.2f, cx, cy + r)
            quadTo(cx - r * 0.2f, cy + r * 0.2f, cx - r, cy)
            quadTo(cx - r * 0.2f, cy - r * 0.2f, cx, cy - r)
            close()
        }
        canvas.drawPath(path, starPaint)
    }
    star(140f, 150f, 36f)
    star(w - 150f, 200f, 24f)
    star(180f, h - 160f, 28f)

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(61, 43, 61)
        textSize = 72f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val layout = StaticLayout.Builder
        .obtain(quote, 0, quote.length, textPaint, w - 260)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(12f, 1f)
        .build()
    val textTop = (h - layout.height) / 2f
    canvas.save()
    canvas.translate(130f, textTop)
    layout.draw(canvas)
    canvas.restore()

    val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(242, 123, 172)
        textSize = 42f
    }
    val caption = "MedTech Review Hub"
    val captionWidth = captionPaint.measureText(caption)
    canvas.drawText(caption, (w - captionWidth) / 2f, h - 140f, captionPaint)
    return bitmap
}
