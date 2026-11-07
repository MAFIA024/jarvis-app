package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
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
    private lateinit var chatBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var settingsScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var micBtn: TextView
    private var pending: TextView? = null

    private val cBg = Color.parseColor("#0A0E1A")
    private val cPanel = Color.parseColor("#121829")
    private val cStroke = Color.parseColor("#22304D")
    private val cCyan = Color.parseColor("#00E5FF")
    private val cCyanDark = Color.parseColor("#00ACC1")
    private val cBlue = Color.parseColor("#1565C0")
    private val cPurple = Color.parseColor("#B388FF")
    private val cText = Color.parseColor("#E6EDF7")
    private val cMuted = Color.parseColor("#6B7A99")

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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) g.setStroke(dp(1), stroke)
        return g
    }

    private fun circle(c1: Int, c2: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
        g.shape = GradientDrawable.OVAL
        return g
    }

    private fun gradBtn(): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cBlue, cCyanDark))
        g.cornerRadius = dp(12).toFloat()
        return g
    }

    private fun scrollDown() {
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBubble(text: String, user: Boolean): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setLineSpacing(0f, 1.15f)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        val r = dp(18).toFloat()
        val s = dp(4).toFloat()
        if (user) {
            val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cBlue, cCyanDark))
            g.cornerRadii = floatArrayOf(r, r, r, r, s, s, r, r)
            tv.background = g
            tv.setTextColor(Color.WHITE)
        } else {
            val g = GradientDrawable()
            g.setColor(cPanel)
            g.setStroke(dp(1), cStroke)
            g.cornerRadii = floatArrayOf(r, r, r, r, r, r, s, s)
            tv.background = g
            tv.setTextColor(cText)
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = if (user) Gravity.END else Gravity.START
        lp.setMargins(0, dp(6), 0, dp(6))
        tv.alpha = 0f
        chatBox.addView(tv, lp)
        tv.animate().alpha(1f).setDuration(250).start()
        scrollDown()
        return tv
    }

    private fun addUser(text: String) {
        pending = null
        addBubble(text, true)
    }

    private fun showThinking(text: String) {
        val p = pending
        if (p == null) {
            pending = addBubble(text, false)
        } else {
            p.text = text
            scrollDown()
        }
    }

    private fun setReply(text: String) {
        val p = pending
        if (p != null) {
            p.text = text
            pending = null
            scrollDown()
        } else {
            addBubble(text, false)
        }
    }

    private fun setMic(on: Boolean) {
        micBtn.background = if (on)
            circle(Color.parseColor("#FF1744"), Color.parseColor("#FF4081"))
        else
            circle(cBlue, cCyan)
        val sc = if (on) 1.15f else 1f
        micBtn.animate().scaleX(sc).scaleY(sc).setDuration(200).start()
    }

    private fun addKeyRow(layout: LinearLayout, hint: String, prefName: String, label: String) {
        val box = EditText(this)
        box.hint = hint
        box.setSingleLine(true)
        box.setTextColor(cText)
        box.setHintTextColor(cMuted)
        box.background = rounded(cPanel, 12, cStroke)
        box.setPadding(dp(14), dp(12), dp(14), dp(12))

        val btn = TextView(this)
        btn.text = label
        btn.gravity = Gravity.CENTER
        btn.textSize = 15f
        btn.setTextColor(Color.WHITE)
        btn.background = gradBtn()
        btn.setPadding(dp(12), dp(12), dp(12), dp(12))
        btn.setOnClickListener {
            val k = box.text.toString().trim()
            if (k.isNotEmpty()) {
                prefs().edit().putString(prefName, k).apply()
                box.setText("")
                status.text = "● Save हो गया ✓"
            }
        }

        val lp1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp1.setMargins(0, dp(14), 0, 0)
        val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp2.setMargins(0, dp(8), 0, 0)
        layout.addView(box, lp1)
        layout.addView(btn, lp2)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBar?.hide()
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val match = LinearLayout.LayoutParams.MATCH_PARENT
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(cBg)

        // Header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(18), dp(14), dp(12), dp(10))

        val titleCol = LinearLayout(this)
        titleCol.orientation = LinearLayout.VERTICAL

        val title = TextView(this)
        title.text = "JARVIS"
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        title.letterSpacing = 0.25f
        title.setTextColor(cCyan)
        title.setShadowLayer(16f, 0f, 0f, cCyan)

        status = TextView(this)
        status.text = "● तैयार"
        status.textSize = 12f
        status.setTextColor(cMuted)

        titleCol.addView(title)
        titleCol.addView(status)

        val gear = TextView(this)
        gear.text = "⚙"
        gear.textSize = 24f
        gear.setTextColor(cMuted)
        gear.setPadding(dp(12), dp(6), dp(12), dp(6))

        header.addView(titleCol, LinearLayout.LayoutParams(0, wrap, 1f))
        header.addView(gear, LinearLayout.LayoutParams(wrap, wrap))

        val line = View(this)
        val lg = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cCyan, cPurple))
        line.background = lg
        line.alpha = 0.7f

        // Chat area
        chatBox = LinearLayout(this)
        chatBox.orientation = LinearLayout.VERTICAL
        chatBox.setPadding(dp(14), dp(10), dp(14), dp(10))
        chatScroll = ScrollView(this)
        chatScroll.isVerticalScrollBarEnabled = false
        chatScroll.addView(chatBox)

        // Settings area
        val settingsBox = LinearLayout(this)
        settingsBox.orientation = LinearLayout.VERTICAL
        settingsBox.setPadding(dp(18), dp(16), dp(18), dp(24))
        val sTitle = TextView(this)
        sTitle.text = "⚙ सेटिंग्स"
        sTitle.textSize = 20f
        sTitle.setTextColor(cCyan)
        val sInfo = TextView(this)
        sInfo.text = "Keys सिर्फ आपके फोन में सेव होती हैं। सेव करने के बाद ⚙ फिर दबाकर चैट पर लौटें।"
        sInfo.textSize = 13f
        sInfo.setTextColor(cMuted)
        sInfo.setPadding(0, dp(6), 0, 0)
        settingsBox.addView(sTitle)
        settingsBox.addView(sInfo)
        addKeyRow(settingsBox, "Groq key यहाँ paste करो", "qkey", "Groq key save karo")
        addKeyRow(settingsBox, "ElevenLabs key यहाँ paste करो", "ekey", "ElevenLabs key save karo")
        addKeyRow(settingsBox, "ElevenLabs Voice ID यहाँ paste करो", "vid", "Voice ID save karo")
        settingsScroll = ScrollView(this)
        settingsScroll.addView(settingsBox)
        settingsScroll.visibility = View.GONE

        gear.setOnClickListener {
            val show = settingsScroll.visibility != View.VISIBLE
            settingsScroll.visibility = if (show) View.VISIBLE else View.GONE
            chatScroll.visibility = if (show) View.GONE else View.VISIBLE
        }

        // Bottom bar
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), dp(8), dp(12), dp(12))

        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = rounded(cPanel, 26, cStroke)
        pill.setPadding(dp(16), 0, dp(6), 0)

        input = EditText(this)
        input.hint = "कुछ पूछिए या कहिए..."
        input.setHintTextColor(cMuted)
        input.setTextColor(cText)
        input.textSize = 16f
        input.background = null
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, _, _ ->
            sendTyped()
            true
        }

        val sendBtn = TextView(this)
        sendBtn.text = "➤"
        sendBtn.textSize = 20f
        sendBtn.gravity = Gravity.CENTER
        sendBtn.setTextColor(cCyan)
        sendBtn.setOnClickListener { sendTyped() }

        pill.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        pill.addView(sendBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

        micBtn = TextView(this)
        micBtn.text = "🎤"
        micBtn.textSize = 22f
        micBtn.gravity = Gravity.CENTER
        micBtn.background = circle(cBlue, cCyan)
        micBtn.setOnClickListener { startListening() }

        bar.addView(pill, LinearLayout.LayoutParams(0, wrap, 1f))
        val mlp = LinearLayout.LayoutParams(dp(52), dp(52))
        mlp.setMargins(dp(10), 0, 0, 0)
        bar.addView(micBtn, mlp)

        root.addView(header, LinearLayout.LayoutParams(match, wrap))
        root.addView(line, LinearLayout.LayoutParams(match, dp(2)))
        root.addView(chatScroll, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(settingsScroll, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(bar, LinearLayout.LayoutParams(match, wrap))
        setContentView(root)

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

        addBubble("नमस्ते! मैं जार्विस हूँ। 🎤 दबाकर बोलिए या नीचे टाइप कीजिए।", false)
        val gk = prefs().getString("qkey", "") ?: ""
        if (gk.isEmpty()) {
            addBubble("सबसे पहले ऊपर ⚙ दबाकर अपनी Groq key सेव कीजिए।", false)
        }
    }

    private fun sendTyped() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        tts?.stop()
        try { player?.stop() } catch (e: Exception) {}
        addUser(t)
        handleCommand(t)
    }

    private fun speakPhone(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        } else {
            status.text = "● Hindi voice फोन में install नहीं है"
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
                        status.text = "● Play error: ${e.message}"
                        speakPhone(text)
                    }
                } else {
                    status.text = "● " + err
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
            setReply(msg)
            speak(msg)
            return
        }
        val msg = "$label खोल रहा हूँ"
        setReply(msg)
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
            setReply(msg)
            speak(msg)
            return true
        }

        showThinking("$name ढूंढ रहा हूँ...")
        val labels = installedLabels().map { it.second }.distinct().take(200)
        Thread {
            var picked = ""
            try {
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "system").put("content",
                    "The user spoke a Hindi voice command to open an Android app. Speech recognition often mishears app names, so the spoken text may be a wrong phonetic version of the real name. You are given the list of installed apps. Choose the ONE installed app that best matches what the user most likely meant, judging by sound and meaning. Reply with ONLY that app name copied exactly from the list. If nothing is a reasonable match, reply NONE."))
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
                    setReply(msg + (if (picked.isNotEmpty()) " (खोजा: $picked)" else ""))
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
            setReply(msg)
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
        showThinking("समझ रहा हूँ...")
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
            setReply("पहले ऊपर ⚙ दबाकर Groq key सेव कीजिए।")
            return
        }
        showThinking("जार्विस सोच रहा है...")
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
                    if (!ok) answer = "जवाब खाली आया।"
                } else {
                    answer = "Groq error ($code): $text"
                }
            } catch (e: Exception) {
                answer = "Error: ${e.message}"
            }
            runOnUiThread {
                setReply(answer)
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
            status.text = "● पहले mic permission दें"
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "● इस फोन में voice recognition नहीं है"
            return
        }
        tts?.stop()
        try { player?.stop() } catch (e: Exception) {}
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "● सुन रहा हूँ..."
                setMic(true)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                status.text = "● समझ रहा हूँ..."
                setMic(false)
            }
            override fun onError(error: Int) {
                setMic(false)
                status.text = "● Error (code $error). फिर से 🎤 दबाइए"
            }
            override fun onResults(results: Bundle?) {
                setMic(false)
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val said = list?.firstOrNull()
                if (said == null) {
                    status.text = "● कुछ नहीं सुना"
                } else {
                    status.text = "● तैयार"
                    addUser(said)
                    handleCommand(said)
                }
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
    
