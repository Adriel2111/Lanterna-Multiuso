package com.example.lanternadesos

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class ReflexGameActivity : AppCompatActivity() {

    private lateinit var buttonStartGame: Button
    private lateinit var buttonMark: Button
    private lateinit var buttonReturn: Button
    private lateinit var buttonRestartGame: Button
    private lateinit var buttonShowRules: Button
    private lateinit var textViewScore: TextView
    private lateinit var dialogRules: RelativeLayout
    private lateinit var textViewRulesContent: TextView
    private lateinit var buttonCloseRules: Button
    private var score = 0
    private var consecutiveHits = 0
    private var isGameActive = false
    private val handler = Handler(Looper.getMainLooper())
    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val cameraId by lazy { cameraManager.cameraIdList.firstOrNull() ?: "" }
    private var isFlashlightOn = false
    private var flashOnTime: Long = 0
    private var missedFlashes = 0
    private val maxMissedFlashes = 5
    private lateinit var vibrator: Vibrator
    private lateinit var mediaPlayer: MediaPlayer
    private var currentFlashOnDuration = 500L
    private val minFlashOnDuration = 50L

    // Lista para armazenar os melhores tempos
    private val bestScores = mutableListOf<Long>()

    private val flashRunnable = object : Runnable {
        override fun run() {
            if (isGameActive) {
                // Duração da lanterna ligada e desligada ajustadas
                val flashOffDuration = Random.nextLong(300, 1000)

                toggleFlashlight(true)
                vibratePhone()
                buttonMark.setBackgroundColor(Color.RED)
                flashOnTime = System.currentTimeMillis()

                handler.postDelayed({
                    if (isFlashlightOn) {
                        missedFlashes++
                        if (missedFlashes >= maxMissedFlashes) {
                            endGame()
                        } else {
                            score = (score - 10).coerceAtLeast(0)
                            updateScore()
                            toggleFlashlight(false)
                            buttonMark.setBackgroundColor(Color.GRAY)
                            scheduleNextFlash()
                        }
                    }
                }, currentFlashOnDuration)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reflex_game)

        buttonStartGame = findViewById(R.id.button_start_game)
        buttonMark = findViewById(R.id.button_mark)
        buttonReturn = findViewById(R.id.button_return)
        buttonRestartGame = findViewById(R.id.button_restart_game)
        buttonShowRules = findViewById(R.id.button_show_rules)
        textViewScore = findViewById(R.id.textView_score)
        dialogRules = findViewById(R.id.dialog_rules)
        textViewRulesContent = findViewById(R.id.textView_rules_content)
        buttonCloseRules = findViewById(R.id.button_close_rules)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        mediaPlayer = MediaPlayer.create(this, R.raw.game_over)

        buttonStartGame.setOnClickListener {
            if (!isGameActive) {
                startGame()
            }
        }

        buttonRestartGame.setOnClickListener {
            resetGame()
        }

        buttonMark.setOnClickListener {
            if (isFlashlightOn) {
                score += 10
                consecutiveHits++
                updateScore()
                toggleFlashlight(false)
                buttonMark.setBackgroundColor(Color.GRAY)
                missedFlashes = 0
                adjustFlashDuration()
                scheduleNextFlash()
            } else {
                endGame()
            }
        }

        buttonReturn.setOnClickListener {
            if (!isGameActive) {
                finish()
            } else {
                Toast.makeText(this, "Não é possível voltar enquanto o jogo está ativo", Toast.LENGTH_SHORT).show()
            }
        }

        buttonShowRules.setOnClickListener {
            if (!isGameActive) {
                showRulesDialog()
            } else {
                Toast.makeText(this, "Não é possível visualizar as regras durante o jogo.", Toast.LENGTH_SHORT).show()
            }
        }

        buttonCloseRules.setOnClickListener {
            closeRulesDialog()
        }
    }

    override fun onBackPressed() {
        if (isGameActive) {
            Toast.makeText(this, "Não é possível voltar enquanto o jogo está ativo", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isGameActive) {
            Toast.makeText(this, "Não é possível voltar enquanto o jogo está ativo", Toast.LENGTH_SHORT).show()
            return true // Impede que o botão "Voltar" funcione enquanto o jogo está ativo
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startGame() {
        if (!hasCameraPermission()) {
            Toast.makeText(this, "Permissões de câmera necessárias", Toast.LENGTH_SHORT).show()
            return
        }

        isGameActive = true
        buttonStartGame.isEnabled = false
        buttonRestartGame.isEnabled = true
        buttonReturn.isEnabled = false
        buttonShowRules.isEnabled = false
        score = 0
        missedFlashes = 0
        consecutiveHits = 0
        currentFlashOnDuration = 500L
        updateScore()
        scheduleNextFlash()
    }

    private fun resetGame() {
        endGame()
        buttonStartGame.isEnabled = true
        buttonRestartGame.isEnabled = false
        buttonReturn.isEnabled = true
        buttonShowRules.isEnabled = true
    }

    private fun scheduleNextFlash() {
        val randomDelay = Random.nextLong(200, 4000) // Mantém o intervalo aleatório entre piscadas
        handler.postDelayed(flashRunnable, randomDelay)
    }

    private fun updateScore() {
        textViewScore.text = "Pontuação: $score"
    }

    private fun endGame() {
        if (!isGameActive) return

        isGameActive = false
        buttonStartGame.isEnabled = true
        buttonRestartGame.isEnabled = false
        buttonReturn.isEnabled = true
        buttonShowRules.isEnabled = true
        handler.removeCallbacks(flashRunnable)
        toggleFlashlight(false)
        buttonMark.setBackgroundColor(Color.GRAY)
        mediaPlayer.start()

        // Adiciona a pontuação final à lista de melhores tempos
        bestScores.add(score.toLong())
        // Ordena e mantém apenas os 5 melhores
        bestScores.sortDescending()
        if (bestScores.size > 5) {
            bestScores.removeAt(bestScores.size - 1)
        }

        // Exibe os 5 melhores tempos na tela
        val bestScoresText = bestScores.joinToString("\n") { "Melhor tempo: $it" }
        Toast.makeText(this, "Jogo terminado! Pontuação final: $score\n$bestScoresText", Toast.LENGTH_LONG).show()
    }

    private fun toggleFlashlight(state: Boolean) {
        if (hasCameraPermission()) {
            try {
                cameraManager.setTorchMode(cameraId, state)
                isFlashlightOn = state
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    private fun vibratePhone() {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(100)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun showRulesDialog() {
        dialogRules.visibility = View.VISIBLE
        handler.postDelayed({
            closeRulesDialog()
        }, 50000) // Fecha as regras após 50 segundos
    }

    private fun closeRulesDialog() {
        dialogRules.visibility = View.GONE
    }

    private fun adjustFlashDuration() {
        // Reduz a duração do flash a cada 5 acertos consecutivos, até o limite mínimo
        if (consecutiveHits > 0 && consecutiveHits % 5 == 0) {
            currentFlashOnDuration =
                (currentFlashOnDuration * 0.9).coerceAtLeast(minFlashOnDuration.toDouble()).toLong()
        }
    }
}
