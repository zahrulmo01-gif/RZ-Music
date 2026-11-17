package com.rulsz.music;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public class YouTubeMusicClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String PREF_NAME = "convx_auth_prefs";
    private static final String KEY_COOKIE = "innertube_cookie";
    private static final String KEY_AVATAR = "account_avatar";
    private static final String KEY_NAME = "account_name";

    private static String savedCookie = "";
    private static String savedAvatarUrl = "";
    private static String savedAccountName = "RZ";
    private static String visitorData = "";
    private static boolean newPipeInitialized = false;

    public static class StreamResult {
        public String url;
        public String errorDetail;

        public StreamResult(String url, String errorDetail) {
            this.url = url;
            this.errorDetail = errorDetail;
        }
    }

    public static class Song {
        public String videoId;
        public String browseId;
        public String browseParams;
        public String title;
        public String subtitle;
        public String thumbnail;
        public boolean isPlaylist;
        public boolean isMood;
        public int moodColor;

        public Song(String videoId, String browseId, String title, String subtitle, String thumbnail, boolean isPlaylist) {
            this.videoId = videoId;
            this.browseId = browseId;
            this.browseParams = "";
            this.title = title;
            this.subtitle = subtitle;
            this.thumbnail = thumbnail;
            this.isPlaylist = isPlaylist;
            this.isMood = false;
            this.moodColor = 0;
        }
    }

    public static class HomeSection {
        public String title;
        public String strapline;
        public List<Song> items = new ArrayList<>();
    }

    // Downloader bersih tanpa cookie browser agar YouTube tidak membalas "The page needs to be reloaded."
    private static class OkHttpNewPipeDownloader extends Downloader {
        @Override
        public Response execute(Request request) throws IOException, ReCaptchaException {
            okhttp3.Request.Builder rb = new okhttp3.Request.Builder()
                    .url(request.url())
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9");

            Map<String, List<String>> headers = request.headers();
            if (headers != null) {
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    if ("Cookie".equalsIgnoreCase(entry.getKey())) continue;
                    for (String val : entry.getValue()) {
                        rb.addHeader(entry.getKey(), val);
                    }
                }
            }

            byte[] data = request.dataToSend();
            if ("POST".equalsIgnoreCase(request.httpMethod())) {
                RequestBody body = RequestBody.create(data != null ? data : new byte[0], null);
                rb.post(body);
            } else {
                rb.get();
            }

            try (okhttp3.Response res = client.newCall(rb.build()).execute()) {
                if (res.code() == 429) {
                    throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
                }
                String bodyStr = res.body() != null ? res.body().string() : "";
                return new Response(
                        res.code(),
                        res.message(),
                        res.headers().toMultimap(),
                        bodyStr,
                        res.request().url().toString()
                );
            }
        }
    }

    public static void init(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        savedCookie = prefs.getString(KEY_COOKIE, "");
        savedAvatarUrl = prefs.getString(KEY_AVATAR, "");
        savedAccountName = prefs.getString(KEY_NAME, "RZ");
        if (!newPipeInitialized) {
            NewPipe.init(new OkHttpNewPipeDownloader(), new Localization("en", "US"));
            newPipeInitialized = true;
        }
    }

    public static String getSavedAvatarUrl() {
        return savedAvatarUrl;
    }

    public static String getSavedAccountName() {
        return savedAccountName;
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
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (rawInput == null || rawInput.trim().isEmpty()) {
            savedCookie = "";
            savedAvatarUrl = "";
            savedAccountName = "RZ";
            prefs.edit().remove(KEY_COOKIE).remove(KEY_AVATAR).remove(KEY_NAME).apply();
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
        prefs.edit().putString(KEY_COOKIE, cleaned).apply();
        return true;
    }

    // Ambil foto profil & nama akun YouTube Music (account/account_menu)
    public static void fetchAccountProfile(Context context) {
        if (!isLoggedIn()) return;
        try {
            String payload = "{\"context\": {\"client\": {\"clientName\": \"WEB_REMIX\", \"clientVersion\": \"1.20241118.01.00\", \"hl\": \"id\", \"gl\": \"ID\"}}}";
            try (okhttp3.Response res = client.newCall(buildWebRemixRequest("account/account_menu", payload).build()).execute()) {
                if (!res.isSuccessful() || res.body() == null) return;
                JSONObject root = new JSONObject(res.body().string());
                JSONArray actions = root.optJSONArray("actions");
                if (actions == null || actions.length() == 0) return;

                JSONObject header = actions.getJSONObject(0)
                        .getJSONObject("openPopupAction")
                        .getJSONObject("popup")
                        .getJSONObject("multiPageMenuRenderer")
                        .getJSONObject("header")
                        .getJSONObject("activeAccountHeaderRenderer");

                String name = extractRunsText(header.optJSONObject("accountName"));
                JSONArray thumbs = header.getJSONObject("accountPhoto").getJSONArray("thumbnails");
                String avatar = thumbs.getJSONObject(thumbs.length() - 1).optString("url", "");

                if (!avatar.isEmpty()) {
                    savedAvatarUrl = avatar;
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit().putString(KEY_AVATAR, avatar).apply();
                }
                if (!name.isEmpty()) {
                    savedAccountName = name;
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit().putString(KEY_NAME, name).apply();
                }
            }
        } catch (Exception ignored) {
        }
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

    private static okhttp3.Request.Builder buildWebRemixRequest(String endpoint, String payload) {
        okhttp3.Request.Builder rb = new okhttp3.Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/" + endpoint + "?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
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

    // Ambil Section Beranda / Explore / Library + Otomatis muat halaman lanjutan (continuations)
    public static List<HomeSection> getBrowseSections(String browseId, String params) throws Exception {
        String paramsJson = (params != null && !params.isEmpty()) ? ", \"params\": \"" + params + "\"" : "";
        String payload = "{"
                + "\"context\": {\"client\": {\"clientName\": \"WEB_REMIX\", \"clientVersion\": \"1.20241118.01.00\", \"hl\": \"id\", \"gl\": \"ID\"}},"
                + "\"browseId\": \"" + browseId + "\"" + paramsJson
                + "}";

        List<HomeSection> sections = new ArrayList<>();
        String nextContinuation = null;

        try (okhttp3.Response response = client.newCall(buildWebRemixRequest("browse", payload).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return sections;
            JSONObject root = new JSONObject(response.body().string());

            if (root.has("responseContext")) {
                String vd = root.getJSONObject("responseContext").optString("visitorData", "");
                if (!vd.isEmpty()) visitorData = vd;
            }

            JSONObject contents = root.optJSONObject("contents");
            if (contents == null) return sections;

            JSONObject sectionListRenderer = null;
            if (contents.has("singleColumnBrowseResultsRenderer")) {
                JSONArray tabs = contents.getJSONObject("singleColumnBrowseResultsRenderer").getJSONArray("tabs");
                JSONObject tabContent = tabs.getJSONObject(0).getJSONObject("tabRenderer").getJSONObject("content");
                sectionListRenderer = tabContent.optJSONObject("sectionListRenderer");
            } else if (contents.has("twoColumnBrowseResultsRenderer")) {
                sectionListRenderer = contents.getJSONObject("twoColumnBrowseResultsRenderer")
                        .getJSONObject("secondaryContents").optJSONObject("sectionListRenderer");
            }

            if (sectionListRenderer != null) {
                parseSectionList(sectionListRenderer.optJSONArray("contents"), sections);
                nextContinuation = extractContinuationToken(sectionListRenderer.optJSONArray("continuations"));
            }
        }

        // Muat hingga 3 halaman lanjutan (continuations) agar Your Playlists, Find Your Mood, dll ikut muncul
        int page = 0;
        while (nextContinuation != null && !nextContinuation.isEmpty() && page < 3) {
            page++;
            String contPayload = "{"
                    + "\"context\": {\"client\": {\"clientName\": \"WEB_REMIX\", \"clientVersion\": \"1.20241118.01.00\", \"hl\": \"id\", \"gl\": \"ID\"}},"
                    + "\"continuation\": \"" + nextContinuation + "\""
                    + "}";
            nextContinuation = null;
            try (okhttp3.Response contRes = client.newCall(buildWebRemixRequest("browse", contPayload).build()).execute()) {
                if (!contRes.isSuccessful() || contRes.body() == null) break;
                JSONObject contRoot = new JSONObject(contRes.body().string());
                JSONObject contContents = contRoot.optJSONObject("continuationContents");
                if (contContents != null && contContents.has("sectionListContinuation")) {
                    JSONObject slc = contContents.getJSONObject("sectionListContinuation");
                    parseSectionList(slc.optJSONArray("contents"), sections);
                    nextContinuation = extractContinuationToken(slc.optJSONArray("continuations"));
                }
            } catch (Exception ignored) {
                break;
            }
        }

        return sections;
    }

    private static String extractContinuationToken(JSONArray continuations) {
        if (continuations == null || continuations.length() == 0) return null;
        try {
            JSONObject first = continuations.getJSONObject(0);
            if (first.has("nextContinuationData")) {
                return first.getJSONObject("nextContinuationData").optString("continuation", null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void parseSectionList(JSONArray sectionList, List<HomeSection> outSections) {
        if (sectionList == null) return;
        for (int i = 0; i < sectionList.length(); i++) {
            try {
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
                    if (!hs.items.isEmpty()) outSections.add(hs);
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
                    if (!hs.items.isEmpty()) outSections.add(hs);
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
                    if (!hs.items.isEmpty()) outSections.add(hs);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static Song parseMusicItem(JSONObject wrapper) {
        try {
            if (wrapper.has("musicTwoRowItemRenderer")) {
                JSONObject item = wrapper.getJSONObject("musicTwoRowItemRenderer");
                String title = extractRunsText(item.optJSONObject("title"));
                String subtitle = extractRunsText(item.optJSONObject("subtitle"));
                String thumb = extractThumbnail(item.optJSONObject("thumbnailRenderer"));

                String videoId = "";
                String browseId = "";
                String params = "";
                JSONObject nav = item.optJSONObject("navigationEndpoint");
                if (nav != null) {
                    if (nav.has("watchEndpoint")) {
                        videoId = nav.getJSONObject("watchEndpoint").optString("videoId", "");
                    } else if (nav.has("browseEndpoint")) {
                        browseId = nav.getJSONObject("browseEndpoint").optString("browseId", "");
                        params = nav.getJSONObject("browseEndpoint").optString("params", "");
                    }
                }
                if (!videoId.isEmpty()) {
                    if (thumb.isEmpty()) thumb = "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg";
                    return new Song(videoId, "", title, subtitle, thumb, false);
                } else if (!browseId.isEmpty()) {
                    Song s = new Song("", browseId, title, subtitle, thumb, true);
                    s.browseParams = params;
                    return s;
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
            } else if (wrapper.has("musicNavigationButtonRenderer")) {
                // Tombol kategori Mood & Genre ("Find Your Mood")
                JSONObject btn = wrapper.getJSONObject("musicNavigationButtonRenderer");
                String title = extractRunsText(btn.optJSONObject("buttonText"));
                int color = 0xFF2563EB;
                JSONObject solid = btn.optJSONObject("solid");
                if (solid != null && solid.has("leftStripeColor")) {
                    color = (int) solid.getLong("leftStripeColor");
                }
                JSONObject click = btn.optJSONObject("clickCommand");
                if (click != null && click.has("browseEndpoint")) {
                    String browseId = click.getJSONObject("browseEndpoint").optString("browseId", "");
                    String params = click.getJSONObject("browseEndpoint").optString("params", "");
                    Song mood = new Song("", browseId, title, "Mood & Genre", "", true);
                    mood.browseParams = params;
                    mood.isMood = true;
                    mood.moodColor = color;
                    return mood;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String extractRunsText(JSONObject textObj) {
        if (textObj == null) return "";
        try {
            JSONArray runs = textObj.optJSONArray("runs");
            if (runs == null) return textObj.optString("simpleText", "");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < runs.length(); i++) {
                sb.append(runs.getJSONObject(i).optString("text", ""));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractThumbnail(JSONObject thumbContainer) {
        if (thumbContainer == null) return "";
        try {
            JSONObject mtr = thumbContainer.optJSONObject("musicThumbnailRenderer");
            if (mtr == null) return "";
            JSONObject thumbObj = mtr.optJSONObject("thumbnail");
            if (thumbObj == null) return "";
            JSONArray thumbs = thumbObj.optJSONArray("thumbnails");
            if (thumbs != null && thumbs.length() > 0) {
                return thumbs.getJSONObject(thumbs.length() - 1).optString("url", "");
            }
        } catch (Exception ignored) {
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
        try (okhttp3.Response response = client.newCall(buildWebRemixRequest("search", payload).build()).execute()) {
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

    public static StreamResult getAudioStream(String videoId) {
        try {
            String watchUrl = "https://www.youtube.com/watch?v=" + videoId;
            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl);
            List<AudioStream> audioStreams = info.getAudioStreams();
            if (audioStreams != null && !audioStreams.isEmpty()) {
                AudioStream best = audioStreams.stream()
                        .max(Comparator.comparingInt(AudioStream::getAverageBitrate))
                        .orElse(audioStreams.get(0));
                if (best != null && best.getContent() != null && !best.getContent().isEmpty()) {
                    return new StreamResult(best.getContent(), null);
                }
            }
            if (info.getHlsUrl() != null && !info.getHlsUrl().isEmpty()) {
                return new StreamResult(info.getHlsUrl(), null);
            }
            return new StreamResult(null, "Audio stream kosong");
        } catch (Exception e) {
            return new StreamResult(null, e.getMessage());
        }
    }
}
