package com.jjmluz.trumportrap.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Checks the deck the app ships with. Gradle runs unit tests from the app module's folder. */
class DeckTest {
    private val deck = parseDeck(File("../data/out/game_deck.json").readText())

    @Test
    fun theDeckIsHalfRealAndHalfFake() {
        assertEquals(deck.size / 2, deck.count { it.isReal })
    }

    @Test
    fun everyCardCanBeShown() {
        assertTrue(deck.isNotEmpty())
        assertEquals(deck.size, deck.map { it.id }.toSet().size)
        assertTrue(deck.all { it.text.isNotBlank() })
    }

    @Test
    fun aGameHasNoRepeats() {
        val game = PostQueue(deck, Random(1)).deal(POST_COUNTS.last())
        assertEquals(POST_COUNTS.last(), game.toSet().size)
    }

    @Test
    fun gamesDoNotRepeatPostsUntilTheDeckRunsOut() {
        val queue = PostQueue(deck, Random(2))
        val played = List(deck.size / POST_COUNTS.last()) { queue.deal(POST_COUNTS.last()) }.flatten()
        assertEquals(played.size, played.toSet().size)
    }

    @Test
    fun theQueueReshufflesWhenItRunsOutWithoutRepeatingWithinAGame() {
        val small = deck.take(6)
        val queue = PostQueue(small, Random(3))
        repeat(20) {
            val game = queue.deal(5)
            assertEquals(5, game.toSet().size)
        }
    }
}
