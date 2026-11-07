package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity() {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var reply: TextView

    private val openWords = listOf(
        "खोलो", "खोलिए", "खोल दो", "खोल", "चालू करो", "चलाओ",
        "kholo", "open", "start"
    )

    private val aliases = listOf(
        "यूट्यूब" to "youtube", "यू ट्यूब" to "youtube",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp",
        "वॉट्सऐप" to "whatsapp", "वाट्सएप" to "whatsapp", "वाट्सऐप" to "whatsapp",
        "कैमरा" to "camera", "क्रोम" to "chrome",
        "सेटिंग्स" to "settings", "सेटिंग" to "settings",
        "गैलरी" to "gallery", "फोटो" to "photos", "गूगल" to "google",
        "मैप्स" to "maps", "मैप" to "maps",
        "इंस्टाग्राम" to "instagram", "फेसबुक" to "facebook",
        "कैलकुलेटर" to "calculator", "घड़ी" to "clock",
        "फोन" to "phone", "मैसेज" to "messages", "जीमेल" to "gmail",
        "टेलीग्राम" to "telegram", "स्पॉटिफाई" to "spotify",
        "प्ले स्टोर" to "play store", "प्लेस्टोर" to "play store"
    )

    private fun prefs() = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    private fun addKeyRow(layout: LinearLayout, hint: String, prefName: String, label: String) {
        val box = EditText(this)
        box.hint = hint
        box.isSingleLine = true
        val btn = Button(this)
        btn.text = label
        btn.setOnClickListener {
            val k = box.text.toString().trim()
            if (k.isNotEmpty()) {
                prefs().edit().putString(prefName, k).apply()
                box.setText("")
                status.text = "Save ho gaya."
            }
        }
        layout.addView(box)
        layout.addView(btn)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER_HORIZONTAL
        layout.setPadding(40, 100, 40, 80)

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
        reply.setPadding(0, 40, 0, 40)

        layout.addView(status)
        layout.addView(button)
        layout.addView(result)
        layout.addView(reply)

        addKeyRow(layout, "Groq key yahan paste karo", "qkey", "Groq key save karo")
        addKeyRow(layout, "ElevenLabs key yahan paste karo", "ekey", "ElevenLabs key save karo")
        addKeyRow(layout, "ElevenLabs Voice ID yahan paste karo", "vid", "Voice ID save karo")

        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)

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

    private fun speakPhone(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        } else {
            status.text = "Hindi voice phone mein install nahi hai"
        }
    }

    private fun speak(text: String) {
        val ekey = prefs().getString("ekey", "") ?: ""
        val vid = prefs().getString("vid", "") ?: ""
        if (ekey.isEmpty() || vid.isEmpty()) {
            speakPhone(text)
            return
        }
        Thread {
            var file: File? = null
            var err = ""
            try {
                val body = JSONObject()
                body.put("text", text)
                body.put("model_id", "eleven_multilingual_v2")

                val conn = URL("https://api.elevenlabs.io/v1/text-to-speech/$vid?output_format=mp3_44100_128")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("xi-api-key", ekey)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "audio/mpeg")
                conn.doOutput = true
                conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))

                val code = conn.responseCode
                if (code in 200..299) {
                    val f = File(cacheDir, "jarvis.mp3")
                    conn.inputStream.use { input ->
                        f.outputStream().use { out -> input.copyTo(out) }
                    }
                    file = f
                } else {
                    val t = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                    err = "ElevenLabs error ($code): " + t.take(200)
                }
            } catch (e: Exception) {
                err = "ElevenLabs error: ${e.message}"
            }
            val f2 = file
            runOnUiThread {
                if (f2 != null) {
                    try {
                        player?.release()
                        val mp = MediaPlayer()
                        mp.setDataSource(f2.absolutePath)
                        mp.prepare()
                        mp.start()
                        player = mp
                    } catch (e: Exception) {
                        status.text = "Play error: ${e.message}"
                        speakPhone(text)
                    }
                } else {
                    status.text = err
                    speakPhone(text)
                }
            }
        }.start()
    }

    // Agar command "kholo" wali hai to true return karta hai (kaam yahin ho jata hai)
    private fun tryOpenApp(said: String): Boolean {
        val s = said.lowercase(Locale.getDefault())
        if (openWords.none { s.contains(it) }) return false

        var name = s
        for (w in openWords) name = name.replace(w, " ")
        for (w in listOf("जार्विस", "जारविस", "जर्विस", "जार्वी", "jarvis", "please", "कृपया", "ऐप", "एप", "app")) {
            name = name.replace(w, " ")
        }
        name = name.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return false

        val keys = mutableListOf(name)
        for ((hi, en) in aliases) {
            if (name.contains(hi)) keys.add(en)
        }

        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        var foundPkg: String? = null
        var foundLabel = ""
        loop@ for (ri in apps) {
            val label = ri.loadLabel(pm).toString().lowercase(Locale.getDefault())
            for (k in keys) {
                if (label.contains(k) || (label.length > 2 && k.contains(label))) {
                    foundPkg = ri.activityInfo.packageName
                    foundLabel = ri.loadLabel(pm).toString()
                    break@loop
                }
            }
        }

        if (foundPkg == null) {
            val msg = "$name ऐप नहीं मिला"
            reply.text = msg
            speak(msg)
            return true
        }

        val launch = pm.getLaunchIntentForPackage(foundPkg)
        if (launch == null) {
            val msg = "$foundLabel नहीं खुल पाया"
            reply.text = msg
            speak(msg)
            return true
        }
        val msg = "$foundLabel खोल रहा हूँ"
        reply.text = msg
        speakPhone(msg)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        return true
    }

    private fun askGroq(question: String) {
        val key = prefs().getString("qkey", "") ?: ""
        if (key.isEmpty()) {
            reply.text = "Pehle neeche Groq key save karo."
            return
        }
        reply.text = "Jarvis soch raha hai..."
        Thread {
            var answer: String
            var ok = false
            try {
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "system").put("content",
                    "Tum Jarvis ho, ek madadgar Hindi voice assistant. Hamesha Devanagari Hindi mein, 1-2 chhote vaakyon mein jawab do. Koi symbol ya emoji mat use karo."))
                msgs.put(JSONObject().put("role", "user").put("content", question))
                val body = JSONObject()
                body.put("model", "openai/gpt-oss-120b")
                body.put("messages", msgs)
                body.put("max_tokens", 1500)

                val conn = URL("https://api.groq.com/openai/v1/chat/completions")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "JarvisApp/1.0")
                conn.doOutput = true
                conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                if (code in 200..299) {
                    answer = JSONObject(text).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message")
                        .optString("content", "").trim()
                    ok = answer.isNotEmpty()
                    if (!ok) answer = "Jawab khaali aaya."
                } else {
                    answer = "Groq error ($code): $text"
                }
            } catch (e: Exception) {
                answer = "Error: ${e.message}"
            }
            runOnUiThread {
                reply.text = answer
                if (ok) speak(answer)
            }
        }.start()
    }

    private fun handleCommand(said: String) {
        if (tryOpenApp(said)) return
        askGroq(said)
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
        try { player?.stop() } catch (e: Exception) {}
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
                    handleCommand(said)
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
        player?.release()
        super.onDestroy()
    }
}
