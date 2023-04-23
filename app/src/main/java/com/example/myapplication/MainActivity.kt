package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10



class MainActivity : AppCompatActivity() {

    private lateinit var TextSPL: AppCompatEditText
    private lateinit var btStart: AppCompatButton
    private lateinit var alerta: AppCompatTextView
    private lateinit var btStop: AppCompatButton
    private lateinit var btPanic: AppCompatButton

    private val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 1
    private val referencia = 2e-5

    // Defina a taxa de amostragem e o número de canais
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    var recebeDB = 0.0
    var decibel = ""
    val nivelDeAlerta = 65.00

    var dbTexto = " db"


    private val handler = Handler(Looper.getMainLooper())

    private val runnable = object: Runnable {
        override fun run() {
            TextSPL.text!!.clear()
            recebeDB = calculateSPL()
            Alerta(recebeDB)
            decibel  = String.format("%.2f", recebeDB)
            TextSPL.text?.append(decibel+dbTexto)
            handler.postDelayed(this,1)
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission()
        }
        TextSPL = findViewById(R.id.TextSPL)
        btStart = findViewById(R.id.btStart)
        alerta = findViewById(R.id.alerta)
        btStop = findViewById(R.id.btStop)


        btStart.setOnClickListener {
            handler.postDelayed(runnable,1)
        }

        btStop.setOnClickListener{
            handler.removeCallbacks(runnable)

            TextSPL.text!!.clear()


        }
    }

    private fun Alerta(dbValor: Double){
        if (dbValor > nivelDeAlerta){
            val message = "Cuidado! \n Ambiente Ruídoso"
            alerta.text = message
            alerta.visibility = View.VISIBLE

            Handler(Looper.getMainLooper()).postDelayed({alerta.visibility = View.INVISIBLE}, 5000)

        }
    }

    fun panicButton(){
        btPanic = findViewById(R.id.btPanic)
        btPanic.setOnClickListener{
            TODO("pegar dados de geolocalização")
        }

    }

    fun calculateSPL(): Double {
        // Configura a gravação de áudio

        val audioBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission()
        }
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, audioBufferSize)
        audioRecord.startRecording()


        // Captura 1 segundo de áudio
        val audioSamples = ShortArray(SAMPLE_RATE)
        var readSize = 0
        while (readSize < SAMPLE_RATE) {
            readSize += audioRecord.read(audioSamples, readSize, SAMPLE_RATE - readSize)
        }

        // Calcula o nível de pressão sonora médio (SPL)
        var rms = 0.0
        for (sample in audioSamples) {
            rms += sample * sample.toDouble()
        }
        rms = Math.sqrt(rms / audioSamples.size)
        val db = 20 * log10(rms / referencia) - 94

        // Libera recursos de gravação de áudio
        audioRecord.stop()
        audioRecord.release()

        // Retorna o valor do SPL em decibéis (dB)
        return db
    }

    fun AppCompatActivity.requestRecordAudioPermission() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Tratar exceção aqui, se necessário
        }
    }
    // Método para manipular a resposta do usuário às solicitações de permissão
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // A permissão foi concedida. Você pode iniciar a gravação de áudio aqui.
            } else {
                // A permissão foi negada. Você pode exibir uma mensagem para o usuário aqui.
            }
        }
    }
}