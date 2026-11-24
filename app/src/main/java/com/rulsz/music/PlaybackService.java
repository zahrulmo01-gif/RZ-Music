
package com.rulsz.music;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.io.File;

@UnstableApi
public class PlaybackService extends MediaSessionService {

    private static MediaSession mediaSession;
    private static ExoPlayer player;
    private static SimpleCache simpleCache;

    private static volatile String streamUserAgent =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36";

    public static void setStreamUserAgent(String userAgent) {
        if (userAgent != null && !userAgent.trim().isEmpty()) {
            streamUserAgent = userAgent;
        }
    }

    public static ExoPlayer getPlayer() {
        return player;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (simpleCache == null) {
            File cacheDir = new File(getCacheDir(), "media_cache");

            simpleCache = new SimpleCache(
                    cacheDir,
                    new LeastRecentlyUsedCacheEvictor(100L * 1024L * 1024L),
                    new StandaloneDatabaseProvider(this)
            );
        }

        DataSource.Factory upstreamFactory = () ->
                new DefaultHttpDataSource.Factory()
                        .setUserAgent(streamUserAgent)
                        .setAllowCrossProtocolRedirects(true)
                        .createDataSource();

        CacheDataSource.Factory cacheFactory =
                new CacheDataSource.Factory()
                        .setCache(simpleCache)
                        .setUpstreamDataSourceFactory(upstreamFactory)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build();

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                        new DefaultMediaSourceFactory(cacheFactory)
                )
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        mediaSession = new MediaSession.Builder(this, player)
                .build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(
            MediaSession.ControllerInfo controllerInfo
    ) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }

        if (player != null) {
            player.release();
            player = null;
        }

        super.onDestroy();
    }
}
