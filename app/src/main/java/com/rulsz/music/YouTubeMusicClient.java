package com.rulsz.music;

import org.json.JSONArray;
import org.json.JSONObject;
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

    public static List<Song> searchSongs(String query) throws Exception {
        String url = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false";
        String payload = "{"
                + "\"context\": {\"client\": {\"clientName\": \"WEB_REMIX\", \"clientVersion\": \"1.20241101.01.00\", \"hl\": \"id\", \"gl\": \"ID\"}},"
                + "\"query\": " + JSONObject.quote(query) + ","
                + "\"params\": \"EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D\""
                + "}";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Origin", "https://music.youtube.com")
                .post(RequestBody.create(payload, JSON))
                .build();

        List<Song> results = new ArrayList<>();
        try (Response response = client.newCall(request).execute()) {
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
        // Format: {clientName, clientIdHeader, clientVersion, userAgent, extraClientJson}
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

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("X-YouTube-Client-Name", clientId)
                    .header("X-YouTube-Client-Version", clientVersion)
                    .header("Origin", "https://www.youtube.com")
                    .post(RequestBody.create(payload, JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
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
                    // Ambil stream audio m4a/webm yang memiliki direct url (bukan signatureCipher)
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
