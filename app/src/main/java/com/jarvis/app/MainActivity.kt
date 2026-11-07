package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
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

    private val actionWords = listOf(
        "सर्च", "search", "खोज", "ढूंढ", "ढूँढ", "रास्ता",
        "बजाओ", "गाना", "play", "दिखाओ"
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
        "प्ले स्टोर" to "play store", "प्लेस्टोर" to "play store",
        "चैट जीपीटी" to "chatgpt", "चैटजीपीटी" to "chatgpt",
        "अटेंडेंस" to "attendance", "हाजिरी" to "attendance",
        "क्लॉड" to "claude", "जेमिनी" to "gemini"
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

    private fun installedLabels(): List<Pair<String, String>> {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = mutableListOf<Pair<String, String>>()
        for (ri in pm.queryIntentActivities(launcher, 0)) {
            out.add(Pair(ri.activityInfo.packageName, ri.loadLabel(pm).toString()))
        }
        return out
    }

    private fun findApp(keys: List<String>): Pair<String, String>? {
        val apps = installedLabels()
        for ((pkg, labelRaw) in apps) {
            val label = labelRaw.lowercase(Locale.getDefault()).replace(" ", "")
            for (k0 in keys) {
                val k = k0.lowercase(Locale.getDefault()).replace(" ", "")
                if (k.length < 2) continue
                if (label.contains(k) || (label.length > 2 && k.contains(label))) {
                    return Pair(pkg, labelRaw)
                }
            }
        }
        return null
    }

    private fun launchApp(pkg: String, label: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            val msg = "$label नहीं खुल पाया"
            reply.text = msg
            speak(msg)
            return
        }
        val msg = "$label खोल रहा हूँ"
        reply.text = msg
        speakPhone(msg)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

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

        val first = findApp(keys)
        if (first != null) {
            launchApp(first.first, first.second)
            return true
        }

        val gkey = prefs().getString("qkey", "") ?: ""
        if (gkey.isEmpty()) {
            val msg = "$name ऐप नहीं मिला"
            reply.text = msg
            speak(msg)
            return true
        }

        reply.text = "$name ढूंढ रहा हूँ..."
        val labels = installedLabels().map { it.second }.distinct().take(200)
        Thread {
            var picked = ""
            try {
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "system").put("content",
                    "The user spoke a Hindi voice command to open an Android app. Speech recognition often mishears app names, so the spoken text may be a wrong phonetic version of the real name. You are given the list of installed apps. Choose the ONE installed app that best matches what the user most likely meant, judging by sound and meaning. Reply with ONLY that app name copied exactly from the list. If nothing is a reasonable match, reply NONE."))
                msgs.put(JSONObject().put("role", "user").put("content",
                    "Spoken text: " + name + "\nInstalled apps: " + labels.joinToString(" | ")))
                val body = JSONObject()
                body.put("model", "openai/gpt-oss-120b")
                body.put("messages", msgs)
                body.put("max_tokens", 800)

                val conn = URL("https://api.groq.com/openai/v1/chat/completions")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $gkey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "JarvisApp/1.0")
                conn.doOutput = true
                conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
                val code = conn.responseCode
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                    picked = JSONObject(text).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message")
                        .optString("content", "").trim()
                }
            } catch (e: Exception) {
            }
            runOnUiThread {
                var found: Pair<String, String>? = null
                if (picked.isNotEmpty() && !picked.equals("NONE", ignoreCase = true)) {
                    found = installedLabels().firstOrNull { it.second.equals(picked, ignoreCase = true) }
                    if (found == null) found = findApp(listOf(picked))
                }
                if (found != null) {
                    launchApp(found.first, found.second)
                } else {
                    val msg = "$name ऐप नहीं मिला"
                    reply.text = msg + (if (picked.isNotEmpty()) " (खोजा: $picked)" else "")
                    speak(msg)
                }
            }
        }.start()
        return true
    }

    private fun looksLikeAction(said: String): Boolean {
        val s = said.lowercase(Locale.getDefault())
        return actionWords.any { s.contains(it) }
    }

    private fun startUri(uri: String, pkg: String?): Boolean {
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            if (pkg != null) i.setPackage(pkg)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun doAction(target: String, query: String): Boolean {
        if (query.isEmpty()) return false
        val q = Uri.encode(query)
        val ok: Boolean
        val msg: String
        when (target) {
            "youtube" -> {
                val u = "https://www.youtube.com/results?search_query=$q"
                ok = startUri(u, "com.google.android.youtube") || startUri(u, null)
                msg = "यूट्यूब पर $query खोज रहा हूँ"
            }
            "google", "chrome" -> {
                ok = startUri("https://www.google.com/search?q=$q", null)
                msg = "गूगल पर $query खोज रहा हूँ"
            }
            "maps" -> {
                ok = startUri("geo:0,0?q=$q", "com.google.android.apps.maps") ||
                    startUri("https://www.google.com/maps/search/?api=1&query=$q", null)
                msg = "मैप्स में $query खोज रहा हूँ"
            }
            "playstore" -> {
                ok = startUri("market://search?q=$q&c=apps", null) ||
                    startUri("https://play.google.com/store/search?q=$q", null)
                msg = "प्ले स्टोर में $query खोज रहा हूँ"
            }
            "spotify" -> {
                ok = startUri("spotify:search:$q", null) ||
                    startUri("https://open.spotify.com/search/$q", null)
                msg = "स्पॉटिफाई पर $query खोज रहा हूँ"
            }
            else -> return false
        }
        if (ok) {
            reply.text = msg
            speakPhone(msg)
        }
        return ok
    }

    private fun tryAction(said: String) {
        val gkey = prefs().getString("qkey", "") ?: ""
        if (gkey.isEmpty()) {
            if (!tryOpenApp(said)) askGroq(said)
            return
        }
        reply.text = "समझ रहा हूँ..."
        Thread {
            var target = ""
            var query = ""
            try {
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "system").put("content",
                    "You turn a Hindi voice command into a JSON action for an Android phone. Reply with ONLY JSON like {\"target\":\"youtube\",\"query\":\"TMKOC\"}. target must be one of: youtube, google, chrome, maps, playstore, spotify. Use google if the user wants to search the web without naming an app. query is the text to search or play, written the way a person would type it: use English letters for English words and brand or show names, and Devanagari for Hindi words. Fix obvious speech recognition mistakes. If the command is not a search, play or navigation request for one of these targets, reply {\"target\":\"none\",\"query\":\"\"}."))
                msgs.put(JSONObject().put("role", "user").put("content", said))
                val body = JSONObject()
                body.put("model", "openai/gpt-oss-120b")
                body.put("messages", msgs)
                body.put("max_tokens", 800)

                val conn = URL("https://api.groq.com/openai/v1/chat/completions")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $gkey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "JarvisApp/1.0")
                conn.doOutput = true
                conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
                val code = conn.responseCode
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                    val content = JSONObject(text).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message")
                        .optString("content", "")
                    val a = content.indexOf("{")
                    val b = content.lastIndexOf("}")
                    if (a >= 0 && b > a) {
                        val j = JSONObject(content.substring(a, b + 1))
                        target = j.optString("target", "").lowercase(Locale.getDefault())
                        query = j.optString("query", "").trim()
                    }
                }
            } catch (e: Exception) {
            }
            runOnUiThread {
                if (!doAction(target, query)) {
                    if (!tryOpenApp(said)) askGroq(said)
                }
            }
        }.start()
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
                    "Tum Jarvis ho, ek smart aur advanced Hindi voice assistant. Hamesha Devanagari Hindi mein jawab do. Jawab poora aur vistaar se do: kaaran, example aur zaroori details batao, lagbhag 100 se 150 shabdon mein. Koi markdown, star, hash, bullet ya emoji mat use karo, kyunki jawab bolkar sunaya jata hai. Baat-cheet jaise saaf vaakyon mein likho."))
                msgs.put(JSONObject().put("role", "user").put("content", question))
                val body = JSONObject()
                body.put("model", "openai/gpt-oss-120b")
                body.put("messages", msgs)
                body.put("max_tokens", 4000)

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
        if (looksLikeAction(said)) {
            tryAction(said)
            return
        }
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










                
