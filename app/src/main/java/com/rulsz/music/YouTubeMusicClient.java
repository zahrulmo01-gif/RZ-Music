package com.rulsz.music;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class YouTubeMusicClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String PREF_NAME = "convx_auth_prefs";
    private static final String KEY_COOKIE = "innertube_cookie";
    private static String savedCookie = "";

    public static class StreamResult {
        public String url;
        public String userAgent;

        public StreamResult(String url, String userAgent) {
            this.url = url;
            this.userAgent = userAgent;
        }
    }

    public static class Song {
        public String videoId;
        public String title;
        public String artist;
        public String thumbnail;

        public Song(String videoId, String title, String artist, String thumbnail) {
            this.videoId = videoId;
            this.title = title;
            this.artist = artist;
            this.thumbnail = thumbnail;
        }
    }

    // Muat cookie tersimpan saat aplikasi dibuka
    public static void init(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        savedCookie = prefs.getString(KEY_COOKIE, "");
    }

    public static String getRawToken(Context context) {
        if (savedCookie.isEmpty()) init(context);
        if (savedCookie.isEmpty()) return "";
        return "***INNERTUBE COOKIE***\n=" + savedCookie;
    }

    public static boolean isLoggedIn() {
        return !extractSapisid(savedCookie).isEmpty();
    }

    // Parsing & Validasi token format ***INNERTUBE COOKIE*** seperti di Metrolist/ArchiveTune
    public static boolean saveToken(Context context, String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            savedCookie = "";
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().remove(KEY_COOKIE).apply();
            return true;
        }

        String cleaned = rawInput.trim();
        if (cleaned.startsWith("***INNERTUBE COOKIE***")) {
            cleaned = cleaned.replace("***INNERTUBE COOKIE***", "").trim();
            if (cleaned.startsWith("=")) {
                cleaned = cleaned.substring(1).trim();
            }
        }
        // Gabungkan baris baru jika user paste multi-line
        cleaned = cleaned.replace("\n", "").replace("\r", "").trim();

        // Validasi wajib memiliki SAPISID atau __Secure-3PAPISID
        if (extractSapisid(cleaned).isEmpty()) {
            return false;
        }

        savedCookie = cleaned;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_COOKIE, cleaned).apply();
        return true;
    }

    private static String extractSapisid(String cookie) {
        if (cookie == null || cookie.isEmpty()) return "";
        String[] parts = cookie.split(";");
        String fallback = "";
        for (String part : parts) {
            String p = part.trim();
            if (p.startsWith("__Secure-3PAPISID=")) {
                return p.substring("__Secure-3PAPISID=".length()).trim();
            }
            if (p.startsWith("SAPISID=")) {
                fallback = p.substring("SAPISID=".length()).trim();
            }
        }
        return fallback;
    }

    // Generate Authorization: SAPISIDHASH <timestamp>_<sha1> untuk autentikasi YouTube Music
    private static String buildSapisidHashHeader(String origin) {
        String sapisid = extractSapisid(savedCookie);
        if (sapisid.isEmpty()) return null;
        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            String input = timestamp + " " + sapisid + " " + origin;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return "SAPISIDHASH " + timestamp + "_" + hex;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Song> searchSongs(String query) throws Exception {
        String url = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false";
        String payload = "{"
                + "\"context\": {\"client\": {\"clientName\": \"WEB_REMIX\", \"clientVersion\": \"1.20241101.01.00\", \"hl\": \"id\", \"gl\": \"ID\"}},"
                + "\"query\": " + JSONObject.quote(query) + ","
                + "\"params\": \"EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D\""
                + "}";

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Origin", "https://music.youtube.com")
                .post(RequestBody.create(payload, JSON));

        if (!savedCookie.isEmpty()) {
            reqBuilder.header("Cookie", savedCookie);
            String auth = buildSapisidHashHeader("https://music.youtube.com");
            if (auth != null) reqBuilder.header("Authorization", auth);
        }

        List<Song> results = new ArrayList<>();
        try (Response response = client.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return results;
            JSONObject root = new JSONObject(response.body().string());

            JSONArray tabs = root.getJSONObject("contents")
                    .getJSONObject("tabbedSearchResultsRenderer")
                    .getJSONArray("tabs");
            JSONArray sectionContents = tabs.getJSONObject(0)
                    .getJSONObject("tabRenderer")
                    .getJSONObject("content")
                    .getJSONObject("sectionListRenderer")
                    .getJSONArray("contents");

            for (int i = 0; i < sectionContents.length(); i++) {
                JSONObject sec = sectionContents.getJSONObject(i);
                if (!sec.has("musicShelfRenderer")) continue;
                JSONArray items = sec.getJSONObject("musicShelfRenderer").getJSONArray("contents");

                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer");
                    if (item == null) continue;

                    String videoId = item.optJSONObject("playlistItemData") != null
                            ? item.getJSONObject("playlistItemData").optString("videoId", "") : "";
                    if (videoId.isEmpty()) continue;

                    JSONArray flexCols = item.getJSONArray("flexColumns");
                    String title = flexCols.getJSONObject(0)
                            .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            .getJSONObject("text").getJSONArray("runs")
                            .getJSONObject(0).getString("text");

                    String artist = flexCols.getJSONObject(1)
                            .getJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            .getJSONObject("text").getJSONArray("runs")
                            .getJSONObject(0).getString("text");

                    String thumb = "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg";
                    results.add(new Song(videoId, title, artist, thumb));
                }
            }
        }
        return results;
    }

    public static StreamResult getAudioStream(String videoId) {
        String[][] clients = {
                {
                        "ANDROID_VR",
                        "28",
                        "1.60.19",
                        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                        "\"deviceMake\": \"Oculus\", \"deviceModel\": \"Quest 3\", \"androidSdkVersion\": 32, \"osName\": \"Android\", \"osVersion\": \"12L\""
                },
                {
                        "IOS",
                        "5",
                        "19.45.4",
                        "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                        "\"deviceMake\": \"Apple\", \"deviceModel\": \"iPhone16,2\", \"osName\": \"iPhone\", \"osVersion\": \"18.1.0.22B83\""
                },
                {
                        "ANDROID_MUSIC",
                        "21",
                        "7.27.52",
                        "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14; gzip)",
                        "\"androidSdkVersion\": 34, \"osName\": \"Android\", \"osVersion\": \"14\""
                }
        };

        for (String[] c : clients) {
            String streamUrl = fetchStreamWithClient(videoId, c[0], c[1], c[2], c[3], c[4]);
            if (streamUrl != null && !streamUrl.isEmpty()) {
                return new StreamResult(streamUrl, c[3]);
            }
        }
        return null;
    }

    private static String fetchStreamWithClient(
            String videoId, String clientName, String clientId, String clientVersion, String userAgent, String extraJson) {
        try {
            String url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false";
            String payload = "{"
                    + "\"context\": {\"client\": {"
                    + "\"clientName\": \"" + clientName + "\", "
                    + "\"clientVersion\": \"" + clientVersion + "\", "
                    + extraJson + ", "
                    + "\"hl\": \"id\", \"gl\": \"ID\""
                    + "}},"
                    + "\"videoId\": \"" + videoId + "\","
                    + "\"contentCheckOk\": true,"
                    + "\"racyCheckOk\": true"
                    + "}";

            Request.Builder reqBuilder = new Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("X-YouTube-Client-Name", clientId)
                    .header("X-YouTube-Client-Version", clientVersion)
                    .header("Origin", "https://www.youtube.com")
                    .post(RequestBody.create(payload, JSON));

            if (!savedCookie.isEmpty()) {
                reqBuilder.header("Cookie", savedCookie);
                String auth = buildSapisidHashHeader("https://www.youtube.com");
                if (auth != null) reqBuilder.header("Authorization", auth);
            }

            try (Response response = client.newCall(reqBuilder.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                JSONObject root = new JSONObject(response.body().string());
                if (!root.has("streamingData")) return null;

                JSONObject streamingData = root.getJSONObject("streamingData");
                JSONArray formats = streamingData.optJSONArray("adaptiveFormats");
                if (formats == null) return null;

                String bestAudioUrl = null;
                int maxBitrate = 0;

                for (int i = 0; i < formats.length(); i++) {
                    JSONObject fmt = formats.getJSONObject(i);
                    String mimeType = fmt.optString("mimeType", "");
                    if (mimeType.startsWith("audio/") && fmt.has("url")) {
                        int bitrate = fmt.optInt("bitrate", 0);
                        if (bitrate > maxBitrate) {
                            maxBitrate = bitrate;
                            bestAudioUrl = fmt.getString("url");
                        }
                    }
                }
                return bestAudioUrl;
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
