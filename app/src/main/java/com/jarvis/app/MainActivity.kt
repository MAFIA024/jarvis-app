package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var reply: TextView
    private lateinit var keyBox: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER_HORIZONTAL
        layout.setPadding(40, 100, 40, 40)

        status = TextView(this)
        status.text = "Namaste! Main Jarvis hoon."
        status.textSize = 20f

        val button = Button(this)
        button.text = "Bolo"
        button.setOnClickListener { startListening() }

        result = TextView(this)
        result.textSize = 18f
        result.setPadding(0, 40, 0, 0)

        reply = TextView(this)
        reply.textSize = 20f
        reply.setPadding(0, 40, 0, 0)

        keyBox = EditText(this)
        keyBox.hint = "Claude API key yahan paste karo"
        keyBox.isSingleLine = true

        val saveBtn = Button(this)
        saveBtn.text = "Key save karo"
        saveBtn.setOnClickListener {
            val k = keyBox.text.toString().trim()
            if (k.isNotEmpty()) {
                getSharedPreferences("jarvis", Context.MODE_PRIVATE)
                    .edit().putString("key", k).apply()
                keyBox.setText("")
                status.text = "Key save ho gayi."
            }
        }

        layout.addView(status)
        layout.addView(button)
        layout.addView(result)
        layout.addView(reply)
        layout.addView(keyBox)
        layout.addView(saveBtn)
        setContentView(layout)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun askClaude(question: String) {
        val key = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .getString("key", "") ?: ""
        if (key.isEmpty()) {
            reply.text = "Pehle neeche API key save karo."
            return
        }
        reply.text = "Jarvis soch raha hai..."
        Thread {
            var answer: String
            try {
                val body = JSONObject()
                body.put("model", "claude-sonnet-5-5")
                body.put("max_tokens", 300)
                body.put("system", "Tum Jarvis ho, ek madadgar Hindi voice assistant. Hamesha Hindi mein, 1-2 chhote vaakyon mein jawab do.")
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "user").put("content", question))
                body.put("messages", msgs)

                val conn = URL("https://api.anthropic.com/v1/messages")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("x-api-key", key)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.setRequestProperty("content-type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                answer = if (code in 200..299) {
                    JSONObject(text).getJSONArray("content")
                        .getJSONObject(0).getString("text")
                } else {
                    "Claude error ($code): $text"
                }
            } catch (e: Exception) {
                answer = "Error: ${e.message}"
            }
            runOnUiThread { reply.text = answer }
        }.start()
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            status.text = "Pehle mic permission do"
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Is phone mein voice recognition nahi hai"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Boliye, main sun raha hoon..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Samajh raha hoon..." }
            override fun onError(error: Int) { status.text = "Error aaya (code $error). Dobara Bolo dabao." }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val said = list?.firstOrNull()
                if (said == null) {
                    result.text = "Kuch nahi suna"
                } else {
                    result.text = "Aapne kaha: $said"
                    askClaude(said)
                }
                status.text = "Dobara bolne ke liye Bolo dabao."
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        recognizer?.startListening(intent)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }
}
