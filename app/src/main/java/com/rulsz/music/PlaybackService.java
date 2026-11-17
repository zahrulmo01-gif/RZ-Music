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
    private static String visitorData = "";

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
        public String browseId; // jika item adalah playlist/album
        public String title;
        public String subtitle;
        public String thumbnail;
        public boolean isPlaylist;

        public Song(String videoId, String browseId, String title, String subtitle, String thumbnail, boolean isPlaylist) {
            this.videoId = videoId;
            this.browseId = browseId;
            this.title = title;
            this.subtitle = subtitle;
            this.thumbnail = thumbnail;
            this.isPlaylist = isPlaylist;
        }
    }

    public static class HomeSection {
        public String title;
        public String strapline;
        public List<Song> items = new ArrayList<>();
    }

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
        cleaned = cleaned.replace("\n", "").replace("\r", "").trim();

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

    private static Request.Builder buildWebRemixRequest(String endpoint, String payload) {
        Request.Builder rb = new Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/" + endpoint + "?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20241118.01.00")
                .post(RequestBody.create(payload, JSON));

        if (!visitorData.isEmpty()) {
            rb.header("X-Goog-Visitor-Id", visitorData);
        }
        if (!savedCookie.isEmpty()) {
            rb.header("Cookie", savedCookie);
            String auth = buildSapisidHashHeader("https://music.youtube.com");
            if (auth != null) {
                rb.header("Authorization", auth);
                rb.header("X-Goog-AuthUser", "0");
            }
        }
        return rb;
    }

    // Ambil Section Beranda / Explore / Library langsung dari YouTube Music
    public static List<HomeSection> getBrowseSections(String browseId) throws Exception {
        String payload = "{"
                + "\"context\": {\"client\": {\"clientName\": \"WEB_REMIX\", \"clientVersion\": \"1.20241118.01.00\", \"hl\": \"id\", \"gl\": \"ID\"}},"
                + "\"browseId\": \"" + browseId + "\""
                + "}";

        List<HomeSection> sections = new ArrayList<>();
        try (Response response = client.newCall(buildWebRemixRequest("browse", payload).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return sections;
            JSONObject root = new JSONObject(response.body().string());

            if (root.has("responseContext")) {
                String vd = root.getJSONObject("responseContext").optString("visitorData", "");
                if (!vd.isEmpty()) visitorData = vd;
            }

            JSONObject contents = root.optJSONObject("contents");
            if (contents == null) return sections;

            JSONArray sectionList = null;
            if (contents.has("singleColumnBrowseResultsRenderer")) {
                JSONArray tabs = contents.getJSONObject("singleColumnBrowseResultsRenderer").getJSONArray("tabs");
                JSONObject tabContent = tabs.getJSONObject(0).getJSONObject("tabRenderer").getJSONObject("content");
                if (tabContent.has("sectionListRenderer")) {
                    sectionList = tabContent.getJSONObject("sectionListRenderer").getJSONArray("contents");
                }
            } else if (contents.has("twoColumnBrowseResultsRenderer")) {
                JSONObject secCol = contents.getJSONObject("twoColumnBrowseResultsRenderer")
                        .getJSONObject("secondaryContents").getJSONObject("sectionListRenderer");
                sectionList = secCol.getJSONArray("contents");
            }

            if (sectionList == null) return sections;

            for (int i = 0; i < sectionList.length(); i++) {
                JSONObject secObj = sectionList.getJSONObject(i);
                if (secObj.has("musicCarouselShelfRenderer")) {
                    JSONObject carousel = secObj.getJSONObject("musicCarouselShelfRenderer");
                    HomeSection hs = new HomeSection();
                    JSONObject header = carousel.optJSONObject("header");
                    if (header != null && header.has("musicCarouselShelfBasicHeaderRenderer")) {
                        JSONObject basicHeader = header.getJSONObject("musicCarouselShelfBasicHeaderRenderer");
                        hs.title = extractRunsText(basicHeader.optJSONObject("title"));
                        hs.strapline = extractRunsText(basicHeader.optJSONObject("strapline"));
                    }
                    if (hs.title == null || hs.title.isEmpty()) hs.title = "Rekomendasi";

                    JSONArray items = carousel.optJSONArray("contents");
                    if (items != null) {
                        for (int j = 0; j < items.length(); j++) {
                            Song parsed = parseMusicItem(items.getJSONObject(j));
                            if (parsed != null) hs.items.add(parsed);
                        }
                    }
                    if (!hs.items.isEmpty()) sections.add(hs);
                } else if (secObj.has("musicShelfRenderer")) {
                    JSONObject shelf = secObj.getJSONObject("musicShelfRenderer");
                    HomeSection hs = new HomeSection();
                    hs.title = extractRunsText(shelf.optJSONObject("title"));
                    if (hs.title == null || hs.title.isEmpty()) hs.title = "Daftar Lagu";

                    JSONArray items = shelf.optJSONArray("contents");
                    if (items != null) {
                        for (int j = 0; j < items.length(); j++) {
                            Song parsed = parseMusicItem(items.getJSONObject(j));
                            if (parsed != null) hs.items.add(parsed);
                        }
                    }
                    if (!hs.items.isEmpty()) sections.add(hs);
                } else if (secObj.has("gridRenderer")) {
                    JSONObject grid = secObj.getJSONObject("gridRenderer");
                    HomeSection hs = new HomeSection();
                    hs.title = "Your Playlists";
                    JSONArray items = grid.optJSONArray("items");
                    if (items != null) {
                        for (int j = 0; j < items.length(); j++) {
                            Song parsed = parseMusicItem(items.getJSONObject(j));
                            if (parsed != null) hs.items.add(parsed);
                        }
                    }
                    if (!hs.items.isEmpty()) sections.add(hs);
                }
            }
        }
        return sections;
    }

    private static Song parseMusicItem(JSONObject wrapper) {
        if (wrapper.has("musicTwoRowItemRenderer")) {
            JSONObject item = wrapper.getJSONObject("musicTwoRowItemRenderer");
            String title = extractRunsText(item.optJSONObject("title"));
            String subtitle = extractRunsText(item.optJSONObject("subtitle"));
            String thumb = extractThumbnail(item.optJSONObject("thumbnailRenderer"));

            String videoId = "";
            String browseId = "";
            JSONObject nav = item.optJSONObject("navigationEndpoint");
            if (nav != null) {
                if (nav.has("watchEndpoint")) {
                    videoId = nav.getJSONObject("watchEndpoint").optString("videoId", "");
                } else if (nav.has("browseEndpoint")) {
                    browseId = nav.getJSONObject("browseEndpoint").optString("browseId", "");
                }
            }
            if (!videoId.isEmpty()) {
                if (thumb.isEmpty()) thumb = "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg";
                return new Song(videoId, "", title, subtitle, thumb, false);
            } else if (!browseId.isEmpty()) {
                return new Song("", browseId, title, subtitle, thumb, true);
            }
        } else if (wrapper.has("musicResponsiveListItemRenderer")) {
            JSONObject item = wrapper.getJSONObject("musicResponsiveListItemRenderer");
            String videoId = item.optJSONObject("playlistItemData") != null
                    ? item.getJSONObject("playlistItemData").optString("videoId", "") : "";
            String browseId = "";
            JSONObject nav = item.optJSONObject("navigationEndpoint");
            if (nav != null && nav.has("browseEndpoint")) {
                browseId = nav.getJSONObject("browseEndpoint").optString("browseId", "");
            }

            JSONArray flexCols = item.optJSONArray("flexColumns");
            String title = "";
            String subtitle = "";
            if (flexCols != null && flexCols.length() > 0) {
                title = extractRunsText(flexCols.getJSONObject(0)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer").optJSONObject("text"));
            }
            if (flexCols != null && flexCols.length() > 1) {
                subtitle = extractRunsText(flexCols.getJSONObject(1)
                        .getJSONObject("musicResponsiveListItemFlexColumnRenderer").optJSONObject("text"));
            }

            String thumb = extractThumbnail(item.optJSONObject("thumbnail"));
            if (!videoId.isEmpty()) {
                if (thumb.isEmpty()) thumb = "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg";
                return new Song(videoId, "", title, subtitle, thumb, false);
            } else if (!browseId.isEmpty()) {
                return new Song("", browseId, title, subtitle, thumb, true);
            }
        }
        return null;
    }

    private static String extractRunsText(JSONObject textObj) {
        if (textObj == null) return "";
        JSONArray runs = textObj.optJSONArray("runs");
        if (runs == null) return textObj.optString("simpleText", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            sb.append(runs.getJSONObject(i).optString("text", ""));
        }
        return sb.toString();
    }

    private static String extractThumbnail(JSONObject thumbContainer) {
        if (thumbContainer == null) return "";
        JSONObject mtr = thumbContainer.optJSONObject("musicThumbnailRenderer");
        if (mtr == null) return "";
        JSONObject thumbObj = mtr.optJSONObject("thumbnail");
        if (thumbObj == null) return "";
        JSONArray thumbs = thumbObj.optJSONArray("thumbnails");
        if (thumbs != null && thumbs.length() > 0) {
            return thumbs.getJSONObject(thumbs.length() - 1).optString("url", "");
        }
        return "";
    }

    public static List<Song> searchSongs(String query) throws Exception {
        String payload = "{"
                + "\"context\": {\"client\": {\"clientName\": \"WEB_REMIX\", \"clientVersion\": \"1.20241118.01.00\", \"hl\": \"id\", \"gl\": \"ID\"}},"
                + "\"query\": " + JSONObject.quote(query) + ","
                + "\"params\": \"EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D\""
                + "}";

        List<Song> results = new ArrayList<>();
        try (Response response = client.newCall(buildWebRemixRequest("search", payload).build()).execute()) {
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
                    Song parsed = parseMusicItem(items.getJSONObject(j));
                    if (parsed != null && !parsed.videoId.isEmpty()) {
                        results.add(parsed);
                    }
                }
            }
        }
        return results;
    }

    // Pengambilan Audio Stream Anti-Gagal (Tanpa mencampur cookie browser ke client mobile/VR + dukungan HLS)
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
                        "ANDROID_TESTSUITE",
                        "30",
                        "1.9",
                        "com.google.android.youtube/1.9 (Linux; U; Android 13; en_US)",
                        "\"androidSdkVersion\": 33, \"osName\": \"Android\", \"osVersion\": \"13\""
                }
        };

        for (String[] c : clients) {
            String streamUrl = fetchStreamWithClient(videoId, c[0], c[1], c[2], c[3], c[4]);
            if (streamUrl != null && !streamUrl.isEmpty()) {
                return new StreamResult(streamUrl, c[3]);
            }
        }

        // Fallback terakhir ke Piped public API jika IP ISP memblokir /player langsung
        String pipedUrl = fetchFromPipedFallback(videoId);
        if (pipedUrl != null && !pipedUrl.isEmpty()) {
            return new StreamResult(pipedUrl, "Mozilla/5.0 (Android 14; Mobile)");
        }
        return null;
    }

    private static String fetchStreamWithClient(
            String videoId, String clientName, String clientId, String clientVersion, String userAgent, String extraJson) {
        try {
            String url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false";
            String vdJson = visitorData.isEmpty() ? "" : "\"visitorData\": \"" + visitorData + "\", ";
            String payload = "{"
                    + "\"context\": {\"client\": {"
                    + "\"clientName\": \"" + clientName + "\", "
                    + "\"clientVersion\": \"" + clientVersion + "\", "
                    + vdJson
                    + extraJson + ", "
                    + "\"hl\": \"id\", \"gl\": \"ID\""
                    + "}},"
                    + "\"videoId\": \"" + videoId + "\","
                    + "\"playbackContext\": {\"contentPlaybackContext\": {\"html5Preference\": \"HTML5_PREF_WANTS\"}},"
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

            if (!visitorData.isEmpty()) {
                reqBuilder.header("X-Goog-Visitor-Id", visitorData);
            }

            try (Response response = client.newCall(reqBuilder.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                JSONObject root = new JSONObject(response.body().string());
                if (!root.has("streamingData")) return null;

                JSONObject streamingData = root.getJSONObject("streamingData");
                JSONArray formats = streamingData.optJSONArray("adaptiveFormats");
                String bestAudioUrl = null;
                int maxBitrate = 0;

                if (formats != null) {
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
                }
                if (bestAudioUrl != null) return bestAudioUrl;

                // Fallback ke HLS Manifest (khusus client IOS) jika adaptiveFormats terenkripsi
                String hls = streamingData.optString("hlsManifestUrl", "");
                if (!hls.isEmpty()) return hls;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String fetchFromPipedFallback(String videoId) {
        String[] instances = {
                "https://pipedapi.kavin.rocks/streams/",
                "https://pipedapi.adminforge.de/streams/"
        };
        for (String base : instances) {
            try {
                Request req = new Request.Builder()
                        .url(base + videoId)
                        .header("User-Agent", "Mozilla/5.0")
                        .build();
                try (Response res = client.newCall(req).execute()) {
                    if (!res.isSuccessful() || res.body() == null) continue;
                    JSONObject obj = new JSONObject(res.body().string());
                    JSONArray audioStreams = obj.optJSONArray("audioStreams");
                    if (audioStreams != null && audioStreams.length() > 0) {
                        return audioStreams.getJSONObject(0).optString("url", "");
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
