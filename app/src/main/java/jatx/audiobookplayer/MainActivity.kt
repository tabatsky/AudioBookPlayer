package jatx.audiobookplayer

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.NumberPicker
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import jatx.audiobookplayer.databinding.ActivityMainBinding
import jatx.audiobookplayer.library.LibraryFragmentDirections
import jatx.audiobookplayer.models.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

const val CLICK_PLAY = "jatx.audiobookplayer.CLICK_PLAY"
const val CLICK_PAUSE = "jatx.audiobookplayer.CLICK_PAUSE"
const val CLICK_NEXT = "jatx.audiobookplayer.CLICK_NEXT"
const val CLICK_PREV = "jatx.audiobookplayer.CLICK_PREV"
const val CLICK_PLUS_15 = "jatx.audiobookplayer.CLICK_PLUS_15"
const val CLICK_MINUS_15 = "jatx.audiobookplayer.CLICK_MINUS_15"
const val CLICK_PROGRESS = "jatx.audiobookplayer.CLICK_PROGRESS"
const val CLICK_PLAYLIST_ITEM = "jatx.audiobookplayer.CLICK_PLAYLIST_ITEM"

const val NOTIFY_PROGRESS = "jatx.audiobookplayer.NOTIFY_PROGRESS"
const val NOTIFY_DURATION = "jatx.audiobookplayer.NOTIFY_DURATION"

const val KEY_PROGRESS = "progress"
const val KEY_DURATION = "duration"
const val KEY_NAME = "name"
const val KEY_URI = "uri"

class MainActivity : FragmentActivity() {

    private lateinit var navController: NavController

    private lateinit var binding: ActivityMainBinding

    private var mediaPlayer: MediaPlayer? = null

    private var activeAudioFile: File? = null
    private var activeAudioFileWithTempo: File? = null

    private var isPlaying = false

    private var progressJob: Job? = null
    private var progress: Float? = null

    private var progressDialog: Dialog? = null

    private val broadcastReceivers = arrayListOf<BroadcastReceiver>()

    private var tempo: Double = 1.0
        set(value) {
            if (value != field) {
                lifecycleScope.launch {
                    applyTempo(value.toString())
                }
            }
            field = value
            binding.btnSelectTempo.text = value.toString()
        }

    private val openDirResultLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                App.settings.audioBooksDirUri = uri

                val contentResolver = applicationContext.contentResolver

                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                loadAudioBookDir(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.fragment_container)
                as NavHostFragment
        navController = navHost.navController

        try {
            loadAudioBookDir(App.settings.audioBooksDirUri)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding.btnOpenDir.setOnClickListener {
            openDirResultLauncher.launch(App.settings.audioBooksDirUri)
        }

        binding.btnSelectTempo.setOnClickListener {
            showSelectTempoDialog()
        }

        binding.btnPlay.setOnClickListener {
            clickPlay()
        }

        binding.btnPause.setOnClickListener {
            clickPause()
        }

        binding.btnNext.setOnClickListener {
            clickNext()
        }

        binding.btnPrev.setOnClickListener {
            clickPrev()
        }

        binding.btnPlus15.setOnClickListener {
            clickPlus15()
        }

        binding.btnMinus15.setOnClickListener {
            clickMinus15()
        }

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    clickProgress(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        binding.seekBar.setOnSeekBarChangeListener(seekBarListener)

        val builder = AlertDialog.Builder(this)
        builder.setView(R.layout.dialog_progress)
        progressDialog = builder.create()
    }

    override fun onStart() {
        super.onStart()
        App.activityProvider.currentActivity = this
        registerReceivers()
    }

    override fun onStop() {
        super.onStop()
        App.activityProvider.currentActivity = null
        unregisterReceivers()
    }

    fun openPlaylist(playlistName: String) {
        if (playlistName != AppState.playlistName.value) {
            stopAndReleasePlayer()
            cleanPlaylistDir()
            notifyProgress(0, 1)
            AppState.updatePlaylistName(playlistName)
        }
        val action = LibraryFragmentDirections.actionLibraryFragmentToPlaylistFragment()
        navController.navigate(action)
    }

    fun clickPlaylistItem(playlistItem: PlaylistItem) {
        val intent = Intent(CLICK_PLAYLIST_ITEM)
        intent.putExtra(KEY_NAME, playlistItem.name)
        intent.putExtra(KEY_URI, playlistItem.uri.toString())
        sendBroadcast(intent)
    }

    private fun clickPlay() {
        val intent = Intent(CLICK_PLAY)
        sendBroadcast(intent)
    }

    private fun clickPause() {
        val intent = Intent(CLICK_PAUSE)
        sendBroadcast(intent)
    }

    private fun clickNext() {
        val intent = Intent(CLICK_NEXT)
        sendBroadcast(intent)
    }

    private fun clickPrev() {
        val intent = Intent(CLICK_PREV)
        sendBroadcast(intent)
    }

    private fun clickPlus15() {
        val intent = Intent(CLICK_PLUS_15)
        sendBroadcast(intent)
    }

    private fun clickMinus15() {
        val intent = Intent(CLICK_MINUS_15)
        sendBroadcast(intent)
    }

    private fun clickProgress(progress: Int) {
        val intent = Intent(CLICK_PROGRESS)
        intent.putExtra(KEY_PROGRESS, progress)
        sendBroadcast(intent)
    }

    private fun notifyProgress(progress: Int, duration: Int) {
        val intent = Intent(NOTIFY_PROGRESS)
        intent.putExtra(KEY_PROGRESS, progress)
        intent.putExtra(KEY_DURATION, duration)
        sendBroadcast(intent)
    }

    private fun notifyDuration(duration: Int) {
        val intent = Intent(NOTIFY_DURATION)
        intent.putExtra(KEY_DURATION, duration)
        sendBroadcast(intent)
    }

    private fun registerReceivers() {
        val clickPlayReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                lifecycleScope.launch {
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

        val notifyProgressReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val progress = intent?.getIntExtra(KEY_PROGRESS, 0) ?: 0
                val duration = intent?.getIntExtra(KEY_DURATION, 0) ?: 0
                progressChangedByPlayer(progress, duration)
            }
        }
        registerExportedReceiver(notifyProgressReceiver, IntentFilter(NOTIFY_PROGRESS))
        broadcastReceivers.add(notifyProgressReceiver)

        val notifyDurationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val duration = intent?.getIntExtra(KEY_DURATION, 0) ?: 0
                durationChanged(duration)
            }
        }
        registerExportedReceiver(notifyDurationReceiver, IntentFilter(NOTIFY_DURATION))
        broadcastReceivers.add(notifyDurationReceiver)
    }

    private fun unregisterReceivers() {
        broadcastReceivers.forEach {
            unregisterReceiver(it)
        }
        broadcastReceivers.clear()
    }

    private suspend fun showProgressDialog(show: Boolean) = withContext(Dispatchers.Main) {
        if (show) {
            progressDialog?.show()
        } else {
            progressDialog?.dismiss()
        }
    }

    private fun showSelectTempoDialog(): Dialog? {
        val tempoStrValues =  (0..30)
            .map {
                (it - 10) * 0.05 + 1.0
            }
            .map {
                ((it * 100.0).roundToInt() / 100.0).toString()
            }
            .toTypedArray()

        val numberPicker = NumberPicker(this)
        numberPicker.maxValue = 30
        numberPicker.minValue = 0
        numberPicker.value = tempoStrValues.indexOf(tempo.toString()).takeIf { it >=0 } ?: 10
        numberPicker.wrapSelectorWheel = false
        numberPicker.displayedValues = tempoStrValues

        val builder = AlertDialog.Builder(this)
        builder.setView(numberPicker)
        builder.setTitle("Changing the Tempo")
        builder.setMessage("Choose a value :")
        builder.setPositiveButton("OK") { dialog, _ ->
            tempo = numberPicker.value
                .let {
                    (it - 10) * 0.05 + 1.0
                }
                .let {
                    ((it * 100.0).roundToInt() / 100.0)
                }
            dialog.dismiss()
        }
        builder.setNegativeButton("CANCEL") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create()
        return builder.show()
    }

    private fun cleanPlaylistDir() {
        FileUtils.cleanDirectory(getPlaylistDir())
    }

    private fun getPlaylistDir() = getExternalFilesDir(null)

    private fun loadAudioBookDir(dirUri: Uri) {
        val pickedDir = DocumentFile.fromTreeUri(this, dirUri)
        pickedDir?.let {
            lifecycleScope.launch {
                withContext(Dispatchers.Main) {
                    AppState.updateMp3Files(pickedDir)
                }
            }
        }
    }

    private fun copyAndPlayPlaylistItem(playlistItem: PlaylistItem) {
        progress = null
        lifecycleScope.launch {
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
        applyTempo(tempo.toString())
    }

    private suspend fun applyTempo(tempoStr: String) {
        val activeAudioFile = this.activeAudioFile ?: return

        val fileName = "${activeAudioFile.nameWithoutExtension}_${tempoStr}.wav"
        val activeAudioFileWithTempo = File(getPlaylistDir(), fileName)

        val wasPlaying = isPlaying

        showProgressDialog(true)
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
            this@MainActivity.activeAudioFileWithTempo = activeAudioFileWithTempo
        }
        if (wasPlaying) {
            withContext(Dispatchers.Main) {
                playActiveFile()
            }
        }
        showProgressDialog(false)
    }

    private fun playActiveFile() {
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
                    progress = null
                }
                val duration = mediaPlayer?.duration ?: 0
                notifyDuration(duration)
                progress?.let {
                    val progressMs = ((it * duration).toInt() - 3000).takeIf { it >= 0 } ?: 0
                    mediaPlayer?.seekTo(progressMs)
                    binding.seekBar.progress = progressMs
                }
                mediaPlayer?.start()
            }
        } else if (mediaPlayer?.isPlaying != true) {
            mediaPlayer?.start()
        }

        progressJob = lifecycleScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                delay(50L)
                withContext(Dispatchers.Main) {
                    val currentPosition = mediaPlayer?.currentPosition ?: 0
                    val duration = mediaPlayer?.duration ?: 0
                    notifyProgress(currentPosition, duration)
                }
            }
        }

        isPlaying = true

        binding.btnPlay.visibility = View.GONE
        binding.btnPause.visibility = View.VISIBLE
    }

    private fun stopAndReleasePlayer() {
        isPlaying = false

        progressJob?.cancel()

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        binding.btnPlay.visibility = View.VISIBLE
        binding.btnPause.visibility = View.GONE
    }

    private fun pausePlayer() {
        isPlaying = false

        mediaPlayer?.pause()

        binding.btnPlay.visibility = View.VISIBLE
        binding.btnPause.visibility = View.GONE
    }

    private fun nextPlaylistItem() {
        AppState.activePlaylistItem.value?.let { playlistItem ->
            val playlistItems = AppState.playlistItems.value
            val index = playlistItems?.indexOf(playlistItem)?.takeIf { it >= 0 }
            if (playlistItems != null && index != null) {
                val count = playlistItems.size
                val nextIndex = (index + 1) % count
                val nextPlaylistItem = playlistItems[nextIndex]
                clickPlaylistItem(nextPlaylistItem)
            }
        }
    }

    private fun prevPlaylistItem() {
        AppState.activePlaylistItem.value?.let { playlistItem ->
            val playlistItems = AppState.playlistItems.value
            val index = playlistItems?.indexOf(playlistItem)?.takeIf { it >= 0 }
            if (playlistItems != null && index != null) {
                val count = playlistItems.size
                val prevIndex = if (index > 0) {
                    index - 1
                } else {
                    count - 1
                }
                val prevPlaylistItem = playlistItems[prevIndex]
                clickPlaylistItem(prevPlaylistItem)
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

    private fun progressChangedByPlayer(currentPosition: Int, duration: Int) {
        duration.takeIf { it > 0 } ?.let {
            progress = 1f * currentPosition / duration
        }
        binding.seekBar.progress = currentPosition
    }

    private fun durationChanged(duration: Int) {
        binding.seekBar.max = duration
    }

    private fun progressChangedByUser(progress: Int) {
        mediaPlayer?.seekTo(progress)
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