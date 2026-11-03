package com.jjmluz.trumportrap.ui

import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Text
import com.jjmluz.trumportrap.R
import com.jjmluz.trumportrap.game.GameState
import com.jjmluz.trumportrap.game.Guess
import com.jjmluz.trumportrap.game.Phase
import com.jjmluz.trumportrap.game.Post
import com.jjmluz.trumportrap.ui.theme.CollegeFont
import com.jjmluz.trumportrap.ui.theme.TrumpOrTrapColors as C
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the answer stays on screen before the post flies off. */
private const val ANSWER_DELAY_MS = 2500L

private const val CARD_CORNER = 40

@Composable
fun PostScreen(game: GameState, onGuess: (Guess) -> Unit, onNext: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Each post is a fresh card: it comes in, leans toward the guess, then flies off that way.
    val card = remember(game.index) { CardMotion() }
    val scope = rememberCoroutineScope()
    val sounds = rememberAnswerSounds()
    val direction = game.lastGuess?.direction ?: 0f
    fun leave() {
        if (!card.startLeaving()) return
        scope.launch {
            card.flyOff(direction)
            onNext()
        }
    }

    LaunchedEffect(game.index) { card.enter() }
    LaunchedEffect(game.index, game.phase) {
        if (game.phase != Phase.ANSWERED) return@LaunchedEffect
        sounds.play(game.lastGuessRight)
        launch { card.lean(direction) }
        delay(ANSWER_DELAY_MS)
        leave()
    }

    DesignCanvas(
        modifier = Modifier
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val guess = when (event.key) {
                    Key.DirectionLeft -> Guess.TRAP
                    Key.DirectionRight -> Guess.TRUMP
                    Key.DirectionCenter, Key.Enter -> null
                    else -> return@onPreviewKeyEvent false
                }
                if (event.isFirstPress) {
                    when {
                        game.phase == Phase.ANSWERED -> leave()
                        guess != null -> onGuess(guess)
                    }
                }
                true
            }
            .focusable(),
    ) { d ->
        Choice(
            label = stringResource(R.string.choice_trap),
            color = C.Trap,
            chosen = game.phase == Phase.ANSWERED && game.lastGuess == Guess.TRAP,
            d = d,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = d.dp(44)),
        )
        Choice(
            label = stringResource(R.string.choice_trump),
            color = C.Trump,
            chosen = game.phase == Phase.ANSWERED && game.lastGuess == Guess.TRUMP,
            d = d,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = d.dp(44)),
        )
        PostCard(
            post = game.post,
            motion = card,
            glowColor = if (game.lastGuessRight) C.RightGlow else C.WrongGlow,
            d = d,
            modifier = Modifier.align(Alignment.Center),
        )
        if (game.phase == Phase.ANSWERED) Answer(game, d)
    }
}

/** Left for TRAP, right for TRUMP. */
private val Guess.direction: Float
    get() = if (this == Guess.TRUMP) 1f else -1f

/** Holding a direction repeats the key, which must not answer the next post too. */
private val KeyEvent.isFirstPress: Boolean
    get() = type == KeyEventType.KeyDown && nativeKeyEvent.repeatCount == 0

/** The card's movement, in design units and degrees, like a swipe in a dating app. */
@Stable
private class CardMotion {
    val offset = Animatable(0f)
    val tilt = Animatable(0f)
    val alpha = Animatable(0f)
    val scale = Animatable(0.94f)

    /** 0 is a faint grey shadow; 1 is the full green or red glow. */
    val glow = Animatable(0f)

    private var leaving = false

    suspend fun enter() = coroutineScope {
        launch { alpha.animateTo(1f, tween(220)) }
        launch { scale.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    }

    suspend fun lean(direction: Float) = coroutineScope {
        val springy = spring<Float>(dampingRatio = 0.7f, stiffness = 260f)
        launch { offset.animateTo(direction * 36f, springy) }
        launch { tilt.animateTo(direction * 3f, springy) }
        launch { glow.animateTo(1f, tween(260)) }
    }

    /** Only the first request counts, whether it comes from a key or the timer. */
    fun startLeaving(): Boolean {
        if (leaving) return false
        leaving = true
        return true
    }

    suspend fun flyOff(direction: Float) = coroutineScope {
        val out = tween<Float>(340, easing = FastOutLinearInEasing)
        launch { offset.animateTo(direction * 1500f, out) }
        launch { tilt.animateTo(direction * 22f, out) }
        launch { alpha.animateTo(0f, out) }
    }
}

/** The label grows a little when it's the one the player picked. */
@Composable
private fun Choice(label: String, color: Color, chosen: Boolean, d: Design, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (chosen) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 300f),
        label = "choiceScale",
    )
    Text(
        label,
        color = color,
        fontFamily = CollegeFont,
        fontSize = d.sp(52),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

@Composable
private fun PostCard(post: Post, motion: CardMotion, glowColor: Color, d: Design, modifier: Modifier = Modifier) {
    Column(
        modifier
            .graphicsLayer {
                translationX = d.dp(motion.offset.value).toPx()
                rotationZ = motion.tilt.value
                alpha = motion.alpha.value
                scaleX = motion.scale.value
                scaleY = motion.scale.value
            }
            .glow(glowColor, d) { motion.glow.value }
            .background(C.Surface, RoundedCornerShape(d.dp(CARD_CORNER)))
            .padding(horizontal = d.dp(40), vertical = d.dp(32))
            .width(d.dp(1060)),
    ) {
        Row(
            modifier = Modifier.padding(bottom = d.dp(36)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.dp(28)),
        ) {
            ProfilePicture(d)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.dp(12)),
                ) {
                    Text(
                        stringResource(R.string.author_name),
                        color = C.Ink,
                        fontSize = d.sp(44),
                        fontWeight = FontWeight.Bold,
                    )
                    Image(
                        painterResource(R.drawable.ic_verified),
                        contentDescription = stringResource(R.string.verified),
                        modifier = Modifier.size(d.dp(44)),
                    )
                }
                Text(stringResource(R.string.author_handle), color = C.Grey, fontSize = d.sp(36))
            }
        }
        Text(
            post.text,
            color = C.Ink,
            fontSize = d.sp(44),
            lineHeight = d.sp(62),
            textAlign = TextAlign.Start,
        )
    }
}

/** A blurred halo behind the card, read in the draw phase so animating it doesn't recompose. */
private fun Modifier.glow(color: Color, d: Design, strength: () -> Float): Modifier = drawBehind {
    val s = strength()
    val halo = lerp(C.Ink.copy(alpha = 0.10f), color.copy(alpha = 0.95f), s)
    val radius = d.dp(28 + 16 * s).toPx()
    // The answer glow reaches a little past the card's edge.
    val spread = d.dp(10 * s).toPx()
    val corner = d.dp(CARD_CORNER).toPx() + spread
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = halo.toArgb()
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawRoundRect(
            -spread, -spread, size.width + spread, size.height + spread, corner, corner, paint,
        )
    }
}

@Composable
private fun ProfilePicture(d: Design) {
    val context = LocalContext.current
    val picture = remember {
        context.assets.open("profile.jpg").use(BitmapFactory::decodeStream).asImageBitmap()
    }
    Image(
        bitmap = picture,
        contentDescription = stringResource(R.string.profile_picture),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(d.dp(128)).clip(CircleShape),
    )
}

/** Sits below the post, in space even the longest post leaves free. */
@Composable
private fun BoxScope.Answer(game: GameState, d: Design) {
    val message = when {
        game.post.isReal && game.lastGuessRight -> stringResource(R.string.right_real)
        game.post.isReal -> stringResource(R.string.wrong_real)
        game.lastGuessRight -> stringResource(R.string.right_trap)
        else -> stringResource(R.string.wrong_trap)
    }
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = d.dp(40))
            .background(if (game.lastGuessRight) C.Right else C.Wrong, CircleShape)
            .padding(horizontal = d.dp(56), vertical = d.dp(16)),
    ) {
        Text(message, color = C.Surface, fontSize = d.sp(40), fontWeight = FontWeight.Bold)
    }
}
