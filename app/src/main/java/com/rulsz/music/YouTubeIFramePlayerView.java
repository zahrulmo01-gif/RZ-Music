package com.rulsz.music;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class YouTubeIFramePlayerView extends WebView {

    public interface PlayerCallback {
        void onStateChanged(boolean isPlaying);
        void onProgress(long currentMs, long durationMs);
        void onEnded();
    }

    private PlayerCallback callback;
    private boolean isReady = false;
    private String pendingVideoId = null;

    @SuppressLint("SetJavaScriptEnabled")
    public YouTubeIFramePlayerView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        setWebChromeClient(new WebChromeClient());
        setWebViewClient(new WebViewClient());
        addJavascriptInterface(new PlayerBridge(), "AndroidBridge");

        loadIFrameHtml();
    }

    public void setPlayerCallback(PlayerCallback callback) {
        this.callback = callback;
    }

    private void loadIFrameHtml() {
        String html = "<!DOCTYPE html><html><body style='margin:0;background:#000;overflow:hidden;'>"
                + "<div id='player'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script>"
                + "<script>"
                + "var player;"
                + "function onYouTubeIframeAPIReady() {"
                + "  player = new YT.Player('player', {"
                + "    height: '100%', width: '100%',"
                + "    playerVars: { 'playsinline': 1, 'controls': 0, 'rel': 0 },"
                + "    events: {"
                + "      'onReady': function() { AndroidBridge.onReady(); },"
                + "      'onStateChange': function(e) {"
                + "        if (e.data === 1) AndroidBridge.onState(true);"
                + "        else if (e.data === 2) AndroidBridge.onState(false);"
                + "        else if (e.data === 0) AndroidBridge.onEnded();"
                + "      }"
                + "    }"
                + "  });"
                + "  setInterval(function() {"
                + "    if (player && player.getCurrentTime && player.getDuration) {"
                + "      AndroidBridge.onProgress(Math.floor(player.getCurrentTime()*1000), Math.floor(player.getDuration()*1000));"
                + "    }"
                + "  }, 500);"
                + "}"
                + "</script></body></html>";

        loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    public void loadVideo(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        if (!isReady) {
            pendingVideoId = videoId;
            return;
        }
        evaluateJavascript("if(player){player.loadVideoById('" + videoId + "');}", null);
    }

    public void playVideo() {
        if (isReady) evaluateJavascript("if(player){player.playVideo();}", null);
    }

    public void pauseVideo() {
        if (isReady) evaluateJavascript("if(player){player.pauseVideo();}", null);
    }

    public void seekToMs(long ms) {
        if (isReady) evaluateJavascript("if(player){player.seekTo(" + (ms / 1000.0) + ", true);}", null);
    }

    private class PlayerBridge {
        @JavascriptInterface
        public void onReady() {
            post(() -> {
                isReady = true;
                if (pendingVideoId != null) {
                    loadVideo(pendingVideoId);
                    pendingVideoId = null;
                }
            });
        }

        @JavascriptInterface
        public void onState(boolean playing) {
            if (callback != null) post(() -> callback.onStateChanged(playing));
        }

        @JavascriptInterface
        public void onProgress(long currentMs, long durationMs) {
            if (callback != null) post(() -> callback.onProgress(currentMs, durationMs));
        }

        @JavascriptInterface
        public void onEnded() {
            if (callback != null) post(() -> callback.onEnded());
        }
    }
}
