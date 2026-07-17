package com.voicecounter.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var speechService: SpeechService? = null
    private var currentModel: Model? = null
    private var currentRecognizer: Recognizer? = null
    private var loadedModelLang: String? = null
    private var pendingStartLang: String? = null
    private var pendingGrammar: String? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingStartLang?.let { startVosk(it) }
            else notifyJs("onNativeMicError", "permission_denied")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        // targetSdk 36 forces edge-to-edge, so content draws under the status bar
        // (the clock) unless we pad it back out ourselves
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(JsBridge(), "AndroidVosk")
        webView.loadUrl("file:///android_asset/www/index.html")
    }

    inner class JsBridge {
        // lang is "de" or "en" - matches the asset folder names model-de / model-en
        @JavascriptInterface
        fun start(lang: String) {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startVosk(lang)
                } else {
                    pendingStartLang = lang
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        @JavascriptInterface
        fun stop() {
            runOnUiThread { stopVosk() }
        }

        // jsonWords: JSON array of allowed words/names, e.g. ["anna","start","stop"].
        // Restricting the grammar to only what the app actually cares about massively
        // improves accuracy for the small model, since it never has to guess among the
        // full language vocabulary - applies instantly, no restart of the mic needed.
        @JavascriptInterface
        fun setVocabulary(jsonWords: String) {
            runOnUiThread {
                pendingGrammar = jsonWords
                currentRecognizer?.setGrammar(jsonWords)
            }
        }
    }

    private fun startVosk(lang: String) {
        // German-only for now (small English model gave noticeably worse accuracy and
        // doubled the app size for a language this app barely used)
        if (loadedModelLang == "de" && currentModel != null) {
            beginListening(currentModel!!)
            return
        }
        stopVosk()
        StorageService.unpack(this, "model-de", "model",
            { model ->
                currentModel = model
                loadedModelLang = "de"
                beginListening(model)
            },
            { exception ->
                notifyJs("onNativeMicError", "model_load_failed: ${exception.message}")
            })
    }

    private fun beginListening(model: Model) {
        val rec = Recognizer(model, 16000.0f)
        pendingGrammar?.let { rec.setGrammar(it) }
        currentRecognizer = rec
        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                val text = extractField(hypothesis, "partial")
                if (!text.isNullOrBlank()) notifyJs("onNativePartial", text)
            }
            override fun onResult(hypothesis: String?) {
                val text = extractField(hypothesis, "text")
                if (!text.isNullOrBlank()) notifyJs("onNativeTranscript", text)
            }
            // onFinalResult only fires as a flush when listening is explicitly stopped
            // (e.g. recognizer.getFinalResult() on shutdown) - forwarding it too, on top
            // of onResult's normal per-utterance firing, could double-process a single
            // spoken utterance as two separate transcripts. Only onResult is needed here
            // since listening never stops during normal use.
            override fun onFinalResult(hypothesis: String?) {}
            override fun onError(exception: Exception?) {
                notifyJs("onNativeMicError", exception?.message ?: "unknown")
            }
            override fun onTimeout() {}
        }
        speechService = SpeechService(rec, 16000.0f)
        speechService?.startListening(listener)
        notifyJs("onNativeMicStatus", "listening")
    }

    private fun stopVosk() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        currentRecognizer = null
        notifyJs("onNativeMicStatus", "stopped")
    }

    private fun extractField(json: String?, field: String): String? {
        if (json.isNullOrBlank()) return null
        return try {
            JSONObject(json).optString(field, "")
        } catch (e: Exception) {
            null
        }
    }

    private fun notifyJs(fn: String, arg: String) {
        val payload = JSONObject.quote(arg)
        runOnUiThread {
            webView.evaluateJavascript("window.$fn && window.$fn($payload)", null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVosk()
        currentModel?.close()
    }
}
