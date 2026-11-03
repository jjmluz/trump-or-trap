package com.jjmluz.trumportrap.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jjmluz.trumportrap.R

/** The right and wrong cues from scripts/make_sounds.py, loaded up front so they play without a delay. */
internal class AnswerSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val rightSound = pool.load(context, R.raw.answer_right, 1)
    private val wrongSound = pool.load(context, R.raw.answer_wrong, 1)

    fun play(right: Boolean) {
        pool.play(if (right) rightSound else wrongSound, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}

@Composable
internal fun rememberAnswerSounds(): AnswerSounds {
    val context = LocalContext.current
    val sounds = remember { AnswerSounds(context.applicationContext) }
    DisposableEffect(sounds) {
        onDispose { sounds.release() }
    }
    return sounds
}
