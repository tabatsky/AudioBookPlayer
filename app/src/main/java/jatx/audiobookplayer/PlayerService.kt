package jatx.audiobookplayer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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

class PlayerService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var mediaPlayer: MediaPlayer? = null

    private var activeAudioFile: File? = null
    private var activeAudioFileWithTempo: File? = null

    private var isPlaying = false

    private var progressJob: Job? = null

    private var progressBackup: Float? = null

    private val broadcastReceivers = arrayListOf<BroadcastReceiver>()

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()

        registerReceivers()

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
            withContext(Dispatchers.Main) {
                stopAndReleasePlayer()
            }
            withContext(Dispatchers.IO) {
                copyFileAndGetPath(playlistItem.uri)?.let { path ->
                    activeAudioFile = File(path)
                    withContext(Dispatchers.Main) {
                        AppState.updatePlaylistItem(playlistItem)
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
        val activeAudioFile = this.activeAudioFile ?: return

        val fileName = "${activeAudioFile.nameWithoutExtension}_${tempoStr}.wav"
        val activeAudioFileWithTempo = File(getPlaylistDir(), fileName)

        val wasPlaying = isPlaying

        showProgressDialog(true)
        progressBackup = AppState.progress.value
        withContext(Dispatchers.Main) {
            stopAndReleasePlayer()
        }
        withContext(Dispatchers.IO) {
            if (!activeAudioFileWithTempo.exists()) {
                applyTempoJNI(
                    activeAudioFile.absolutePath,
                    activeAudioFileWithTempo.absolutePath,
                    tempoStr
                )
            }
            this@PlayerService.activeAudioFileWithTempo = activeAudioFileWithTempo
        }
        if (wasPlaying) {
            withContext(Dispatchers.Main) {
                playActiveFile()
            }
        }
        showProgressDialog(false)
    }

    private suspend fun playActiveFile() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }

            activeAudioFileWithTempo?.let {  theFile ->
                mediaPlayer?.setDataSource(applicationContext, theFile.toUri())
                mediaPlayer?.prepare()
                mediaPlayer?.setOnCompletionListener {
                    stopAndReleasePlayer()
                    notifyDuration(0)
                    App.settings.lastProgress = 0f
                    nextPlaylistItem()
                }
                val duration = mediaPlayer?.duration ?: 0
                notifyDuration(duration)
                progressBackup?.let {
                    val progressMs = ((it * duration).toInt() - 3000).takeIf { it >= 0 } ?: 0
                    mediaPlayer?.seekTo(progressMs)
                    progressBackup = null
                } ?: run {
                    val progressMs = ((App.settings.lastProgress * duration).toInt() - 3000).takeIf { it >= 0 } ?: 0
                    mediaPlayer?.seekTo(progressMs)
                }
                mediaPlayer?.start()
            }
        }

        progressJob = scope.launch {
            var counter = 0
            while (mediaPlayer?.isPlaying == true) {
                delay(50L)
                val currentPosition = mediaPlayer?.currentPosition ?: 0
                val duration = mediaPlayer?.duration ?: 0
                withContext(Dispatchers.Main) {
                    notifyProgress(currentPosition, duration)
                }
                if (counter % 20 == 0) {
                    AppState.progress.value?.let {
                        App.settings.lastProgress = it
                    }
                }
                counter++
            }
        }

        isPlaying = true
        AppState.updateIsPlaying(true)
    }

    private fun stopAndReleasePlayer() {
        isPlaying = false
        AppState.updateIsPlaying(false)

        progressJob?.cancel()

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun pausePlayer() {
        isPlaying = false
        AppState.updateIsPlaying(false)

        mediaPlayer?.pause()
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
        val currentPosition = mediaPlayer?.currentPosition ?: 0
        val duration = mediaPlayer?.duration ?: 0
        val newPosition = (currentPosition + 15000).takeIf { it <= duration } ?: duration
        progressChangedByUser(newPosition)
    }

    private fun minus15() {
        val currentPosition = mediaPlayer?.currentPosition ?: 0
        val newPosition = (currentPosition - 15000).takeIf { it >= 0 } ?: 0
        progressChangedByUser(newPosition)
    }

    private fun progressChangedByUser(currentPosition: Int) {
        Log.e("progress", currentPosition.toString())
        mediaPlayer?.seekTo(currentPosition)
        AppState.updateCurrentPosition(currentPosition)
        val duration = AppState.duration.value
        duration?.let {
            val progress = 1f * currentPosition / duration
            AppState.updateProgress(progress)
            App.settings.lastProgress = progress
        }
    }

    /**
     * A native method that is implemented by the 'audiobookplayer' native library,
     * which is packaged with this application.
     */
    private external fun applyTempoJNI(inPath: String, outPath: String, tempo: String): Int

    companion object {
        // Used to load the 'audiobookplayer' library on application startup.
        init {
            System.loadLibrary("sox")
            System.loadLibrary("audiobookplayer")
        }
    }
}