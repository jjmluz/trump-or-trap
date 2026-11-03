package com.jjmluz.trumportrap.game

import org.json.JSONArray
import kotlin.random.Random

data class Post(
    val id: String,
    val text: String,
    val isReal: Boolean,
)

/** Reads the cards built by scripts/build_fake_posts.py. */
fun parseDeck(json: String): List<Post> {
    val cards = JSONArray(json)
    return List(cards.length()) { index ->
        val card = cards.getJSONObject(index)
        Post(
            id = card.getString("id"),
            text = card.getString("text"),
            isReal = card.getString("label") == "real",
        )
    }
}

/** Deals posts in a shuffled order, so nothing repeats until the whole deck has been played. */
class PostQueue(private val posts: List<Post>, private val random: Random = Random) {
    private var remaining = posts.shuffled(random)

    fun deal(count: Int): List<Post> {
        val dealt = mutableListOf<Post>()
        while (dealt.size < count) {
            if (remaining.isEmpty()) remaining = posts.shuffled(random)
            val next = remaining.first()
            remaining = remaining.drop(1)
            // A reshuffle can bring back a post that's already in this game.
            if (next !in dealt) dealt += next
        }
        return dealt
    }
}
