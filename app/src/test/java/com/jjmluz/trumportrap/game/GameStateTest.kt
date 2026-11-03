package com.jjmluz.trumportrap.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {
    private val real = Post("real", "A real post", isReal = true)
    private val fake = Post("fake", "A fake post", isReal = false)

    @Test
    fun trumpIsRightForARealPost() {
        val answered = GameState(listOf(real)).guess(Guess.TRUMP)
        assertTrue(answered.lastGuessRight)
        assertEquals(1, answered.score)
        assertEquals(Phase.ANSWERED, answered.phase)
        assertEquals(Guess.TRUMP, answered.lastGuess)
    }

    @Test
    fun trapIsRightForAFakePost() {
        val answered = GameState(listOf(fake)).guess(Guess.TRAP)
        assertTrue(answered.lastGuessRight)
        assertEquals(1, answered.score)
    }

    @Test
    fun wrongGuessesScoreNothing() {
        val onReal = GameState(listOf(real)).guess(Guess.TRAP)
        val onFake = GameState(listOf(fake)).guess(Guess.TRUMP)
        assertFalse(onReal.lastGuessRight)
        assertFalse(onFake.lastGuessRight)
        assertEquals(0, onReal.score + onFake.score)
    }

    @Test
    fun aSecondPressCannotAnswerTheSamePostAgain() {
        val answered = GameState(listOf(real)).guess(Guess.TRUMP)
        assertSame(answered, answered.guess(Guess.TRUMP))
    }

    @Test
    fun advanceWaitsForAnAnswer() {
        val game = GameState(listOf(real, fake))
        assertSame(game, game.advance())
    }

    @Test
    fun advanceMovesToTheNextPostAndKeepsTheScore() {
        val next = GameState(listOf(real, fake)).guess(Guess.TRUMP).advance()
        assertEquals(fake, next.post)
        assertEquals(1, next.score)
        assertEquals(Phase.GUESSING, next.phase)
    }

    @Test
    fun theLastAnswerFinishesTheGame() {
        val finished = GameState(listOf(real, fake))
            .guess(Guess.TRUMP).advance()
            .guess(Guess.TRUMP).advance()
        assertEquals(Phase.FINISHED, finished.phase)
        assertEquals(1, finished.score)
        assertSame(finished, finished.advance())
    }

    @Test
    fun postCountStepsThroughTheOptionsAndStopsAtTheEnds() {
        assertEquals(20, stepPostCount(10, 1))
        assertEquals(5, stepPostCount(10, -1))
        assertEquals(5, stepPostCount(5, -1))
        assertEquals(50, stepPostCount(50, 1))
    }

    @Test
    fun theDefaultPostCountIsAnOption() {
        assertEquals(10, DEFAULT_POST_COUNT)
        assertTrue(DEFAULT_POST_COUNT in POST_COUNTS)
    }
}
