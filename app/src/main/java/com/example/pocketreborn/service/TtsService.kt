package com.example.pocketreborn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pocketreborn.MainActivity
import com.example.pocketreborn.data.local.AppDatabase
import com.example.pocketreborn.data.repository.ArticleRepository
import com.example.pocketreborn.data.repository.UserPreferences
import com.example.pocketreborn.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

data class TtsPlaybackState(
    val articleId: String? = null,
    val articleTitle: String = "",
    val publisher: String = "",
    val isPlaying: Boolean = false,
    val isCompleted: Boolean = false,
    val currentParagraphIndex: Int = 0,
    val totalParagraphs: Int = 0,
    val currentCharOffset: Int = 0,
    val currentChunkLength: Int = 0,
    val speed: Float = 1.0f,
    val selectedEngine: String = "",
    val isInitialized: Boolean = false,
    val isRepeatEnabled: Boolean = false,
    val offlineVoiceAvailable: Boolean? = null,
    val errorMessage: String? = null
)

class TtsService : Service(), TextToSpeech.OnInitListener {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var tts: TextToSpeech? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var chunks: List<String> = emptyList()
    private var currentArticleId: String? = null
    private var activeChunkStartOffset = 0
    @Volatile private var latestCharOffset = 0
    @Volatile private var currentUtteranceId: String? = null
    @Volatile private var currentUtteranceIndex = -1
    private var resumeOnFocusGain = false
    private var generation = 0
    private var pendingStart = false
    @Volatile private var isUserPaused = false
    private var repository: ArticleRepository? = null
    private var userPreferencesRepository: UserPreferencesRepository? = null
    private var currentEnginePackage: String = ""
    private var userPrefs: UserPreferences = UserPreferences()

    private val _playbackState = MutableStateFlow(TtsPlaybackState())
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TtsService onCreate")
        val db = AppDatabase.getInstance(applicationContext)
        repository = ArticleRepository(db.articleDao(), context = applicationContext)
        userPreferencesRepository = UserPreferencesRepository(applicationContext)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()

        mediaSession = MediaSession(this, "TtsServiceMediaSession").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resumePlayback()
                override fun onPause() = pausePlayback()
                override fun onSkipToNext() = skipForward()
                override fun onSkipToPrevious() = skipBackward()
                override fun onStop() = stopPlayback()
            })
            isActive = true
        }

        var isFirstCollect = true
        scope.launch {
            userPreferencesRepository?.userPreferencesFlow?.collectLatest { prefs ->
                userPrefs = prefs
                if (isFirstCollect) {
                    isFirstCollect = false
                    currentEnginePackage = prefs.selectedTtsEngine
                    Log.d(TAG, "Initial TTS engine preference: '${currentEnginePackage.ifBlank { "System Default" }}'")
                    initializeTtsEngine(currentEnginePackage)
                } else if (prefs.selectedTtsEngine != currentEnginePackage) {
                    Log.d(TAG, "TTS Engine preference changed from '$currentEnginePackage' to '${prefs.selectedTtsEngine}'")
                    currentEnginePackage = prefs.selectedTtsEngine
                    initializeTtsEngine(currentEnginePackage)
                }
            }
        }
    }

    private fun initializeTtsEngine(enginePackage: String) {
        Log.d(TAG, "initializeTtsEngine: enginePackage='${enginePackage.ifBlank { "System Default" }}'")
        tts?.stop()
        tts?.shutdown()
        tts = null
        _playbackState.value = _playbackState.value.copy(isInitialized = false)

        val listener = this
        val instance = if (enginePackage.isNotBlank()) {
            TextToSpeech(applicationContext, listener, enginePackage)
        } else {
            TextToSpeech(applicationContext, listener)
        }
        tts = instance
        Log.d(TAG, "TextToSpeech instance created. Waiting for onInit callback...")
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "onInit callback received: status=$status (SUCCESS=${TextToSpeech.SUCCESS})")
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            Log.e(TAG, "onInit failed: status=$status, ttsInstanceIsNull=${engine == null}")
            _playbackState.value = _playbackState.value.copy(
                isInitialized = false,
                isPlaying = false,
                errorMessage = "The selected Text-to-Speech engine could not be initialized (status $status)."
            )
            pendingStart = false
            return
        }

        // Language selection with fallback
        var languageResult = engine.setLanguage(Locale.getDefault())
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Default locale '${Locale.getDefault()}' missing language data. Trying Locale.US")
            languageResult = engine.setLanguage(Locale.US)
        }
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Locale.US missing language data. Trying Locale.ENGLISH")
            languageResult = engine.setLanguage(Locale.ENGLISH)
        }

        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e(TAG, "No voice available for locale. languageResult=$languageResult")
            _playbackState.value = _playbackState.value.copy(
                isInitialized = false,
                isPlaying = false,
                errorMessage = "No speech voice supports ${Locale.getDefault().displayName}. Install a voice in Android Text-to-Speech settings."
            )
            pendingStart = false
            return
        }

        val localVoice = engine.voices
            ?.filter { it.locale.language == Locale.getDefault().language || it.locale.language == "en" }
            ?.firstOrNull { !it.isNetworkConnectionRequired }

        Log.d(TAG, "Local offline voice: '${localVoice?.name}', isOfflineAvailable=${localVoice != null}")

        if (localVoice != null) {
            try {
                engine.voice = localVoice
            } catch (e: Exception) {
                Log.w(TAG, "Could not set voice '${localVoice.name}': ${e.message}")
            }
        }

        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "UtteranceProgressListener.onStart: utteranceId=$utteranceId")
                scope.launch {
                    if (!isCurrentUtterance(utteranceId)) return@launch
                    _playbackState.value = _playbackState.value.copy(isPlaying = true)
                    updateMediaState(PlaybackState.STATE_PLAYING)
                    updateNotification()
                }
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "UtteranceProgressListener.onDone: utteranceId=$utteranceId, isUserPaused=$isUserPaused")
                scope.launch {
                    if (isUserPaused || !isCurrentUtterance(utteranceId)) return@launch
                    val finishedIndex = currentUtteranceIndex
                    currentUtteranceId = null
                    val nextIndex = finishedIndex + 1
                    when {
                        nextIndex < chunks.size -> startChunk(nextIndex)
                        _playbackState.value.isRepeatEnabled -> startChunk(0)
                        else -> finishNaturally()
                    }
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (isUserPaused || utteranceId != currentUtteranceId) return
                val chunkLength = chunks.getOrNull(currentUtteranceIndex)?.length ?: return
                latestCharOffset = (activeChunkStartOffset + end).coerceIn(0, chunkLength)
                scope.launch {
                    if (isUserPaused || !isCurrentUtterance(utteranceId)) return@launch
                    _playbackState.value = _playbackState.value.copy(
                        currentParagraphIndex = currentUtteranceIndex,
                        currentCharOffset = latestCharOffset
                    )
                }
            }

            override fun onError(utteranceId: String?) {
                Log.d(TAG, "UtteranceProgressListener.onError: utteranceId=$utteranceId, isUserPaused=$isUserPaused")
                if (isUserPaused) return
                onSpeechError(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.d(TAG, "UtteranceProgressListener.onError: utteranceId=$utteranceId, errorCode=$errorCode, isUserPaused=$isUserPaused")
                if (isUserPaused) return
                onSpeechError(utteranceId)
            }
        })

        _playbackState.value = _playbackState.value.copy(
            isInitialized = true,
            selectedEngine = currentEnginePackage,
            offlineVoiceAvailable = localVoice != null,
            errorMessage = if (localVoice == null) {
                "Speech engine initialized. Install an offline voice in Android settings for full offline support."
            } else null
        )

        Log.d(TAG, "TTS Initialization complete. isInitialized=true. pendingStart=$pendingStart")

        if (pendingStart) {
            pendingStart = false
            Log.d(TAG, "Executing pendingStart now. Starting chunk ${_playbackState.value.currentParagraphIndex}")
            startForegroundPlayback()
            requestAudioFocus()
            startChunk(_playbackState.value.currentParagraphIndex)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_SKIP_FORWARD -> skipForward()
            ACTION_SKIP_BACKWARD -> skipBackward()
            ACTION_STOP -> stopPlayback()
            else -> {
                if (_playbackState.value.isPlaying) {
                    startForegroundPlayback()
                }
            }
        }
        return START_STICKY
    }

    fun playArticle(
        articleId: String,
        title: String,
        publisher: String,
        plainText: String,
        startParagraphIndex: Int = 0,
        speed: Float = userPrefs.defaultTtsSpeed
    ) {
        Log.d(TAG, "playArticle called: articleId=$articleId, title='$title', plainTextLength=${plainText.length}, startIndex=$startParagraphIndex, speed=$speed")

        val articleChunks = splitIntoChunks(plainText)
        if (articleChunks.isEmpty()) {
            Log.w(TAG, "Article plainText is empty or blank!")
            generation++
            activeGeneration = generation
            currentUtteranceId = null
            tts?.stop()
            abandonAudioFocus()
            chunks = emptyList()
            currentArticleId = articleId
            _playbackState.value = TtsPlaybackState(
                articleId = articleId,
                articleTitle = title,
                publisher = publisher,
                isInitialized = _playbackState.value.isInitialized,
                errorMessage = "This article has no readable text to speak."
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        Log.d(TAG, "Article text split into ${articleChunks.size} chunk(s)")
        generation++
        activeGeneration = generation
        currentUtteranceId = null
        pendingStart = false
        isUserPaused = false
        currentArticleId = articleId
        chunks = articleChunks
        val initialIndex = startParagraphIndex.coerceIn(0, chunks.lastIndex)
        val previousState = _playbackState.value

        _playbackState.value = TtsPlaybackState(
            articleId = articleId,
            articleTitle = title,
            publisher = publisher,
            currentParagraphIndex = initialIndex,
            totalParagraphs = chunks.size,
            speed = speed.coerceIn(0.5f, 2.0f),
            selectedEngine = currentEnginePackage,
            isInitialized = previousState.isInitialized,
            offlineVoiceAvailable = previousState.offlineVoiceAvailable,
            errorMessage = if (previousState.isInitialized) null else previousState.errorMessage
        )

        tts?.stop()
        tts?.setSpeechRate(_playbackState.value.speed)

        if (_playbackState.value.isInitialized) {
            Log.d(TAG, "Engine is initialized. Starting foreground playback and speaking chunk $initialIndex")
            startForegroundPlayback()
            requestAudioFocus()
            startChunk(initialIndex)
        } else {
            Log.d(TAG, "Engine not initialized yet. Setting pendingStart=true. Speech will begin automatically onInit.")
            pendingStart = true
        }
    }

    private fun splitIntoChunks(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder()
        val maxLength = 2500

        text.split(Regex("\\n+")).filter { it.isNotBlank() }.forEach { paragraph ->
            val sentences = paragraph.trim().split(Regex("(?<=[.!?])\\s+"))
            sentences.forEach { sentence ->
                var remaining = sentence.trim()
                while (remaining.isNotEmpty()) {
                    val separatorLength = if (current.isEmpty()) 0 else 1
                    val available = maxLength - current.length - separatorLength
                    if (available <= 0) {
                        result += current.toString()
                        current.clear()
                        continue
                    }
                    if (remaining.length <= available) {
                        if (separatorLength > 0) current.append(' ')
                        current.append(remaining)
                        remaining = ""
                    } else {
                        var splitAt = remaining.lastIndexOf(' ', available.coerceAtMost(remaining.lastIndex))
                        if (splitAt <= 0) splitAt = available
                        if (separatorLength > 0) current.append(' ')
                        current.append(remaining.substring(0, splitAt))
                        remaining = remaining.substring(splitAt).trimStart()
                        result += current.toString()
                        current.clear()
                    }
                }
            }
            if (current.isNotEmpty()) {
                result += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result.filter { it.isNotBlank() }
    }

    private var activeGeneration: Int = 0

    private fun isCurrentUtterance(utteranceId: String?): Boolean =
        utteranceId != null && utteranceId == currentUtteranceId && !isUserPaused

    private fun startChunk(index: Int, startOffset: Int = 0) {
        if (index !in chunks.indices) {
            Log.w(TAG, "startChunk: index $index out of bounds (chunks size=${chunks.size})")
            return
        }
        isUserPaused = false
        generation++
        activeGeneration = generation
        currentUtteranceIndex = index
        val chunk = chunks[index]
        val safeStartOffset = startOffset.coerceIn(0, chunk.length)
        activeChunkStartOffset = safeStartOffset
        latestCharOffset = safeStartOffset
        val utteranceId = "utterance_${activeGeneration}_$index"
        currentUtteranceId = utteranceId

        Log.d(TAG, "startChunk: index=$index/${chunks.size}, offset=$safeStartOffset, chunkLength=${chunk.length}, utteranceId='$utteranceId'")

        _playbackState.value = _playbackState.value.copy(
            currentParagraphIndex = index,
            isPlaying = true,
            isCompleted = false,
            currentCharOffset = safeStartOffset,
            currentChunkLength = chunk.length,
            errorMessage = null
        )

        currentArticleId?.let { id ->
            scope.launch { repository?.updateTtsPosition(id, 0L, index) }
        }

        val rateResult = tts?.setSpeechRate(_playbackState.value.speed)
        if (rateResult == TextToSpeech.ERROR) {
            Log.e(TAG, "setSpeechRate returned ERROR for rate=${_playbackState.value.speed}")
            setSpeechFailure("The speech engine rejected the selected speech rate.")
            return
        }

        val textToSpeak = chunk.substring(safeStartOffset)
        val speakResult = tts?.speak(
            textToSpeak,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        Log.d(TAG, "tts?.speak() returned result code $speakResult (SUCCESS=${TextToSpeech.SUCCESS}, ERROR=${TextToSpeech.ERROR})")

        if (speakResult != TextToSpeech.SUCCESS) {
            Log.e(TAG, "tts?.speak() failed with code $speakResult")
            setSpeechFailure("The speech engine could not start reading this article (Error code $speakResult).")
            return
        }

        updateMediaState(PlaybackState.STATE_PLAYING)
        updateNotification()
    }

    fun pausePlayback(preserveFocusResume: Boolean = false) {
        Log.d(TAG, "pausePlayback called. Current chunk index=${_playbackState.value.currentParagraphIndex}, chunksSize=${chunks.size}")
        if (!_playbackState.value.isPlaying) return
        isUserPaused = true
        currentUtteranceId = null
        _playbackState.value = _playbackState.value.copy(
            currentCharOffset = latestCharOffset.coerceIn(
                0,
                chunks.getOrNull(_playbackState.value.currentParagraphIndex)?.length ?: 0
            )
        )
        generation++
        activeGeneration = generation
        pendingStart = false
        tts?.stop()
        if (!preserveFocusResume) {
            resumeOnFocusGain = false
            abandonAudioFocus()
        }
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
        updateMediaState(PlaybackState.STATE_PAUSED)
        updateNotification()
    }

    fun resumePlayback() {
        Log.d(TAG, "resumePlayback called. Chunk index=${_playbackState.value.currentParagraphIndex}, charOffset=${_playbackState.value.currentCharOffset}, chunksSize=${chunks.size}, isInitialized=${_playbackState.value.isInitialized}")
        if (_playbackState.value.isPlaying && currentUtteranceId != null) return
        if (chunks.isEmpty() || !_playbackState.value.isInitialized) {
            Log.w(TAG, "Cannot resume playback: chunks empty or engine not initialized.")
            return
        }
        isUserPaused = false
        startForegroundPlayback()
        if (!requestAudioFocus()) {
            Log.e(TAG, "Failed to acquire audio focus on resume")
            setSpeechFailure("Audio playback is unavailable because another app currently owns audio focus.")
            return
        }
        if (_playbackState.value.isCompleted) {
            startChunk(0)
        } else {
            val index = _playbackState.value.currentParagraphIndex.coerceIn(0, chunks.lastIndex)
            val offset = _playbackState.value.currentCharOffset
            if (offset >= chunks[index].length && index < chunks.lastIndex) {
                startChunk(index + 1)
            } else {
                startChunk(index, offset)
            }
        }
    }

    fun skipForward() {
        if (chunks.isEmpty()) return
        val nextIndex = (_playbackState.value.currentParagraphIndex + 1).coerceAtMost(chunks.lastIndex)
        Log.d(TAG, "skipForward: moving to chunk $nextIndex")
        moveToChunk(nextIndex)
    }

    fun skipBackward() {
        if (chunks.isEmpty()) return
        val previousIndex = (_playbackState.value.currentParagraphIndex - 1).coerceAtLeast(0)
        Log.d(TAG, "skipBackward: moving to chunk $previousIndex")
        moveToChunk(previousIndex)
    }

    private fun moveToChunk(index: Int) {
        if (_playbackState.value.isPlaying) {
            startChunk(index)
        } else {
            generation++
            activeGeneration = generation
            currentUtteranceId = null
            _playbackState.value = _playbackState.value.copy(
                currentParagraphIndex = index,
                isCompleted = false,
                currentCharOffset = 0
            )
            currentArticleId?.let { id ->
                scope.launch { repository?.updateTtsPosition(id, 0L, index) }
            }
        }
    }

    fun toggleRepeat() {
        val newRepeat = !_playbackState.value.isRepeatEnabled
        Log.d(TAG, "toggleRepeat: isRepeatEnabled=$newRepeat")
        _playbackState.value = _playbackState.value.copy(isRepeatEnabled = newRepeat)
        updateNotification()
    }

    fun setSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        Log.d(TAG, "setSpeed: speed=$safeSpeed")
        if (_playbackState.value.speed == safeSpeed) return
        val wasPlaying = _playbackState.value.isPlaying
        if (wasPlaying) {
            isUserPaused = true
            currentUtteranceId = null
            _playbackState.value = _playbackState.value.copy(currentCharOffset = latestCharOffset)
            generation++
            activeGeneration = generation
            tts?.stop()
        }
        _playbackState.value = _playbackState.value.copy(speed = safeSpeed)
        scope.launch { userPreferencesRepository?.updateTtsSpeed(safeSpeed) }
        if (wasPlaying) {
            isUserPaused = false
            startChunk(_playbackState.value.currentParagraphIndex, _playbackState.value.currentCharOffset)
        } else {
            if (tts?.setSpeechRate(safeSpeed) == TextToSpeech.ERROR) {
                _playbackState.value = _playbackState.value.copy(
                    errorMessage = "The speech engine rejected the selected speech rate."
                )
            }
        }
        updateMediaState(if (wasPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
        updateNotification()
    }

    fun stopPlayback() {
        Log.d(TAG, "stopPlayback called")
        isUserPaused = false
        generation++
        activeGeneration = generation
        currentUtteranceId = null
        pendingStart = false
        tts?.stop()
        abandonAudioFocus()
        chunks = emptyList()
        currentArticleId = null
        _playbackState.value = TtsPlaybackState(isInitialized = _playbackState.value.isInitialized)
        updateMediaState(PlaybackState.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun finishNaturally() {
        Log.d(TAG, "finishNaturally: All chunks finished speaking")
        isUserPaused = false
        generation++
        activeGeneration = generation
        currentUtteranceId = null
        abandonAudioFocus()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isCompleted = true,
            currentCharOffset = chunks.lastOrNull()?.length ?: 0
        )
        updateMediaState(PlaybackState.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun onSpeechError(utteranceId: String?) {
        if (isUserPaused) return
        scope.launch {
            if (!isCurrentUtterance(utteranceId)) return@launch
            Log.e(TAG, "onSpeechError for active utteranceId='$utteranceId'")
            setSpeechFailure("Text-to-Speech encountered an error while reading this article.")
        }
    }

    private fun setSpeechFailure(message: String) {
        if (isUserPaused) return
        Log.e(TAG, "setSpeechFailure: message='$message'")
        generation++
        activeGeneration = generation
        currentUtteranceId = null
        pendingStart = false
        tts?.stop()
        abandonAudioFocus()
        _playbackState.value = _playbackState.value.copy(isPlaying = false, errorMessage = message)
        updateMediaState(PlaybackState.STATE_ERROR)
        updateNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundPlayback() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateMediaState(state: Int) {
        val mediaState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_STOP
            )
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, _playbackState.value.speed)
            .build()
        mediaSession?.setPlaybackState(mediaState)
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: run {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "OnAudioFocusChangeListener: focusChange=$focusChange (isPlaying=${_playbackState.value.isPlaying})")
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                if (_playbackState.value.isPlaying) {
                                    resumeOnFocusGain = false
                                    pausePlayback()
                                }
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                if (_playbackState.value.isPlaying) {
                                    resumeOnFocusGain = true
                                    pausePlayback(preserveFocusResume = true)
                                }
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                if (resumeOnFocusGain) {
                                    resumeOnFocusGain = false
                                    resumePlayback()
                                }
                            }
                        }
                    }
                    .build()
                    .also { focusRequest = it }
            }
            return audioManager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        focusRequest = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Text to Speech Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Controls for background speech synthesis" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val state = _playbackState.value
        val showFullDetails = userPrefs.masterNotificationsEnabled && userPrefs.ttsNotificationEnabled

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (showFullDetails) state.articleTitle.ifBlank { "Article Audio" } else "Later Background Speech")
            .setContentText(if (showFullDetails) (state.errorMessage ?: state.publisher.ifBlank { "Later" }) else "Reading in background")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(state.isPlaying)
            .setPriority(if (showFullDetails) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setOnlyAlertOnce(true)

        if (showFullDetails) {
            builder.addAction(
                android.R.drawable.ic_media_previous,
                "Rewind",
                servicePendingIntent(ACTION_SKIP_BACKWARD, 1002)
            ).addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "Pause" else "Play",
                servicePendingIntent(if (state.isPlaying) ACTION_PAUSE else ACTION_PLAY, 1003)
            ).addAction(
                android.R.drawable.ic_media_next,
                "Forward",
                servicePendingIntent(ACTION_SKIP_FORWARD, 1004)
            ).addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                servicePendingIntent(ACTION_STOP, 1005)
            )
        }

        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val actionIntent = Intent(this, TtsService::class.java).setAction(action)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                requestCode,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                requestCode,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun updateNotification() {
        if (_playbackState.value.articleId == null) return
        if (!userPrefs.masterNotificationsEnabled || !userPrefs.ttsNotificationEnabled) {
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "TtsService onDestroy")
        generation++
        tts?.stop()
        tts?.shutdown()
        abandonAudioFocus()
        mediaSession?.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LaterTTS"
        const val CHANNEL_ID = "later_tts_channel"
        const val NOTIFICATION_ID = 1001
        private const val ACTION_PLAY = "com.example.pocketreborn.action.PLAY"
        private const val ACTION_PAUSE = "com.example.pocketreborn.action.PAUSE"
        private const val ACTION_SKIP_FORWARD = "com.example.pocketreborn.action.SKIP_FORWARD"
        private const val ACTION_SKIP_BACKWARD = "com.example.pocketreborn.action.SKIP_BACKWARD"
        private const val ACTION_STOP = "com.example.pocketreborn.action.STOP"
    }
}
