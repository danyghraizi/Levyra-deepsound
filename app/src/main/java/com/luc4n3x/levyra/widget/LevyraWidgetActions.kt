package com.luc4n3x.levyra.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.luc4n3x.levyra.LevyraLaunchActions
import com.luc4n3x.levyra.MainActivity
import com.luc4n3x.levyra.player.PlaybackService
import com.luc4n3x.levyra.player.SystemMediaCommand

internal object LevyraWidgetActions {

    fun toggle(receiver: BroadcastReceiver, context: Context) {
        if (LevyraWidgetBridge.onToggle?.invoke() == true) return
        withController(receiver, context) { controller, finish ->
            if (controller == null) {
                openApp(context)
                finish()
                return@withController
            }
            if (controller.mediaItemCount > 0) {
                if (controller.isPlaying) controller.pause() else controller.play()
                finish()
                return@withController
            }
            val initialMediaId = controller.currentMediaItem?.mediaId
            sendCommand(controller, context, SystemMediaCommand.ResumePlayback) { handled ->
                if (handled) {
                    awaitPlaybackStart(controller, initialMediaId, expectItemChange = false, finish)
                } else {
                    openApp(context)
                    finish()
                }
            }
        }
    }

    fun skip(receiver: BroadcastReceiver, context: Context, forward: Boolean) {
        val bridge = if (forward) LevyraWidgetBridge.onNext else LevyraWidgetBridge.onPrevious
        if (bridge?.invoke() == true) return
        withController(receiver, context) { controller, finish ->
            if (controller == null) {
                openApp(context)
                finish()
                return@withController
            }
            if (controller.mediaItemCount > 0) {
                val command = if (forward) Player.COMMAND_SEEK_TO_NEXT else Player.COMMAND_SEEK_TO_PREVIOUS
                if (controller.isCommandAvailable(command)) {
                    if (forward) controller.seekToNext() else controller.seekToPrevious()
                    finish()
                    return@withController
                }
            }
            val command = if (forward) SystemMediaCommand.SkipNext else SystemMediaCommand.SkipPrevious
            val initialMediaId = controller.currentMediaItem?.mediaId
            sendCommand(controller, context, command) { handled ->
                if (handled) {
                    awaitPlaybackStart(controller, initialMediaId, expectItemChange = true, finish)
                } else {
                    openApp(context)
                    finish()
                }
            }
        }
    }

    fun withController(
        receiver: BroadcastReceiver,
        context: Context,
        command: (MediaController?, finish: () -> Unit) -> Unit
    ) {
        val appContext = context.applicationContext
        val pending = receiver.goAsync()
        val future = MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        ).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            var finished = false
            val finish = {
                if (!finished) {
                    finished = true
                    controller?.release()
                    pending.finish()
                }
            }
            runCatching { command(controller, finish) }.onFailure { finish() }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun awaitPlaybackStart(
        controller: MediaController,
        initialMediaId: String?,
        expectItemChange: Boolean,
        finish: () -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        lateinit var listener: Player.Listener
        val finishOnce = {
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                controller.removeListener(listener)
                finish()
            }
        }
        val started = {
            val playable = controller.isPlaying ||
                (controller.playWhenReady &&
                    (controller.playbackState == Player.STATE_READY ||
                        controller.playbackState == Player.STATE_BUFFERING))
            val mediaChanged = !expectItemChange ||
                controller.currentMediaItem?.mediaId != initialMediaId
            playable && mediaChanged
        }
        listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (started()) finishOnce()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (started()) finishOnce()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (started()) finishOnce()
            }
        }
        controller.addListener(listener)
        if (started()) {
            finishOnce()
            return
        }
        handler.postDelayed(finishOnce, PLAYBACK_START_TIMEOUT_MS)
    }

    private fun sendCommand(
        controller: MediaController,
        context: Context,
        command: SystemMediaCommand,
        onResult: (Boolean) -> Unit
    ) {
        val sessionCommand = SessionCommand(command.customAction, Bundle.EMPTY)
        if (!controller.isSessionCommandAvailable(sessionCommand)) {
            onResult(false)
            return
        }
        Futures.addCallback(
            controller.sendCustomCommand(sessionCommand, Bundle.EMPTY),
            object : FutureCallback<SessionResult> {
                override fun onSuccess(result: SessionResult) {
                    onResult(result.resultCode == SessionResult.RESULT_SUCCESS)
                }

                override fun onFailure(error: Throwable) {
                    onResult(false)
                }
            },
            ContextCompat.getMainExecutor(context.applicationContext)
        )
    }

    fun openApp(context: Context, shortcut: String? = null) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (shortcut != null) putExtra(LevyraLaunchActions.EXTRA_SHORTCUT, shortcut)
        }
        context.startActivity(intent)
    }

    private const val PLAYBACK_START_TIMEOUT_MS = 6_000L
}
