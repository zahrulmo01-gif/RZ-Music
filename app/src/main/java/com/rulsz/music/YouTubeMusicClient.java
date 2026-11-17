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

    // 1. Cari lagu langsung dari InnerTube YouTube Music (WEB_REMIX)
    public static List<Song> searchSongs(String query) throws Exception {
        String url = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false";
        String payload = "{"
                + "\"context\": {\"client\": {\"clientName\": \"WEB_REMIX\", \"clientVersion\": \"1.20240724.00.00\", \"hl\": \"id\", \"gl\": \"ID\"}},"
                + "\"query\": " + JSONObject.quote(query) + ","
                + "\"params\": \"EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D\"" // Filter khusus Songs
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
            String raw = response.body().string();
            JSONObject root = new JSONObject(raw);

            // Navigasi struktur JSON InnerTube MusicResponsiveListItemRenderer
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

                    String thumb = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                    results.add(new Song(videoId, title, artist, thumb));
                }
            }
        }
        return results;
    }

    // 2. Ambil Direct Audio Stream URL via InnerTube ANDROID_MUSIC
    public static String getAudioStreamUrl(String videoId) throws Exception {
        String url = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false";
        String payload = "{"
                + "\"context\": {\"client\": {\"clientName\": \"ANDROID_MUSIC\", \"clientVersion\": \"7.03.52\", \"androidSdkVersion\": 34, \"hl\": \"id\", \"gl\": \"ID\"}},"
                + "\"videoId\": \"" + videoId + "\""
                + "}";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "com.google.android.apps.youtube.music/7.03.52 (Linux; U; Android 14)")
                .post(RequestBody.create(payload, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            JSONObject root = new JSONObject(response.body().string());
            JSONArray formats = root.getJSONObject("streamingData").getJSONArray("adaptiveFormats");

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
    }
}
