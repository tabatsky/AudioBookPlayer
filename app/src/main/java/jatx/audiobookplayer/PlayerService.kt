package jatx.audiobookplayer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import jatx.audiobookplayer.models.PlaylistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileOutputStream

const val CHANNEL_ID_SERVICE = "PlayerService"
const val CHANNEL_NAME_SERVICE = "PlayerService"

class PlayerService : MediaBrowserServiceCompat() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private var player: Player? = null

    private var activeAudioFile: File? = null

    private var isPlaying = false

    private var progressJob: Job? = null

    private var progressBackup: Float? = null

    private var tempo: Float = 1.0f

    private val broadcastReceivers = arrayListOf<BroadcastReceiver>()

    private lateinit var mediaSessionCompat: MediaSessionCompat

    private val mediaSessionCallback: MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {

            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                val keyEvent =
                    mediaButtonEvent!!.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                Log.e("keyEvent", keyEvent.toString())
                if (keyEvent?.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode
                    in listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)) {

                    if (isPlaying) {
                        val intent = Intent(CLICK_PAUSE)
                        sendBroadcast(intent)
                    } else {
                        val intent = Intent(CLICK_PLAY)
                        sendBroadcast(intent)
                    }

                    return true
                } else if (keyEvent?.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {

                    val intent = Intent(CLICK_MINUS_15)
                    sendBroadcast(intent)

                    return true
                }

                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return if (TextUtils.equals(clientPackageName, packageName)) {
            BrowserRoot(getString(R.string.app_name), null)
        } else null
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem?>?>
    ) {
        result.sendResult(null)
    }


    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()

        registerReceivers()

        initMediaSession()

        return START_STICKY_COMPATIBILITY
    }

    override fun onDestroy() {
        unregisterReceivers()

        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun initMediaSession() {
        val mediaButtonReceiver = ComponentName(
            applicationContext,
            MediaButtonReceiver::class.java
        )
        mediaSessionCompat =
            MediaSessionCompat(applicationContext, "Tag", mediaButtonReceiver, null)
        mediaSessionCompat.setCallback(mediaSessionCallback)
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        val flags = if (Build.VERSION.SDK_INT < 23) {
            0
        } else {
            PendingIntent.FLAG_IMMUTABLE
        }

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.setClass(this, MediaButtonReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, flags)
        mediaSessionCompat.setMediaButtonReceiver(pendingIntent)
        sessionToken = mediaSessionCompat.sessionToken
    }

    private fun startForeground() {
        val actIntent = Intent()
        actIntent.setClass(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT < 23) {
            PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
        val pendingIntent =
            PendingIntent.getActivity(this, 0, actIntent, flags)

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            ""
        }

        val builder = NotificationCompat.Builder(this, channelId)

        val notification = builder
            .setContentTitle("AudioBookPlayer")
            .setContentText("Foreground service is running")
            .setContentIntent(pendingIntent)
            .build()

        startForeground(2315, notification)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            Toast
                .makeText(
                    this,
                    "Please enable notifications",
                    Toast.LENGTH_LONG
                )
                .show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channel = NotificationChannel(CHANNEL_ID_SERVICE, CHANNEL_NAME_SERVICE, NotificationManager.IMPORTANCE_MIN)
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val service = NotificationManagerCompat.from(this)
        service.createNotificationChannel(channel)
        return CHANNEL_ID_SERVICE
    }

    private fun registerReceivers() {
        val notifyPlaylistChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                playlistChanged()
            }
        }
        registerExportedReceiver(notifyPlaylistChangedReceiver, IntentFilter(NOTIFY_PLAYLIST_CHANGED))
        broadcastReceivers.add(notifyPlaylistChangedReceiver)

        val clickPlayReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scope.launch {
                    applyTempoAndPlayActiveFile()
                }
            }
        }
        registerExportedReceiver(clickPlayReceiver, IntentFilter(CLICK_PLAY))
        broadcastReceivers.add(clickPlayReceiver)

        val clickPauseReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                pausePlayer()
            }
        }
        registerExportedReceiver(clickPauseReceiver, IntentFilter(CLICK_PAUSE))
        broadcastReceivers.add(clickPauseReceiver)

        val clickNextReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                nextPlaylistItem()
            }
        }
        registerExportedReceiver(clickNextReceiver, IntentFilter(CLICK_NEXT))
        broadcastReceivers.add(clickNextReceiver)

        val clickPrevReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                prevPlaylistItem()
            }
        }
        registerExportedReceiver(clickPrevReceiver, IntentFilter(CLICK_PREV))
        broadcastReceivers.add(clickPrevReceiver)

        val clickPlus15Receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                plus15()
            }
        }
        registerExportedReceiver(clickPlus15Receiver, IntentFilter(CLICK_PLUS_15))
        broadcastReceivers.add(clickPlus15Receiver)

        val clickMinus15Receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                minus15()
            }
        }
        registerExportedReceiver(clickMinus15Receiver, IntentFilter(CLICK_MINUS_15))
        broadcastReceivers.add(clickMinus15Receiver)

        val clickProgressReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val progress = intent?.getIntExtra(KEY_PROGRESS, 0) ?: 0
                progressChangedByUser(progress)
            }
        }
        registerExportedReceiver(clickProgressReceiver, IntentFilter(CLICK_PROGRESS))
        broadcastReceivers.add(clickProgressReceiver)

        val clickPlaylistItemReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val name = intent?.getStringExtra(KEY_NAME) ?: ""
                val uriStr = intent?.getStringExtra(KEY_URI) ?: ""
                val uri = Uri.parse(uriStr)
                val playlistItem = PlaylistItem(name, uri)
                copyAndPlayPlaylistItem(playlistItem)
            }
        }
        registerExportedReceiver(clickPlaylistItemReceiver, IntentFilter(CLICK_PLAYLIST_ITEM))
        broadcastReceivers.add(clickPlaylistItemReceiver)

        val clickTempoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val tempoStr = intent?.getStringExtra(KEY_TEMPO) ?: "1.0"
                scope.launch {
                    applyTempo(tempoStr)
                }
            }
        }
        registerExportedReceiver(clickTempoReceiver, IntentFilter(CLICK_TEMPO))
        broadcastReceivers.add(clickTempoReceiver)
    }

    private fun unregisterReceivers() {
        broadcastReceivers.forEach {
            unregisterReceiver(it)
        }
        broadcastReceivers.clear()
    }

    private fun playlistChanged() {
        stopAndReleasePlayer()
        cleanPlaylistDir()
        notifyProgress(0, 1)
    }

    private fun notifyProgress(currentPosition: Int, duration: Int) {
        AppState.updateDuration(duration)
        AppState.updateCurrentPosition(currentPosition)
        duration.takeIf { it > 0 }?.let {
            val progress = 1f * currentPosition / duration
            AppState.updateProgress(progress)
        }
    }

    private fun cleanPlaylistDir() {
        FileUtils.cleanDirectory(getPlaylistDir())
    }

    private fun getPlaylistDir() = getExternalFilesDir(null)

    private fun notifyDuration(duration: Int) {
        AppState.updateDuration(duration)
        AppState.updateProgress(null)
    }

    private suspend fun showProgressDialog(show: Boolean) = withContext(Dispatchers.Main) {
        AppState.updateProgressDialogVisible(show)
    }

    private fun copyAndPlayPlaylistItem(playlistItem: PlaylistItem) {
        notifyDuration(0)
        scope.launch {
            showProgressDialog(true)
            stopAndReleasePlayer()
            withContext(Dispatchers.IO) {
                copyFileAndGetPath(playlistItem.uri)?.let { path ->
                    activeAudioFile = File(path)
                    withContext(Dispatchers.Main) {
                        AppState.updatePlaylistItem(playlistItem)
                        Log.e("last progress", App.settings.lastProgress.toString())
                        progressBackup = App.settings.lastProgress
                        applyTempoAndPlayActiveFile()
                    }
                }
            }
            showProgressDialog(false)
        }
    }

    private fun copyFileAndGetPath(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        cursor.moveToFirst()
        val displayName = cursor.getString(columnIndex)
        cursor.close()

        val inputStream = contentResolver.openInputStream(uri)
        val newFile = File(getPlaylistDir(), displayName)
        val fileOutputStream = FileOutputStream(newFile)

        inputStream?.copyTo(fileOutputStream)

        fileOutputStream.flush()
        fileOutputStream.close()
        inputStream?.close()

        return newFile.absolutePath
    }

    private suspend fun applyTempoAndPlayActiveFile() {
        isPlaying = true
        applyTempo(App.settings.tempo.toString())
    }

    private suspend fun applyTempo(tempoStr: String) {
        tempo = tempoStr.toFloat()

        val wasPlaying = isPlaying

        progressBackup = AppState.progress.value
        Log.e("prog backup", progressBackup.toString())
        showProgressDialog(true)
        stopAndReleasePlayer()
        if (wasPlaying) {
            playActiveFile()
        }
        showProgressDialog(false)
    }

    private suspend fun playActiveFile() {
        if (player == null) {
            player = ExoPlayer.Builder(applicationContext).build()

            activeAudioFile?.let {  theFile ->
                val mediaItem = MediaItem.fromUri(theFile.toUri())
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.addListener(object: Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_ENDED -> {
                                stopAndReleasePlayer()
                                notifyDuration(0)
                                App.settings.lastProgress = 0f
                                nextPlaylistItem()
                            }
                        }
                        super.onPlaybackStateChanged(playbackState)
                    }
                })
                val playbackParam = PlaybackParameters(tempo, 1.0f)
                player?.playbackParameters = playbackParam
                player?.play()
                while (player?.playbackState != Player.STATE_READY) {
                    delay(50)
                }
                val duration = player?.duration ?: 0L
                notifyDuration(duration.toInt())
                progressBackup?.let {
                    Log.e("prog backup", progressBackup.toString())
                    val progressMs = ((it * duration).toInt() - 3000).takeIf { it >= 0 } ?: 0
                    Log.e("seek to", progressMs.toString())
                    player?.seekTo(progressMs.toLong())
                    progressBackup = null
                } ?: run {
                    val progressMs = ((App.settings.lastProgress * duration).toInt() - 3000).takeIf { it >= 0 } ?: 0
                    player?.seekTo(progressMs.toLong())
                }
            }
        }

        progressJob = scope.launch {
            var counter = 0
            delay(300L)
            while (player?.playbackState == Player.STATE_READY ||
                player?.playbackState == Player.STATE_BUFFERING) {
                delay(50L)
                val currentPosition = player?.currentPosition ?: 0L
                val duration = player?.duration ?: 0L
                //Log.e("progress", "$currentPosition $duration")
                notifyProgress(currentPosition.toInt(), duration.toInt())
                if (counter % 20 == 0) {
                    AppState.progress.value?.let {
                        App.settings.lastProgress = it
                    }
                }
                counter++
            }
            Log.e("progress", "job finished")
        }

        isPlaying = true
        AppState.updateIsPlaying(true)
    }

    private fun stopAndReleasePlayer() {
        isPlaying = false
        AppState.updateIsPlaying(false)

        progressJob?.cancel()

        player?.stop()
        player?.release()
        player = null
    }

    private fun pausePlayer() {
        isPlaying = false
        AppState.updateIsPlaying(false)

        val currentPosition = player?.currentPosition ?: 0L
        val duration = player?.duration ?: 0L
        stopAndReleasePlayer()
        notifyProgress(currentPosition.toInt(), duration.toInt())
    }

    private fun nextPlaylistItem() {
        App.settings.lastProgress = 0f
        AppState.activePlaylistItem.value?.let { playlistItem ->
            val playlistItems = AppState.playlistItems.value
            val index = playlistItems?.indexOf(playlistItem)?.takeIf { it >= 0 }
            if (playlistItems != null && index != null) {
                val count = playlistItems.size
                val nextIndex = index + 1
                if (nextIndex < count) {
                    val nextPlaylistItem = playlistItems[nextIndex]
                    copyAndPlayPlaylistItem(nextPlaylistItem)
                } else {
                    showToast("No tracks after")
                }
            }
        }
    }

    private fun prevPlaylistItem() {
        App.settings.lastProgress = 0f
        AppState.activePlaylistItem.value?.let { playlistItem ->
            val playlistItems = AppState.playlistItems.value
            val index = playlistItems?.indexOf(playlistItem)?.takeIf { it >= 0 }
            if (playlistItems != null && index != null) {
                if (index > 0) {
                    val prevIndex = index - 1
                    val prevPlaylistItem = playlistItems[prevIndex]
                    copyAndPlayPlaylistItem(prevPlaylistItem)
                } else {
                    showToast("No tracks before")
                }
            }
        }
    }

    private fun plus15() {
        val currentPosition = player?.currentPosition?.toInt() ?: 0
        val duration = player?.duration?.toInt() ?: 0
        val newPosition = (currentPosition + 15000).takeIf { it <= duration } ?: duration
        progressChangedByUser(newPosition)
    }

    private fun minus15() {
        val currentPosition = player?.currentPosition?.toInt() ?: 0
        val newPosition = (currentPosition - 15000).takeIf { it >= 0 } ?: 0
        progressChangedByUser(newPosition)
    }

    private fun progressChangedByUser(currentPosition: Int) {
        Log.e("progress", currentPosition.toString())
        player?.seekTo(currentPosition.toLong())
        AppState.updateCurrentPosition(currentPosition)
        val duration = AppState.duration.value
        duration?.let {
            val progress = 1f * currentPosition / duration
            AppState.updateProgress(progress)
            App.settings.lastProgress = progress
        }
    }
}