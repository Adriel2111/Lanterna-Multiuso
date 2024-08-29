package com.example.lanternadesos

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isFlashlightOn: Boolean = false
    private lateinit var vibrator: Vibrator
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var flashMediaPlayer: MediaPlayer? = null
    private var sosMediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isFlashing: Boolean = false
    private var isEmergencyMode: Boolean = false
    private var isNormalFlashing: Boolean = false
    private var flashInterval: Long = 500
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicialização de componentes
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Inicializar MediaPlayer
        flashMediaPlayer = MediaPlayer.create(this, R.raw.flash_sound)
        sosMediaPlayer = MediaPlayer.create(this, R.raw.sos_sound)

        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Erro na câmera: ${e.message}", Toast.LENGTH_LONG).show()
        }

        val buttonToggle: Button = findViewById(R.id.button_toggle)
        buttonToggle.setOnClickListener {
            stopAll()
        }

        val buttonEmergency: Button = findViewById(R.id.button_emergency)
        buttonEmergency.setOnClickListener {
            if (!isEmergencyMode) {
                startSOS()
            }
        }

        val seekBarFrequency: SeekBar = findViewById(R.id.seekBar_frequency)
        seekBarFrequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                flashInterval = progress.toLong()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Erro no reconhecimento de voz", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                results?.let {
                    val matches = it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.let { resultList ->
                        val command = resultList[0].toLowerCase()
                        when {
                            command.contains("ativar sos") -> startSOS()
                            command.contains("parar sos") -> stopSOS()
                            else -> Toast.makeText(this@MainActivity, "Comando não reconhecido", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val buttonVoiceControl: Button = findViewById(R.id.button_voice_control)
        buttonVoiceControl.setOnClickListener {
            startVoiceRecognition()
        }

        val buttonInstructions: Button = findViewById(R.id.button_instructions)
        buttonInstructions.setOnClickListener {
            val intent = Intent(this, InstructionsActivity::class.java)
            startActivity(intent)
        }

        // Botão para iniciar o Desafio de Reflexo
        val buttonStartReflexGame: Button = findViewById(R.id.button_start_reflex_game)
        buttonStartReflexGame.setOnClickListener {
            val intent = Intent(this, ReflexGameActivity::class.java)
            startActivity(intent)
        }

        // Solicitar permissões
        requestPermissions()
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.VIBRATE), 1)
        } else {
            // Permissões já concedidas, inicialize o que for necessário aqui
            setupComponents()
        }
    }

    private fun setupComponents() {
        // Aqui você pode inicializar componentes que dependem de permissões
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions()) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopAll()  // Certifique-se de parar todas as funções ao pausar
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH

                if (acceleration > 12) {
                    if (!isEmergencyMode && !isNormalFlashing && hasPermissions()) {
                        startNormalFlashing()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED
    }

    private fun toggleFlashlight(state: Boolean) {
        if (!hasPermissions() || cameraId == null) return
        try {
            cameraManager.setTorchMode(cameraId!!, state)
            isFlashlightOn = state
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao alternar a lanterna: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro inesperado: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibratePhone(duration: Long) {
        if (!hasPermissions()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun playSoundOnce(soundResId: Int) {
        stopMediaPlayers() // Para qualquer som tocando antes de iniciar o novo
        val player = MediaPlayer.create(this, soundResId)
        player.setOnCompletionListener {
            it.release()
        }
        player.start()
        when (soundResId) {
            R.raw.flash_sound -> flashMediaPlayer = player
            R.raw.sos_sound -> sosMediaPlayer = player
        }
    }

    private fun stopMediaPlayers() {
        // Para e libera todos os MediaPlayers
        try {
            flashMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                    it.release()
                }
                flashMediaPlayer = null
            }
            sosMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                    it.release()
                }
                sosMediaPlayer = null
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao parar o som: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro inesperado ao parar o som: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startNormalFlashing() {
        if (!hasPermissions() || isEmergencyMode || isNormalFlashing) return

        isNormalFlashing = true
        handler.post(normalFlashRunnable)
        vibratePhone(100)
        playSoundOnce(R.raw.flash_sound)
    }

    private fun stopNormalFlashing() {
        if (!isNormalFlashing) return

        isNormalFlashing = false
        handler.removeCallbacks(normalFlashRunnable)
        if (isFlashlightOn) {
            toggleFlashlight(false)
        }
        vibratePhone(100)
        stopMediaPlayers() // Para todos os sons
    }

    private fun startSOS() {
        if (!hasPermissions() || isNormalFlashing || isEmergencyMode) return

        isEmergencyMode = true
        isFlashing = true
        handler.post(sosRunnable)
        vibratePhone(100)
        playSoundOnce(R.raw.sos_sound)
    }

    private fun stopSOS() {
        if (!isEmergencyMode) return

        isEmergencyMode = false
        isFlashing = false
        handler.removeCallbacks(sosRunnable)
        if (isFlashlightOn) {
            toggleFlashlight(false)
        }
        vibratePhone(100)
        stopMediaPlayers() // Para todos os sons
    }

    private fun stopAll() {
        stopNormalFlashing()
        stopSOS()
        if (isFlashlightOn) {
            toggleFlashlight(false)
        }
    }

    private val normalFlashRunnable = object : Runnable {
        override fun run() {
            if (isNormalFlashing && !isEmergencyMode) {
                toggleFlashlight(!isFlashlightOn)
                vibratePhone(100)
                handler.postDelayed(this, flashInterval)
            }
        }
    }

    private val sosRunnable = object : Runnable {
        override fun run() {
            if (isEmergencyMode) {
                val sosPattern = arrayOf(300L, 100L, 300L, 100L, 300L, 1000L, 300L, 100L, 300L, 100L, 300L, 1000L)
                var currentIndex = 0

                handler.post(object : Runnable {
                    override fun run() {
                        if (isEmergencyMode) {
                            toggleFlashlight(currentIndex % 2 == 0)
                            vibratePhone(100)
                            handler.postDelayed(this, sosPattern[currentIndex])
                            currentIndex++
                            if (currentIndex >= sosPattern.size) {
                                currentIndex = 0
                            }
                        }
                    }
                })
            }
        }
    }

    private fun startVoiceRecognition() {
        if (!hasPermissions()) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale algo...")
        }
        speechRecognizer.startListening(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                setupComponents()  // Inicializar componentes após permissões
            } else {
                Toast.makeText(this, "Permissões necessárias não concedidas", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
