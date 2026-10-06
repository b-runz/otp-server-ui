package one.brj.bikebus

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = TokenInjectingWebViewClient(
            authToken = BuildConfig.OTP_AUTH_TOKEN,
            onLoadFailed = { runOnUiThread { showRetryView() } },
        )
        webView.loadUrl("https://otp.brj.one")

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else isEnabled = false.also { finish() }
        }
    }

    private fun showRetryView() {
        // A minimal native view with a message + Retry button that
        // re-calls webView.loadUrl("https://otp.brj.one") -- real layout
        // resource to be added here; not detailed further in this plan
        // since it's a small, low-risk UI addition with no real design
        // decisions left open.
    }
}
