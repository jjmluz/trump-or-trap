package com.jjmluz.trumportrap.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.lightColorScheme
import com.jjmluz.trumportrap.R

/** White throughout, with the near-black, grey and blue of a social media post, and red TRUMP and blue TRAP. */
object TrumpOrTrapColors {
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0F1419)
    val Grey = Color(0xFF536471)
    val Line = Color(0xFFCFD9DE)
    val Blue = Color(0xFF1D9BF0)

    val Trump = Color(0xFFC8102E)
    val Trap = Color(0xFF1C4BB4)

    // Answer message, and the glow around the post.
    val Right = Color(0xFF00794A)
    val Wrong = Color(0xFFC8203A)
    val RightGlow = Color(0xFF14C45F)
    val WrongGlow = Color(0xFFF0283C)
}

/** Alfa Slab One, a heavy slab serif like college lettering. It has one weight, which is already bold. */
val CollegeFont = FontFamily(Font(R.font.alfa_slab_one))

@Composable
fun TrumpOrTrapTheme(content: @Composable () -> Unit) {
    val c = TrumpOrTrapColors
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = c.Ink,
            onPrimary = c.Surface,
            background = c.Surface,
            onBackground = c.Ink,
            surface = c.Surface,
            onSurface = c.Ink,
            onSurfaceVariant = c.Grey,
            border = c.Line,
        ),
    ) {
        // The default style spaces letters out; the mockups don't.
        ProvideTextStyle(TextStyle(letterSpacing = 0.sp), content)
    }
}
