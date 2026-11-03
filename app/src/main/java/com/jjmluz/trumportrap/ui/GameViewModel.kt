package com.jjmluz.trumportrap.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jjmluz.trumportrap.game.DEFAULT_POST_COUNT
import com.jjmluz.trumportrap.game.GameState
import com.jjmluz.trumportrap.game.Guess
import com.jjmluz.trumportrap.game.PostQueue
import com.jjmluz.trumportrap.game.parseDeck
import com.jjmluz.trumportrap.game.stepPostCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val queue = PostQueue(
        parseDeck(application.assets.open("game_deck.json").bufferedReader().use { it.readText() }),
    )

    private val _postCount = MutableStateFlow(DEFAULT_POST_COUNT)
    val postCount: StateFlow<Int> = _postCount.asStateFlow()

    private val _game = MutableStateFlow<GameState?>(null)

    /** Null on the title screen. */
    val game: StateFlow<GameState?> = _game.asStateFlow()

    fun stepCount(step: Int) = _postCount.update { stepPostCount(it, step) }

    fun start() {
        _game.value = GameState(queue.deal(_postCount.value))
    }

    fun guess(guess: Guess) = _game.update { it?.guess(guess) }

    fun advance() = _game.update { it?.advance() }

    fun quit() {
        _game.value = null
    }
}
