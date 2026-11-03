package com.jjmluz.trumportrap.game

val POST_COUNTS = listOf(5, 10, 20, 50)
const val DEFAULT_POST_COUNT = 10

/** The next option in [POST_COUNTS], staying put at either end. */
fun stepPostCount(current: Int, step: Int): Int {
    val index = POST_COUNTS.indexOf(current) + step
    return POST_COUNTS[index.coerceIn(POST_COUNTS.indices)]
}

enum class Guess { TRUMP, TRAP }

enum class Phase { GUESSING, ANSWERED, FINISHED }

data class GameState(
    val posts: List<Post>,
    val index: Int = 0,
    val score: Int = 0,
    val phase: Phase = Phase.GUESSING,
    val lastGuess: Guess? = null,
    val lastGuessRight: Boolean = false,
) {
    val post: Post get() = posts[index]

    /** A second press while the answer is showing doesn't count as a guess on the same post. */
    fun guess(guess: Guess): GameState {
        if (phase != Phase.GUESSING) return this
        val right = post.isReal == (guess == Guess.TRUMP)
        return copy(
            score = if (right) score + 1 else score,
            phase = Phase.ANSWERED,
            lastGuess = guess,
            lastGuessRight = right,
        )
    }

    fun advance(): GameState {
        if (phase != Phase.ANSWERED) return this
        if (index == posts.lastIndex) return copy(phase = Phase.FINISHED)
        return copy(index = index + 1, phase = Phase.GUESSING)
    }
}
