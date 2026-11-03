package com.jjmluz.trumportrap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.jjmluz.trumportrap.R
import com.jjmluz.trumportrap.ui.theme.TrumpOrTrapColors as C

@Composable
fun SummaryScreen(score: Int, total: Int, onPlayAgain: () -> Unit) {
    val playAgain = remember { FocusRequester() }
    LaunchedEffect(Unit) { playAgain.requestFocus() }

    DesignCanvas { d ->
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.summary_label), color = C.Grey, fontSize = d.sp(60))
            Text(
                stringResource(R.string.summary_score, score, total),
                color = C.Ink,
                fontSize = d.sp(168),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                modifier = Modifier.padding(top = d.dp(16), bottom = d.dp(96)),
            )
            PillButton(
                text = stringResource(R.string.play_again),
                onClick = onPlayAgain,
                d = d,
                modifier = Modifier.focusRequester(playAgain),
            )
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SummaryScreenPreview() {
    SummaryScreen(score = 6, total = 10, onPlayAgain = {})
}
