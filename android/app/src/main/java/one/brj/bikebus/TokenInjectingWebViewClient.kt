package one.brj.bikebus

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.OkHttpClient
import okhttp3.Request

class TokenInjectingWebViewClient(
    private val authToken: String,
    private val onLoadFailed: () -> Unit,
) : WebViewClient() {

    private val client = OkHttpClient()

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val upstreamRequest = Request.Builder()
            .url(request.url.toString())
            .method(request.method, null)
            .apply {
                request.requestHeaders.forEach { (name, value) -> header(name, value) }
            }
            .header("X-Auth-Token", authToken)
            .build()

        return try {
            val response = client.newCall(upstreamRequest).execute()
            val body = response.body ?: return null
            val contentType = body.contentType()
            WebResourceResponse(
                contentType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
                contentType?.charset()?.name() ?: "utf-8",
                body.byteStream(),
            )
        } catch (e: Exception) {
            null // falls through to the WebView's own default handling / onReceivedError
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError,
    ) {
        if (request.isForMainFrame) onLoadFailed()
    }
}
