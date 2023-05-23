package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.messaging.FirebaseMessaging


class MainActivity : AppCompatActivity() {

    private lateinit var TextSPL: AppCompatEditText
    private lateinit var eTLocal: AppCompatTextView
    private lateinit var btStart: AppCompatButton
    private lateinit var alerta: AppCompatTextView
    private lateinit var btStop: AppCompatButton
    private lateinit var btPanic: AppCompatButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val REQUEST_CODE_LOCATION = 1


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
    ///private val requestPermissionLauncher = registerForActivityResult()
    val IntegrasData = Firebase.firestore //Instancia do firebase

    private val dbTempo = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object: Runnable {
        override fun run() {
            TextSPL.text!!.clear()

            ///Se valor for maior que o nivel estabelecido, grava no banco o ruido excessivo
            if(recebeDB > nivelDeAlerta){
                bdRuido()
            }

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) //gps
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission()
        }
        TextSPL = findViewById(R.id.TextSPL)
        eTLocal = findViewById(R.id.eTLocal)
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
        btPanic = findViewById(R.id.btPanic)                    ///PANICO
        panico()            ///modificado, função criada abaixo e modificada para gravar dados no firebase toda vez que for chamada
    }

    private fun Alerta(dbValor: Double){
        if (dbValor > nivelDeAlerta){
            val message = "Cuidado! \n Ambiente Ruídoso"
            alerta.text = message
            alerta.visibility = View.VISIBLE

            Handler(Looper.getMainLooper()).postDelayed({alerta.visibility = View.INVISIBLE}, 5000)

        }
    }

    fun pushMessage(){
        FirebaseMessaging.getInstance().token.addOnSuccessListener { OnCompleteListener{task ->
            if (!task.isSuccessful){
                Log.w(TAG, "Não foi possivel pegar o token do dispositivo", task.exception)
                return@OnCompleteListener
            }
            val token = task.result

            val msg = getString(R.string.msg_token_fmt, token)
            Log.d(TAG, msg)
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        } }
    }

    fun panico(){
        btPanic.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        val latLongString =
                            "Latitude: ${location?.latitude}\nLongitude: ${location?.longitude}"
                        eTLocal.text = latLongString
                        Log.d("Localização", latLongString)

                        val db = FirebaseFirestore.getInstance()
                        val data = hashMapOf(
                            "latitude" to location?.latitude,
                            "longitude" to location?.longitude,
                            "time" to FieldValue.serverTimestamp()
                        )
                        db.collection("IntegrasPanico")
                            .add(data)
                            .addOnSuccessListener{ documentReference ->
                                Log.d("FirebaseFirestore", "DocumentSnapshot added with ID: ${documentReference.id}")
                            }
                            .addOnFailureListener{e ->
                                Log.w("FirebaseFirestore","Error adding document", e)
                            }
                    }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1
                )
            }
        }
    }

/*
    val data = hashMapOf(
        "Data" to FieldValue.serverTimestamp(),
        "SPL" to recebeDB


    )
    IntegrasData.collection("IntegrasData")
    .add(data)
    .addOnSuccessListener { documentReference ->
        Log.d(TAG, "Documento add com sucesso ${documentReference.id}")
    }
    .addOnFailureListener{ e ->
        Log.w(TAG, "#######Erro #######", e)
    }

*/

    fun bdRuido() {
        // Obtenha a instância do provedor de localização
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        val db = FirebaseFirestore.getInstance()


        // Verifique se a permissão de localização foi concedida
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Obtenha a última localização conhecida
            fusedLocationProviderClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    // Verifique se a localização é válida
                    if (location != null) {
                        // Obtenha as coordenadas de latitude e longitude
                        val latitude = location.latitude
                        val longitude = location.longitude

                        // Crie o mapa de dados com todas as informações
                        val data = hashMapOf(
                            "Data" to FieldValue.serverTimestamp(),
                            "SPL" to recebeDB,
                            "Latitude" to latitude,
                            "Longitude" to longitude
                        )

                        // Adicione os dados ao Firestore
                        val collectionRef = db.collection("IntegrasData")
                        collectionRef.add(data)
                            .addOnSuccessListener { documentReference ->
                                Log.d(TAG, "Documento adicionado com sucesso: ${documentReference.id}")
                            }
                            .addOnFailureListener { exception ->
                                Log.w(TAG, "Erro ao adicionar documento", exception)
                            }
                    } else {
                        Log.d(TAG, "Localização não encontrada")
                    }
                }
        } else {
            // Permissão de localização não concedida, solicite-a ao usuário
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_LOCATION)
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