package com.rulsz.music;

import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private MediaController mediaController;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<YouTubeMusicClient.Song> songList = new ArrayList<>();
    private SongAdapter adapter;
    private TextView nowPlayingText;
    private Button playPauseBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Root Layout (Dark Theme ala Metrolist)
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D0F12"));
        root.setPadding(36, 48, 36, 36);

        // Header
        TextView header = new TextView(this);
        header.setText("RulszMusic");
        header.setTextColor(Color.WHITE);
        header.setTextSize(24);
        header.setPadding(0, 0, 0, 24);
        root.addView(header);

        // Search Bar Container
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);

        EditText searchInput = new EditText(this);
        searchInput.setHint("Cari lagu atau artis di YT Music...");
        searchInput.setHintTextColor(Color.GRAY);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setPadding(32, 24, 32, 24);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#1A1D24"));
        inputBg.setCornerRadius(40);
        searchInput.setBackground(inputBg);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        searchInput.setLayoutParams(inputParams);

        Button searchBtn = new Button(this);
        searchBtn.setText("Cari");
        searchBtn.setTextColor(Color.BLACK);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#10B981")); // Emerald Accent
        btnBg.setCornerRadius(40);
        searchBtn.setBackground(btnBg);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(16, 0, 0, 0);
        searchBtn.setLayoutParams(btnParams);

        searchRow.addView(searchInput);
        searchRow.addView(searchBtn);
        root.addView(searchRow);

        // Song List (RecyclerView)
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SongAdapter();
        recyclerView.setAdapter(adapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.setMargins(0, 24, 0, 24);
        recyclerView.setLayoutParams(listParams);
        root.addView(recyclerView);

        // Mini Player Bar
        LinearLayout miniPlayer = new LinearLayout(this);
        miniPlayer.setOrientation(LinearLayout.HORIZONTAL);
        miniPlayer.setGravity(Gravity.CENTER_VERTICAL);
        miniPlayer.setPadding(32, 24, 32, 24);
        GradientDrawable playerBg = new GradientDrawable();
        playerBg.setColor(Color.parseColor("#161920"));
        playerBg.setCornerRadius(36);
        miniPlayer.setBackground(playerBg);

        nowPlayingText = new TextView(this);
        nowPlayingText.setText("Belum ada lagu diputar");
        nowPlayingText.setTextColor(Color.WHITE);
        nowPlayingText.setSingleLine(true);
        nowPlayingText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        playPauseBtn = new Button(this);
        playPauseBtn.setText("Play/Pause");
        playPauseBtn.setTextColor(Color.WHITE);
        playPauseBtn.setBackgroundColor(Color.TRANSPARENT);
        playPauseBtn.setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.isPlaying()) mediaController.pause();
                else mediaController.play();
            }
        });

        miniPlayer.addView(nowPlayingText);
        miniPlayer.addView(playPauseBtn);
        root.addView(miniPlayer);

        setContentView(root);

        // Hubungkan ke PlaybackService
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        ListenableFuture<MediaController> controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());

        // Event Pencarian
        searchBtn.setOnClickListener(v -> {
            String query = searchInput.getText().toString().trim();
            if (query.isEmpty()) return;
            Toast.makeText(this, "Mencari...", Toast.LENGTH_SHORT).show();
            executor.execute(() -> {
                try {
                    List<YouTubeMusicClient.Song> results = YouTubeMusicClient.searchSongs(query);
                    runOnUiThread(() -> {
                        songList.clear();
                        songList.addAll(results);
                        adapter.notifyDataSetChanged();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        });
    }

    private void playSong(YouTubeMusicClient.Song song) {
        nowPlayingText.setText("Memuat: " + song.title + "...");
        executor.execute(() -> {
            try {
                String streamUrl = YouTubeMusicClient.getAudioStreamUrl(song.videoId);
                if (streamUrl == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Gagal mengambil stream audio", Toast.LENGTH_SHORT).show());
                    return;
                }
                runOnUiThread(() -> {
                    if (mediaController == null) return;
                    MediaMetadata metadata = new MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(Uri.parse(song.thumbnail))
                            .build();

                    MediaItem mediaItem = new MediaItem.Builder()
                            .setUri(streamUrl)
                            .setMediaMetadata(metadata)
                            .build();

                    mediaController.setMediaItem(mediaItem);
                    mediaController.prepare();
                    mediaController.play();
                    nowPlayingText.setText(song.title + " • " + song.artist);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Gagal memutar: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout itemLayout = new LinearLayout(parent.getContext());
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(24, 24, 24, 24);
            itemLayout.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView title = new TextView(parent.getContext());
            title.setId(View.generateViewId());
            title.setTextColor(Color.WHITE);
            title.setTextSize(16);

            TextView artist = new TextView(parent.getContext());
            artist.setId(View.generateViewId());
            artist.setTextColor(Color.parseColor("#9CA3AF"));
            artist.setTextSize(13);

            itemLayout.addView(title);
            itemLayout.addView(artist);
            return new ViewHolder(itemLayout, title, artist);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            YouTubeMusicClient.Song song = songList.get(position);
            holder.title.setText(song.title);
            holder.artist.setText(song.artist);
            holder.itemView.setOnClickListener(v -> playSong(song));
        }

        @Override
        public int getItemCount() {
            return songList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, artist;
            ViewHolder(View itemView, TextView title, TextView artist) {
                super(itemView);
                this.title = title;
                this.artist = artist;
            }
        }
    }
}
