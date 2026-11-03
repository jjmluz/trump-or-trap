package com.jjmluz.trumportrap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.jjmluz.trumportrap.ui.theme.TrumpOrTrapColors as C
import kotlin.math.min

// Screens are laid out on the 1920×1080 grid of mockups/index.html and scaled to the TV.
internal const val DESIGN_W = 1920f
internal const val DESIGN_H = 1080f

/** Converts design-grid units to dp, and to sp that ignore the system font scale so text always fits. */
@Stable
internal class Design(private val unit: Float, private val fontScale: Float) {
    fun dp(v: Number): Dp = (v.toFloat() * unit).dp
    fun sp(v: Number): TextUnit = (v.toFloat() * unit / fontScale).sp
}

/** Full-screen white surface with a centred, scaled 1920×1080 design area. */
@Composable
internal fun DesignCanvas(modifier: Modifier = Modifier, content: @Composable BoxScope.(Design) -> Unit) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(C.Surface),
        contentAlignment = Alignment.Center,
    ) {
        val d = Design(
            unit = min(maxWidth.value / DESIGN_W, maxHeight.value / DESIGN_H),
            fontScale = LocalDensity.current.fontScale,
        )
        Box(Modifier.size(d.dp(DESIGN_W), d.dp(DESIGN_H))) { content(d) }
    }
}

/** Near-black pill. A blue ring, with a white gap inside it, marks focus. */
@Composable
internal fun PillButton(text: String, onClick: () -> Unit, d: Design, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .border(d.dp(6), if (focused) C.Blue else Color.Transparent, CircleShape)
            .padding(d.dp(12))
            .background(C.Ink, CircleShape)
            .padding(horizontal = d.dp(104), vertical = d.dp(28)),
    ) {
        Text(text, color = C.Surface, fontSize = d.sp(48), fontWeight = FontWeight.Bold, lineHeight = d.sp(56))
    }
}
