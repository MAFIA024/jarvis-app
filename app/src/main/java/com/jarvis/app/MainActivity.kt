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
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity() {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
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
        keyBox.hint = "Gemini API key yahan paste karo"
        keyBox.isSingleLine = true

        val saveBtn = Button(this)
        saveBtn.text = "Key save karo"
        saveBtn.setOnClickListener {
            val k = keyBox.text.toString().trim()
            if (k.isNotEmpty()) {
                getSharedPreferences("jarvis", Context.MODE_PRIVATE)
                    .edit().putString("gkey", k).apply()
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

        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("hi", "IN"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA &&
                    r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun askGemini(question: String) {
        val key = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .getString("gkey", "") ?: ""
        if (key.isEmpty()) {
            reply.text = "Pehle neeche Gemini key save karo."
            return
        }
        reply.text = "Jarvis soch raha hai..."
        Thread {
            var answer: String
            var ok = false
            try {
                val sys = JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text",
                        "Tum Jarvis ho, ek madadgar Hindi voice assistant. Hamesha Devanagari Hindi mein, 1-2 chhote vaakyon mein jawab do. Koi symbol ya emoji mat use karo.")))
                val content = JSONObject().put("role", "user").put("parts",
                    JSONArray().put(JSONObject().put("text", question)))
                val body = JSONObject()
                body.put("system_instruction", sys)
                body.put("contents", JSONArray().put(content))

                val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("x-goog-api-key", key)
                conn.setRequestProperty("content-type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                if (code in 200..299) {
                    val parts = JSONObject(text).getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) {
                        sb.append(parts.getJSONObject(i).optString("text", ""))
                    }
                    answer = sb.toString().trim()
                    ok = answer.isNotEmpty()
                    if (!ok) answer = "Jawab khaali aaya."
                } else {
                    answer = "Gemini error ($code): $text"
                }
            } catch (e: Exception) {
                answer = "Error: ${e.message}"
            }
            runOnUiThread {
                reply.text = answer
                if (ok) {
                    if (ttsReady) {
                        tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
                    } else {
                        status.text = "Hindi voice phone mein install nahi hai"
                    }
                }
            }
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
        tts?.stop()
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
                    askGemini(said)
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
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
