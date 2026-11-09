package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null

    private lateinit var homeLayer: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var chatBox: LinearLayout
    private lateinit var voiceLayer: LinearLayout
    private lateinit var settingsScroll: ScrollView
    private lateinit var typedBar: LinearLayout
    private lateinit var voiceBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var orb: OrbView
    private lateinit var voiceStatus: TextView
    private lateinit var caption: TextView
    private lateinit var input: EditText
    private lateinit var micIn: TextView
    private lateinit var wave: WaveIcon
    private lateinit var arrow: TextView
    private lateinit var muteBtn: TextView

    private var pending: TextView? = null
    private var screen = 0
    private var voiceMode = false
    private var muted = false
    private var dictate = false
    private var listening = false
    private var speaking = false
    private var errStreak = 0

    private val match = LinearLayout.LayoutParams.MATCH_PARENT
    private val wrap = LinearLayout.LayoutParams.WRAP_CONTENT

    private val cBg = Color.BLACK
    private val cText = Color.WHITE
    private val cMuted = Color.parseColor("#9A9AA0")
    private val cPill = Color.parseColor("#2B2B2E")
    private val cBtn = Color.parseColor("#232326")
    private val cBubble = Color.parseColor("#303033")
    private val cBlue = Color.parseColor("#3D6BF2")

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

    // ---------- helpers ----------

    private fun prefs() = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(m: String) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    }

    private fun hasMic(): Boolean {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun shape(color: Int, oval: Boolean, radiusDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        if (oval) g.shape = GradientDrawable.OVAL else g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun iconBtn(glyph: String, bg: Int?, fg: Int, sizeDp: Int, sp: Float): TextView {
        val t = TextView(this)
        t.text = glyph
        t.textSize = sp
        t.gravity = Gravity.CENTER
        t.setTextColor(fg)
        if (bg != null) t.background = shape(bg, true)
        t.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return t
    }

    private fun addKeyRow(layout: LinearLayout, hint: String, prefName: String, label: String) {
        val box = EditText(this)
        box.hint = hint
        box.setSingleLine(true)
        box.setTextColor(cText)
        box.setHintTextColor(cMuted)
        box.background = shape(cPill, false, 14)
        box.setPadding(dp(16), dp(14), dp(16), dp(14))

        val btn = TextView(this)
        btn.text = label
        btn.gravity = Gravity.CENTER
        btn.textSize = 15f
        btn.setTextColor(cText)
        btn.background = shape(cBlue, false, 14)
        btn.setPadding(dp(12), dp(13), dp(12), dp(13))
        btn.setOnClickListener {
            val k = box.text.toString().trim()
            if (k.isNotEmpty()) {
                prefs().edit().putString(prefName, k).apply()
                box.setText("")
                toast("Save हो गया ✓")
            }
        }

        val lp1 = LinearLayout.LayoutParams(match, wrap)
        lp1.setMargins(0, dp(16), 0, 0)
        val lp2 = LinearLayout.LayoutParams(match, wrap)
        lp2.setMargins(0, dp(8), 0, 0)
        layout.addView(box, lp1)
        layout.addView(btn, lp2)
    }

    // ---------- screen ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBar?.hide()
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(cBg)

        // upar ki bar
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(14), dp(12), dp(14), dp(6))
        val newChat = iconBtn("✎", cBtn, cText, 44, 18f)
        val gear = iconBtn("⚙", cBtn, cText, 44, 20f)
        titleView = TextView(this)
        titleView.text = "Jarvis"
        titleView.textSize = 17f
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.setTextColor(cText)
        titleView.gravity = Gravity.CENTER
        top.addView(newChat)
        top.addView(titleView, LinearLayout.LayoutParams(0, wrap, 1f))
        top.addView(gear)

        val content = FrameLayout(this)

        // home
        homeLayer = LinearLayout(this)
        homeLayer.orientation = LinearLayout.VERTICAL
        homeLayer.gravity = Gravity.BOTTOM
        homeLayer.setPadding(dp(22), 0, dp(22), dp(18))
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = TextView(this)
        hello.text = if (hour < 12) "सुप्रभात! आज क्या करना है?"
        else if (hour < 17) "नमस्ते! मैं क्या मदद करूँ?"
        else "आज रात आपके मन में क्या है?"
        hello.textSize = 22f
        hello.typeface = Typeface.DEFAULT_BOLD
        hello.setTextColor(cText)
        hello.setPadding(0, 0, 0, dp(8))
        homeLayer.addView(hello)
        val sugg = listOf(
            "📺" to "यूट्यूब पर TMKOC सर्च करो",
            "📍" to "मैप्स में जयपुर सर्च करो",
            "💡" to "भारत की अर्थव्यवस्था समझाओ"
        )
        for ((ic, tx) in sugg) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(12), 0, dp(12))
            val i1 = TextView(this)
            i1.text = ic
            i1.textSize = 18f
            val t1 = TextView(this)
            t1.text = tx
            t1.textSize = 15f
            t1.setTextColor(cText)
            t1.setPadding(dp(14), 0, 0, 0)
            row.addView(i1)
            row.addView(t1)
            row.setOnClickListener { submit(tx) }
            homeLayer.addView(row)
        }

        // chat
        chatBox = LinearLayout(this)
        chatBox.orientation = LinearLayout.VERTICAL
        chatBox.setPadding(dp(16), dp(10), dp(16), dp(10))
        chatScroll = ScrollView(this)
        chatScroll.isVerticalScrollBarEnabled = false
        chatScroll.addView(chatBox)

        // voice (orb)
        voiceLayer = LinearLayout(this)
        voiceLayer.orientation = LinearLayout.VERTICAL
        voiceLayer.gravity = Gravity.CENTER
        val size = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        orb = OrbView(this)
        voiceLayer.addView(orb, LinearLayout.LayoutParams(size, size))
        voiceStatus = TextView(this)
        voiceStatus.textSize = 14f
        voiceStatus.setTextColor(cMuted)
        voiceStatus.gravity = Gravity.CENTER
        voiceStatus.setPadding(0, dp(6), 0, 0)
        caption = TextView(this)
        caption.textSize = 17f
        caption.setTextColor(cText)
        caption.gravity = Gravity.CENTER
        caption.maxLines = 5
        caption.ellipsize = TextUtils.TruncateAt.END
        caption.setPadding(dp(28), dp(14), dp(28), 0)
        voiceLayer.addView(voiceStatus, LinearLayout.LayoutParams(match, wrap))
        voiceLayer.addView(caption, LinearLayout.LayoutParams(match, wrap))

        // settings
        val sBox = LinearLayout(this)
        sBox.orientation = LinearLayout.VERTICAL
        sBox.setPadding(dp(20), dp(16), dp(20), dp(24))
        val sTitle = TextView(this)
        sTitle.text = "सेटिंग्स"
        sTitle.textSize = 22f
        sTitle.typeface = Typeface.DEFAULT_BOLD
        sTitle.setTextColor(cText)
        val sInfo = TextView(this)
        sInfo.text = "Keys सिर्फ आपके फोन में सेव होती हैं। सेव करके ⚙ फिर दबाइए।"
        sInfo.textSize = 13f
        sInfo.setTextColor(cMuted)
        sInfo.setPadding(0, dp(6), 0, 0)
        sBox.addView(sTitle)
        sBox.addView(sInfo)
        addKeyRow(sBox, "Groq key यहाँ paste करो", "qkey", "Groq key save karo")
        addKeyRow(sBox, "ElevenLabs key यहाँ paste करो", "ekey", "ElevenLabs key save karo")
        addKeyRow(sBox, "ElevenLabs Voice ID यहाँ paste करो", "vid", "Voice ID save karo")
        settingsScroll = ScrollView(this)
        settingsScroll.addView(sBox)

        content.addView(homeLayer, FrameLayout.LayoutParams(match, match))
        content.addView(chatScroll, FrameLayout.LayoutParams(match, match))
        content.addView(voiceLayer, FrameLayout.LayoutParams(match, match))
        content.addView(settingsScroll, FrameLayout.LayoutParams(match, match))

        // neeche ki text pill
        typedBar = LinearLayout(this)
        typedBar.orientation = LinearLayout.HORIZONTAL
        typedBar.gravity = Gravity.CENTER_VERTICAL
        typedBar.setPadding(dp(12), dp(6), dp(12), dp(14))
        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = shape(cPill, false, 28)
        pill.setPadding(dp(18), dp(4), dp(6), dp(4))

        input = EditText(this)
        input.hint = "Jarvis से पूछिए"
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
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val has = !s.isNullOrBlank()
                arrow.visibility = if (has) View.VISIBLE else View.GONE
                wave.visibility = if (has) View.GONE else View.VISIBLE
            }
        })

        micIn = iconBtn("🎤", null, cText, 40, 18f)
        micIn.setOnClickListener { startListening(true) }

        val mainBtn = FrameLayout(this)
        mainBtn.background = shape(cBlue, true)
        wave = WaveIcon(this)
        arrow = TextView(this)
        arrow.text = "↑"
        arrow.textSize = 22f
        arrow.setTextColor(cText)
        arrow.gravity = Gravity.CENTER
        arrow.visibility = View.GONE
        mainBtn.addView(wave, FrameLayout.LayoutParams(match, match))
        mainBtn.addView(arrow, FrameLayout.LayoutParams(match, match))
        mainBtn.setOnClickListener {
            if (input.text.toString().isNotBlank()) sendTyped() else enterVoice()
        }

        pill.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        pill.addView(micIn, LinearLayout.LayoutParams(dp(40), dp(40)))
        val mlp = LinearLayout.LayoutParams(dp(40), dp(40))
        mlp.setMargins(dp(4), 0, 0, 0)
        pill.addView(mainBtn, mlp)
        typedBar.addView(pill, LinearLayout.LayoutParams(match, wrap))

        // voice mode ki bar
        voiceBar = LinearLayout(this)
        voiceBar.orientation = LinearLayout.HORIZONTAL
        voiceBar.gravity = Gravity.CENTER_VERTICAL
        voiceBar.setPadding(dp(12), dp(6), dp(12), dp(14))
        val vlabel = TextView(this)
        vlabel.text = "Jarvis से बात कर रहे हैं"
        vlabel.textSize = 15f
        vlabel.setTextColor(cMuted)
        vlabel.gravity = Gravity.CENTER_VERTICAL
        vlabel.background = shape(cPill, false, 28)
        vlabel.setPadding(dp(20), 0, dp(20), 0)
        muteBtn = iconBtn("🎤", cPill, cText, 52, 20f)
        val closeBtn = iconBtn("✕", Color.WHITE, Color.BLACK, 52, 18f)
        muteBtn.setOnClickListener { toggleMute() }
        closeBtn.setOnClickListener { exitVoice(true) }
        voiceBar.addView(vlabel, LinearLayout.LayoutParams(0, dp(52), 1f))
        val l1 = LinearLayout.LayoutParams(dp(52), dp(52))
        l1.setMargins(dp(10), 0, 0, 0)
        voiceBar.addView(muteBtn, l1)
        val l2 = LinearLayout.LayoutParams(dp(52), dp(52))
        l2.setMargins(dp(10), 0, 0, 0)
        voiceBar.addView(closeBtn, l2)

        root.addView(top, LinearLayout.LayoutParams(match, wrap))
        root.addView(content, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(typedBar, LinearLayout.LayoutParams(match, wrap))
        root.addView(voiceBar, LinearLayout.LayoutParams(match, wrap))
        setContentView(root)

        newChat.setOnClickListener {
            voiceMode = false
            stopAll()
            chatBox.removeAllViews()
            pending = null
            orb.setMode(0)
            showScreen(0)
        }
        gear.setOnClickListener {
            if (voiceMode) exitVoice(true)
            if (screen == 3) {
                showScreen(if (chatBox.childCount > 0) 1 else 0)
            } else {
                showScreen(3)
            }
        }

        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("hi", "IN"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA &&
                    r != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        runOnUiThread { speechDone() }
                    }
                    override fun onError(utteranceId: String?) {
                        runOnUiThread { speechDone() }
                    }
                })
            }
        }

        if (!hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        showScreen(0)
        if ((prefs().getString("qkey", "") ?: "").isEmpty()) {
            toast("पहले ऊपर ⚙ दबाकर Groq key सेव कीजिए")
        }
    }

    private fun showScreen(n: Int) {
        screen = n
        homeLayer.visibility = if (n == 0) View.VISIBLE else View.GONE
        chatScroll.visibility = if (n == 1) View.VISIBLE else View.GONE
        voiceLayer.visibility = if (n == 2) View.VISIBLE else View.GONE
        settingsScroll.visibility = if (n == 3) View.VISIBLE else View.GONE
        typedBar.visibility = if (n == 0 || n == 1) View.VISIBLE else View.GONE
        voiceBar.visibility = if (n == 2) View.VISIBLE else View.GONE
        titleView.visibility = if (n == 2) View.INVISIBLE else View.VISIBLE
        if (n == 1) scrollDown()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    // ---------- chat ----------

    private fun scrollDown() {
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addUserMsg(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setTextColor(cText)
        tv.background = shape(cBubble, false, 20)
        tv.setPadding(dp(16), dp(10), dp(16), dp(10))
        tv.maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        val lp = LinearLayout.LayoutParams(wrap, wrap)
        lp.gravity = Gravity.END
        lp.setMargins(0, dp(8), 0, dp(8))
        tv.alpha = 0f
        chatBox.addView(tv, lp)
        tv.animate().alpha(1f).setDuration(220).start()
        scrollDown()
    }

    private fun addAssistant(text: String, ready: Boolean): TextView {
        val holder = LinearLayout(this)
        holder.orientation = LinearLayout.VERTICAL
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setLineSpacing(0f, 1.2f)
        tv.setTextColor(if (ready) cText else cMuted)
        holder.addView(tv, LinearLayout.LayoutParams(wrap, wrap))
        val lp = LinearLayout.LayoutParams(match, wrap)
        lp.setMargins(0, dp(8), dp(24), dp(8))
        holder.alpha = 0f
        chatBox.addView(holder, lp)
        holder.animate().alpha(1f).setDuration(220).start()
        if (ready) attachActions(tv, text)
        scrollDown()
        return tv
    }

    private fun attachActions(tv: TextView, text: String) {
        val holder = tv.parent as? LinearLayout ?: return
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(4), 0, 0)
        val copy = TextView(this)
        copy.text = "📋"
        val up = TextView(this)
        up.text = "👍"
        val down = TextView(this)
        down.text = "👎"
        for (b in listOf(copy, up, down)) {
            b.textSize = 16f
            b.alpha = 0.55f
            b.setPadding(0, dp(6), dp(22), dp(6))
            row.addView(b)
        }
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("jarvis", text))
            toast("कॉपी हो गया")
        }
        up.setOnClickListener {
            up.alpha = 1f
            down.alpha = 0.55f
        }
        down.setOnClickListener {
            down.alpha = 1f
            up.alpha = 0.55f
        }
        holder.addView(row)
    }

    private fun showThinking(text: String) {
        orb.setMode(2)
        if (voiceMode) voiceStatus.text = "सोच रहा हूँ…"
        val p = pending
        if (p == null) {
            pending = addAssistant(text, false)
        } else {
            p.text = text
            scrollDown()
        }
    }

    private fun setReply(text: String) {
        val p = pending
        if (p != null) {
            p.text = text
            p.setTextColor(cText)
            pending = null
            attachActions(p, text)
            scrollDown()
        } else {
            addAssistant(text, true)
        }
        if (voiceMode) caption.text = text
    }

    private fun submit(text: String) {
        pending = null
        addUserMsg(text)
        if (!voiceMode) showScreen(1)
        handleCommand(text)
    }

    private fun sendTyped() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        stopAll()
        submit(t)
    }

    // ---------- voice mode ----------

    private fun setDictating(on: Boolean) {
        micIn.background = if (on) shape(Color.parseColor("#E53935"), true) else null
    }

    private fun enterVoice() {
        if (!hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            toast("पहले mic permission दें")
            return
        }
        voiceMode = true
        muted = false
        errStreak = 0
        muteBtn.text = "🎤"
        caption.text = ""
        voiceStatus.text = ""
        hideKeyboard()
        showScreen(2)
        startListening(false)
    }

    private fun exitVoice(stop: Boolean) {
        voiceMode = false
        if (stop) stopAll()
        orb.setMode(0)
        orb.setLevel(0f)
        showScreen(if (chatBox.childCount > 0) 1 else 0)
    }

    private fun toggleMute() {
        muted = !muted
        if (muted) {
            try { recognizer?.cancel() } catch (e: Exception) {}
            listening = false
            orb.setMode(0)
            orb.setLevel(0f)
            voiceStatus.text = "म्यूट है"
            muteBtn.text = "🔇"
        } else {
            muteBtn.text = "🎤"
            if (!speaking && !listening) startListening(false)
        }
    }

    private fun stopAll() {
        tts?.stop()
        try { player?.stop() } catch (e: Exception) {}
        try { recognizer?.cancel() } catch (e: Exception) {}
        speaking = false
        listening = false
        setDictating(false)
    }

    private fun restartListeningSoon() {
        orb.postDelayed({
            if (voiceMode && !muted && !speaking && !listening) startListening(false)
        }, 700L)
    }

    private fun speechDone() {
        speaking = false
        if (voiceMode) {
            orb.setMode(0)
            if (!muted) restartListeningSoon() else voiceStatus.text = "म्यूट है"
        }
    }

    private fun startListening(forInput: Boolean) {
        if (!hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            toast("पहले mic permission दें")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("इस फोन में voice recognition नहीं है")
            if (voiceMode) exitVoice(true)
            return
        }
        dictate = forInput
        tts?.stop()
        try { player?.stop() } catch (e: Exception) {}
        speaking = false
        listening = true
        setDictating(forInput)
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (voiceMode) {
                    voiceStatus.text = "सुन रहा हूँ…"
                    orb.setMode(1)
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (voiceMode) orb.setLevel((rmsdB + 2f) / 12f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                orb.setLevel(0f)
                if (voiceMode) {
                    voiceStatus.text = "समझ रहा हूँ…"
                    orb.setMode(2)
                }
            }
            override fun onError(error: Int) {
                listening = false
                setDictating(false)
                orb.setLevel(0f)
                if (voiceMode && !muted) {
                    if (error == 7 || error == 6) {
                        restartListeningSoon()
                    } else {
                        errStreak++
                        if (errStreak >= 3) {
                            toast("Voice error (code $error)")
                            exitVoice(true)
                        } else {
                            restartListeningSoon()
                        }
                    }
                } else if (!voiceMode) {
                    toast("सुन नहीं पाया (code $error). फिर से कोशिश करें")
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                setDictating(false)
                orb.setLevel(0f)
                val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (said.isNullOrBlank()) {
                    if (voiceMode && !muted) restartListeningSoon()
                    return
                }
                errStreak = 0
                if (dictate) {
                    input.setText(said)
                    input.setSelection(said.length)
                    input.requestFocus()
                    return
                }
                if (voiceMode) {
                    caption.text = said
                    voiceStatus.text = "सोच रहा हूँ…"
                    orb.setMode(2)
                }
                submit(said)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        recognizer?.startListening(intent)
    }

    // ---------- bolna ----------

    private fun speakPhone(text: String) {
        if (ttsReady) {
            speaking = true
            if (voiceMode) {
                orb.setMode(3)
                voiceStatus.text = "बोल रहा हूँ…"
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        } else {
            toast("Hindi voice फोन में install नहीं है")
            speechDone()
        }
    }

    private fun speak(text: String) {
        speaking = true
        if (voiceMode) {
            orb.setMode(3)
            voiceStatus.text = "बोल रहा हूँ…"
        }
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
                    conn.inputStream.use { inp ->
                        f.outputStream().use { out -> inp.copyTo(out) }
                    }
                    file = f
                } else {
                    val t = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                    err = "ElevenLabs error ($code): " + t.take(160)
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
                        mp.setOnCompletionListener { speechDone() }
                        mp.setOnErrorListener { _, _, _ ->
                            speechDone()
                            true
                        }
                        mp.start()
                        player = mp
                    } catch (e: Exception) {
                        toast("Play error: ${e.message}")
                        speakPhone(text)
                    }
                } else {
                    toast(err)
                    speakPhone(text)
                }
            }
        }.start()
    }

    // ---------- app kholna / search ----------

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

    // ---------- sawaal-jawab ----------

    private fun askGroq(question: String) {
        val key = prefs().getString("qkey", "") ?: ""
        if (key.isEmpty()) {
            setReply("पहले ऊपर ⚙ दबाकर Groq key सेव कीजिए।")
            speechDone()
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
                if (ok) speak(answer) else speechDone()
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

    // ---------- lifecycle ----------

    override fun onPause() {
        if (voiceMode) {
            voiceMode = false
            try { recognizer?.cancel() } catch (e: Exception) {}
            listening = false
            orb.setMode(0)
            orb.setLevel(0f)
            showScreen(if (chatBox.childCount > 0) 1 else 0)
        }
        super.onPause()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        player?.release()
        super.onDestroy()
    }
}
