package com.example.lanternadesos

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class InstructionsActivity : AppCompatActivity() {

    private var easterEggMediaPlayer: MediaPlayer? = null
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private lateinit var vibrator: Vibrator
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instructions)

        // Inicialização do CameraManager e Vibrator
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        val buttonBack: Button = findViewById(R.id.button_back)
        buttonBack.setOnClickListener {
            onBackPressed() // Voltar para a MainActivity
        }

        // Inicializar MediaPlayer para o Easter egg
        easterEggMediaPlayer = MediaPlayer.create(this, R.raw.easter_egg_sound)

        // Configurar botão invisível para o Easter egg
        val buttonInvisible: Button = findViewById(R.id.button_invisible)
        buttonInvisible.setOnClickListener {
            activateEasterEgg()
        }
    }

    private fun activateEasterEgg() {
        // Mostrar uma mensagem de agradecimento por um tempo maior
        showEasterEggMessage()

        // Tocar o som do Easter egg
        easterEggMediaPlayer?.start()

        // Sincronizar lanterna e vibração com o ritmo da música
        blinkFlashlightOnce()
    }

    private fun showEasterEggMessage() {
        val textView: TextView = findViewById(R.id.textview_easter_egg_message)
        textView.visibility = TextView.VISIBLE

        handler.postDelayed({
            textView.visibility = TextView.GONE
        }, 50000)
    }

    private fun blinkFlashlightOnce() {
        if (cameraId == null) return

        try {
            // Ligando a lanterna
            cameraManager.setTorchMode(cameraId!!, true)
            // Vibrar o telefone
            vibratePhone(500) // Duração da vibração em milissegundos

            // Desligar a lanterna após 200 ms
            handler.postDelayed({
                cameraManager.setTorchMode(cameraId!!, false)
            }, 500)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun vibratePhone(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar recursos do MediaPlayer
        easterEggMediaPlayer?.release()
        easterEggMediaPlayer = null
    }

    override fun onBackPressed() {
        super.onBackPressed() // Voltar para a MainActivity
    }
}
