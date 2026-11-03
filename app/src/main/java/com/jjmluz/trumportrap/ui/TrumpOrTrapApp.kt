package com.jjmluz.trumportrap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jjmluz.trumportrap.game.Phase

/** The game state decides the screen. Back leaves a game; on the title screen it exits the app. */
@Composable
fun TrumpOrTrapApp(vm: GameViewModel = viewModel()) {
    val game by vm.game.collectAsStateWithLifecycle()
    val postCount by vm.postCount.collectAsStateWithLifecycle()
    BackHandler(enabled = game != null, onBack = vm::quit)

    val current = game
    when {
        current == null -> TitleScreen(postCount = postCount, onStep = vm::stepCount, onStart = vm::start)
        current.phase == Phase.FINISHED -> SummaryScreen(
            score = current.score,
            total = current.posts.size,
            onPlayAgain = vm::quit,
        )
        else -> PostScreen(game = current, onGuess = vm::guess, onNext = vm::advance)
    }
}
