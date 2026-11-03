package com.jjmluz.trumportrap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Text
import com.jjmluz.trumportrap.R
import com.jjmluz.trumportrap.game.POST_COUNTS
import com.jjmluz.trumportrap.ui.theme.CollegeFont
import com.jjmluz.trumportrap.ui.theme.TrumpOrTrapColors as C

@Composable
fun TitleScreen(postCount: Int, onStep: (Int) -> Unit, onStart: () -> Unit) {
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { start.requestFocus() }

    DesignCanvas { d ->
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = C.Trump)) { append(stringResource(R.string.title_trump)) }
                    append(" ")
                    append(stringResource(R.string.title_or))
                    append(" ")
                    withStyle(SpanStyle(color = C.Trap)) { append(stringResource(R.string.title_trap)) }
                },
                color = C.Ink,
                fontFamily = CollegeFont,
                fontSize = d.sp(128),
                modifier = Modifier.padding(bottom = d.dp(104)),
            )
            Text(
                stringResource(R.string.number_of_posts),
                color = C.Grey,
                fontSize = d.sp(36),
                modifier = Modifier.padding(bottom = d.dp(20)),
            )
            PostCountStepper(postCount, onStep, d, Modifier.padding(bottom = d.dp(72)))
            PillButton(
                text = stringResource(R.string.start),
                onClick = onStart,
                d = d,
                modifier = Modifier.focusRequester(start),
            )
        }
    }
}

/** Left and right change the number while it has focus; a blue ring shows that it does. */
@Composable
private fun PostCountStepper(postCount: Int, onStep: (Int) -> Unit, d: Design, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                val step = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) onStep(step)
                true
            }
            .focusable(interactionSource = interactions),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.dp(44)),
    ) {
        val atStart = postCount == POST_COUNTS.first()
        val atEnd = postCount == POST_COUNTS.last()
        Chevron(R.drawable.ic_chevron_left, if (atStart) C.Line else C.Grey, d)
        Box(
            modifier = Modifier
                .width(d.dp(200))
                .height(d.dp(120))
                .border(
                    width = if (focused) d.dp(6) else d.dp(3),
                    color = if (focused) C.Blue else C.Line,
                    shape = RoundedCornerShape(d.dp(20)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                postCount.toString(),
                color = C.Ink,
                fontSize = d.sp(76),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Chevron(R.drawable.ic_chevron_right, if (atEnd) C.Line else C.Grey, d)
    }
}

@Composable
private fun Chevron(icon: Int, color: Color, d: Design) {
    Image(
        painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(d.dp(44)),
    )
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun TitleScreenPreview() {
    TitleScreen(postCount = 10, onStep = {}, onStart = {})
}
