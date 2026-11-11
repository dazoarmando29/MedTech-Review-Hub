package com.medtech.reviewhub.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.domain.MascotMood

/**
 * Mascot bubble (shared/DESIGN.md v2 "MochiBubble" / "MascotBubble").
 * Vector-drawable mascot illustrations: res/drawable/mochi_*.xml (Mochi the
 * cat in a tiny lab coat) and bantay_*.xml (the optional dog companion) �?"
 * original simple shapes, one drawing per mood.
 */
@Composable
fun MascotBubble(
    kind: MascotKind,
    mood: MascotMood,
    line: String,
    modifier: Modifier = Modifier,
    name: String = kind.displayName,
    reduceAnimation: Boolean = false,
    sizeDp: Int = 72
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val scale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(if (reduceAnimation) 1 else 400),
            label = "mascot"
        )
        Image(
            painter = painterResource(mood.drawable(kind)),
            contentDescription = "$name (${mood.name.lowercase()})",
            modifier = Modifier
                .size(sizeDp.dp)
                .mascotBreathing(enabled = !reduceAnimation)
                .graphicsLayer { scaleX = scale; scaleY = scale }
        )
        Spacer(Modifier.width(8.dp))
        if (line.isNotBlank()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Slow, calming idle "breathing" bob: a 6-second ease-in-out cycle (3s up,
 * 3s down) with at most 3dp of vertical travel. Deliberately gentle - nothing
 * faster than 4s and no rotation - per feedback about fast jiggling.
 */
@Composable
private fun Modifier.mascotBreathing(enabled: Boolean): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "mascotBreathing")
    val bob by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascotBob"
    )
    return graphicsLayer { translationY = bob.dp.toPx() }
}

/** Compact mascot row without bubble (e.g. streak reactions). */
@Composable
fun MascotFace(kind: MascotKind, mood: MascotMood, sizeDp: Int = 48, modifier: Modifier = Modifier, reduceAnimation: Boolean = false) {
    Image(
        painter = painterResource(mood.drawable(kind)),
        contentDescription = null,
        modifier = modifier
            .size(sizeDp.dp)
            .mascotBreathing(enabled = !reduceAnimation)
    )
}

@Composable
fun MascotGreeting(kind: MascotKind, mood: MascotMood, greeting: String, modifier: Modifier = Modifier, reduceAnimation: Boolean = false) {
    Column(modifier = modifier) {
        MascotBubble(kind = kind, mood = mood, line = greeting, reduceAnimation = reduceAnimation)
    }
}

/** Bubble color for the blocking overlay (focus mode). */
val OverlayBubbleColor: Color = Color(0xFFF59E0B)
