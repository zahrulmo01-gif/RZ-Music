package com.rulsz.music;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private MediaController mediaController;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final List<YouTubeMusicClient.Song> songList = new ArrayList<>();
    private SongAdapter adapter;
    private int currentIndex = -1;
    private int currentAccentColor = Color.parseColor("#1E293B");

    // Root & Backdrop
    private FrameLayout rootFrame;
    private View ambientBackdrop;

    // Profile & Settings Views
    private TextView profileIconBtn;
    private LinearLayout settingsSheet;
    private TextView accountStatusText;
    private TextView logoutRowBtn;

    // Mini Player Views
    private LinearLayout miniPlayerCard;
    private ImageView miniCover;
    private TextView miniTitle, miniArtist, miniPlayBtn, miniNextBtn;

    // Full Screen Now Playing Views
    private LinearLayout fullPlayerSheet;
    private ImageView fullCover;
    private TextView fullTitle, fullArtist, currentTimeText, totalTimeText, fullPlayBtn;
    private SeekBar seekBar;
    private boolean isSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YouTubeMusicClient.init(this);

        // Edge-to-edge transparent status bar ala iOS / Convx
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.parseColor("#090B0E"));
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(Color.parseColor("#090B0E"));

        // Adaptive Ambient Liquid Glow Backdrop
        ambientBackdrop = new View(this);
        updateAmbientColors(Color.parseColor("#1E3A5F"));
        rootFrame.addView(ambientBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Main Browse Screen
        LinearLayout browseScreen = buildBrowseScreen();
        rootFrame.addView(browseScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Floating Liquid Glass Mini Player
        miniPlayerCard = buildMiniPlayer();
        FrameLayout.LayoutParams miniParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        miniParams.gravity = Gravity.BOTTOM;
        miniParams.setMargins(dp(16), 0, dp(16), dp(24));
        rootFrame.addView(miniPlayerCard, miniParams);

        // Settings Screen Overlay
        settingsSheet = buildSettingsSheet();
        settingsSheet.setVisibility(View.GONE);
        rootFrame.addView(settingsSheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Full-Screen Cupertino Now Playing Sheet
        fullPlayerSheet = buildFullPlayerSheet();
        fullPlayerSheet.setVisibility(View.GONE);
        rootFrame.addView(fullPlayerSheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(rootFrame);
        refreshAccountState();
        initMediaController();
        startProgressUpdater();

        // Muat rekomendasi awal otomatis
        performSearch("Lagu Pop Indonesia Hits");
    }

    private LinearLayout buildBrowseScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(46), dp(18), 0);

        // Top Header ala Convx + Icon Profil di Pojok Kanan Atas
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(4), 0, dp(4), dp(14));

        TextView appTitle = new TextView(this);
        appTitle.setText("Convx");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(28);
        appTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        appTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Tombol Icon Profil (Liquid Glass Circle)
        profileIconBtn = new TextView(this);
        profileIconBtn.setText("👤");
        profileIconBtn.setTextColor(Color.WHITE);
        profileIconBtn.setTextSize(17);
        profileIconBtn.setGravity(Gravity.CENTER);
        profileIconBtn.setBackground(createGlassSurface(
                Color.parseColor("#26FFFFFF"), Color.parseColor("#55FFFFFF"), dp(21)));
        applySpringTouch(profileIconBtn);
        profileIconBtn.setOnClickListener(v -> openSettings());

        headerRow.addView(appTitle);
        headerRow.addView(profileIconBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));
        container.addView(headerRow);

        // Liquid Glass Pill Search Bar
        LinearLayout searchPill = new LinearLayout(this);
        searchPill.setOrientation(LinearLayout.HORIZONTAL);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setPadding(dp(16), dp(4), dp(8), dp(4));
        searchPill.setBackground(createGlassSurface(
                Color.parseColor("#1FFFFFFF"), Color.parseColor("#45FFFFFF"), dp(28)));

        EditText searchInput = new EditText(this);
        searchInput.setHint("Cari lagu, artis, atau album...");
        searchInput.setHintTextColor(Color.parseColor("#94A3B8"));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(14);
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView searchBtn = new TextView(this);
        searchBtn.setText("Cari");
        searchBtn.setTextColor(Color.WHITE);
        searchBtn.setTextSize(13);
        searchBtn.setTypeface(null, Typeface.BOLD);
        searchBtn.setGravity(Gravity.CENTER);
        searchBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        searchBtn.setBackground(createGlassSurface(
                Color.parseColor("#38FFFFFF"), Color.parseColor("#66FFFFFF"), dp(22)));
        applySpringTouch(searchBtn);

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.getText().toString().trim());
                return true;
            }
            return false;
        });
        searchBtn.setOnClickListener(v -> performSearch(searchInput.getText().toString().trim()));

        searchPill.addView(searchInput);
        searchPill.addView(searchBtn);
        container.addView(searchPill);

        // Song RecyclerView
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, dp(14), 0, dp(110));
        adapter = new SongAdapter();
        recyclerView.setAdapter(adapter);
        container.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return container;
    }

    // Halaman Pengaturan dengan Bagian "Pengaturan Akun -> Login dengan token" di Atas
    private LinearLayout buildSettingsSheet() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(48), dp(20), dp(24));
        sheet.setClickable(true);
        sheet.setBackgroundColor(Color.parseColor("#FA0B0E14"));

        // Top Bar Pengaturan (< Pengaturan)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(24));

        TextView backBtn = new TextView(this);
        backBtn.setText("‹");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(28);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setPadding(dp(8), 0, dp(14), dp(2));
        backBtn.setOnClickListener(v -> closeSettings());

        TextView title = new TextView(this);
        title.setText("Pengaturan");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);

        topBar.addView(backBtn);
        topBar.addView(title);
        sheet.addView(topBar);

        // Section Header: PENGATURAN AKUN
        TextView sectionLabel = new TextView(this);
        sectionLabel.setText("PENGATURAN AKUN");
        sectionLabel.setTextColor(Color.parseColor("#94A3B8"));
        sectionLabel.setTextSize(12);
        sectionLabel.setTypeface(null, Typeface.BOLD);
        sectionLabel.setPadding(dp(8), 0, 0, dp(10));
        sheet.addView(sectionLabel);

        // Kartu Liquid Glass untuk Pengaturan Akun
        LinearLayout accountCard = new LinearLayout(this);
        accountCard.setOrientation(LinearLayout.VERTICAL);
        accountCard.setBackground(createGlassSurface(
                Color.parseColor("#18FFFFFF"), Color.parseColor("#33FFFFFF"), dp(22)));

        // Item 1: Login dengan token
        LinearLayout tokenRow = new LinearLayout(this);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setGravity(Gravity.CENTER_VERTICAL);
        tokenRow.setPadding(dp(18), dp(16), dp(18), dp(16));
        applySpringTouch(tokenRow);

        TextView keyIcon = new TextView(this);
        keyIcon.setText("🔑");
        keyIcon.setTextSize(20);
        keyIcon.setPadding(0, 0, dp(14), 0);

        LinearLayout tokenTextCol = new LinearLayout(this);
        tokenTextCol.setOrientation(LinearLayout.VERTICAL);
        tokenTextCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tokenTitle = new TextView(this);
        tokenTitle.setText("Login dengan token");
        tokenTitle.setTextColor(Color.WHITE);
        tokenTitle.setTextSize(16);
        tokenTitle.setTypeface(null, Typeface.BOLD);

        accountStatusText = new TextView(this);
        accountStatusText.setText("Belum login • Ketuk untuk memasukkan InnerTube Cookie");
        accountStatusText.setTextColor(Color.parseColor("#94A3B8"));
        accountStatusText.setTextSize(12);

        tokenTextCol.addView(tokenTitle);
        tokenTextCol.addView(accountStatusText);

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextColor(Color.parseColor("#94A3B8"));
        chevron.setTextSize(22);

        tokenRow.addView(keyIcon);
        tokenRow.addView(tokenTextCol);
        tokenRow.addView(chevron);
        tokenRow.setOnClickListener(v -> showTokenLoginDialog());

        accountCard.addView(tokenRow);

        // Item 2: Logout / Hapus Token (muncul jika sudah login)
        logoutRowBtn = new TextView(this);
        logoutRowBtn.setText("Hapus token & keluar akun");
        logoutRowBtn.setTextColor(Color.parseColor("#F87171"));
        logoutRowBtn.setTextSize(14);
        logoutRowBtn.setTypeface(null, Typeface.BOLD);
        logoutRowBtn.setPadding(dp(18), dp(14), dp(18), dp(16));
        logoutRowBtn.setVisibility(View.GONE);
        logoutRowBtn.setOnClickListener(v -> {
            YouTubeMusicClient.saveToken(this, "");
            refreshAccountState();
            Toast.makeText(this, "Token dihapus", Toast.LENGTH_SHORT).show();
        });
        accountCard.addView(logoutRowBtn);

        sheet.addView(accountCard);

        return sheet;
    }

    // Dialog Login dengan Token persis seperti contoh Metrolist / ArchiveTune
    private void showTokenLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog dialog = builder.create();
                if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }


        LinearLayout dialogCard = new LinearLayout(this);
        dialogCard.setOrientation(LinearLayout.VERTICAL);
        dialogCard.setPadding(dp(20), dp(20), dp(20), dp(16));
        dialogCard.setBackground(createGlassSurface(
                Color.parseColor("#F222242A"), Color.parseColor("#44FFFFFF"), dp(24)));

        // Kotak Input Token Monospace dengan Border Putih Tipis
        EditText tokenInput = new EditText(this);
        tokenInput.setHint("***INNERTUBE COOKIE***\n=__Secure-1PSIDTS=...; SAPISID=...; __Secure-3PAPISID=...");
        tokenInput.setHintTextColor(Color.parseColor("#64748B"));
        tokenInput.setTextColor(Color.parseColor("#E2E8F0"));
        tokenInput.setTextSize(13);
        tokenInput.setTypeface(Typeface.MONOSPACE);
        tokenInput.setGravity(Gravity.TOP | Gravity.START);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        tokenInput.setMinLines(8);
        tokenInput.setMaxLines(12);
        tokenInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        tokenInput.setBackground(createGlassSurface(
                Color.parseColor("#181A20"), Color.parseColor("#88FFFFFF"), dp(12)));

        String existing = YouTubeMusicClient.getRawToken(this);
        if (!existing.isEmpty()) {
            tokenInput.setText(existing);
        }
        dialogCard.addView(tokenInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Baris Penjelasan dengan Ikon (i)
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(dp(4), dp(16), dp(4), dp(16));

        TextView infoIcon = new TextView(this);
        infoIcon.setText("i");
        infoIcon.setTextColor(Color.parseColor("#CBD5E1"));
        infoIcon.setTextSize(12);
        infoIcon.setTypeface(null, Typeface.BOLD);
        infoIcon.setGravity(Gravity.CENTER);
        infoIcon.setBackground(createGlassSurface(
                Color.TRANSPARENT, Color.parseColor("#94A3B8"), dp(11)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.setMargins(0, 0, dp(12), 0);
        infoRow.addView(infoIcon, iconParams);

        TextView infoDesc = new TextView(this);
        infoDesc.setText("Ini adalah metode login LANJUTAN. Sebagai alternatif dari portal web, Anda dapat langsung memasukkan atau memperbarui token login Anda di sini. Sebagai contoh, ini dapat mempercepat proses login di beberapa perangkat sekaligus. Perlu diperhatikan bahwa format token yang tidak valid dan gagal diproses oleh aplikasi tidak akan diterima.");
        infoDesc.setTextColor(Color.parseColor("#CBD5E1"));
        infoDesc.setTextSize(12);
        infoDesc.setLineSpacing(dp(2), 1f);
        infoRow.addView(infoDesc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        dialogCard.addView(infoRow);

        // Tombol Batal & Oke di Kanan Bawah
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setPadding(0, dp(4), dp(4), 0);

        TextView cancelBtn = new TextView(this);
        cancelBtn.setText("Batal");
        cancelBtn.setTextColor(Color.parseColor("#EF4444"));
        cancelBtn.setTextSize(15);
        cancelBtn.setTypeface(null, Typeface.BOLD);
        cancelBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        TextView okBtn = new TextView(this);
        okBtn.setText("Oke");
        okBtn.setTextColor(Color.parseColor("#EF4444"));
        okBtn.setTextSize(15);
        okBtn.setTypeface(null, Typeface.BOLD);
        okBtn.setPadding(dp(16), dp(10), dp(12), dp(10));
        okBtn.setOnClickListener(v -> {
            String raw = tokenInput.getText().toString();
            boolean valid = YouTubeMusicClient.saveToken(this, raw);
            if (!valid) {
                Toast.makeText(this, "Format token tidak valid (SAPISID tidak ditemukan)", Toast.LENGTH_LONG).show();
                return;
            }
            refreshAccountState();
            Toast.makeText(this, "Token berhasil disimpan!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnRow.addView(cancelBtn);
        btnRow.addView(okBtn);
        dialogCard.addView(btnRow);

        ScrollView scrollWrapper = new ScrollView(this);
        scrollWrapper.addView(dialogCard);
        dialog.setView(scrollWrapper);
        dialog.show();
    }

    private void refreshAccountState() {
        boolean loggedIn = YouTubeMusicClient.isLoggedIn();
        if (loggedIn) {
            profileIconBtn.setBackground(createGlassSurface(
                    Color.parseColor("#3310B981"), Color.parseColor("#34D399"), dp(21)));
            accountStatusText.setText("Terhubung via InnerTube Cookie • Aktif");
            accountStatusText.setTextColor(Color.parseColor("#34D399"));
            logoutRowBtn.setVisibility(View.VISIBLE);
        } else {
            profileIconBtn.setBackground(createGlassSurface(
                    Color.parseColor("#26FFFFFF"), Color.parseColor("#55FFFFFF"), dp(21)));
            accountStatusText.setText("Belum login • Ketuk untuk memasukkan InnerTube Cookie");
            accountStatusText.setTextColor(Color.parseColor("#94A3B8"));
            logoutRowBtn.setVisibility(View.GONE);
        }
    }

    private void openSettings() {
        settingsSheet.setVisibility(View.VISIBLE);
        settingsSheet.setAlpha(0f);
        settingsSheet.animate().alpha(1f).setDuration(220).start();
    }

    private void closeSettings() {
        settingsSheet.animate().alpha(0f).setDuration(180)
                .withEndAction(() -> settingsSheet.setVisibility(View.GONE)).start();
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(14), dp(10));
        card.setElevation(dp(16));
        card.setBackground(createGlassSurface(
                Color.parseColor("#CC181C24"), Color.parseColor("#55FFFFFF"), dp(26)));
        applySpringTouch(card);

        miniCover = new ImageView(this);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniCover.setBackgroundColor(Color.parseColor("#2A303C"));
        roundImageView(miniCover, dp(14));
        card.addView(miniCover, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setPadding(dp(12), 0, dp(8), 0);

        miniTitle = new TextView(this);
        miniTitle.setText("Pilih lagu untuk memutar");
        miniTitle.setTextColor(Color.WHITE);
        miniTitle.setTextSize(14);
        miniTitle.setTypeface(null, Typeface.BOLD);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);

        miniArtist = new TextView(this);
        miniArtist.setText("Liquid Glass Player");
        miniArtist.setTextColor(Color.parseColor("#CBD5E1"));
        miniArtist.setTextSize(12);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(TextUtils.TruncateAt.END);

        infoCol.addView(miniTitle);
        infoCol.addView(miniArtist);
        card.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        miniPlayBtn = new TextView(this);
        miniPlayBtn.setText("▶");
        miniPlayBtn.setTextColor(Color.WHITE);
        miniPlayBtn.setTextSize(18);
        miniPlayBtn.setGravity(Gravity.CENTER);
        miniPlayBtn.setBackground(createGlassSurface(
                Color.parseColor("#2EFFFFFF"), Color.parseColor("#55FFFFFF"), dp(20)));
        miniPlayBtn.setOnClickListener(v -> togglePlayPause());
        card.addView(miniPlayBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        miniNextBtn = new TextView(this);
        miniNextBtn.setText("⏭");
        miniNextBtn.setTextColor(Color.WHITE);
        miniNextBtn.setTextSize(16);
        miniNextBtn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(36), dp(40));
        nextParams.setMargins(dp(6), 0, 0, 0);
        miniNextBtn.setOnClickListener(v -> playNext());
        card.addView(miniNextBtn, nextParams);

        card.setOnClickListener(v -> openFullPlayer());

        return card;
    }

    private LinearLayout buildFullPlayerSheet() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setGravity(Gravity.CENTER_HORIZONTAL);
        sheet.setPadding(dp(26), dp(48), dp(26), dp(36));
        sheet.setClickable(true);
        sheet.setBackgroundColor(Color.parseColor("#F20B0E14"));

        View dragPill = new View(this);
        dragPill.setBackground(createGlassSurface(
                Color.parseColor("#55FFFFFF"), Color.TRANSPARENT, dp(4)));
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(dp(42), dp(5));
        pillParams.setMargins(0, 0, 0, dp(18));
        sheet.addView(dragPill, pillParams);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("⌄  Tutup");
        closeBtn.setTextColor(Color.parseColor("#E2E8F0"));
        closeBtn.setTextSize(14);
        closeBtn.setPadding(dp(14), dp(7), dp(14), dp(7));
        closeBtn.setBackground(createGlassSurface(
                Color.parseColor("#22FFFFFF"), Color.parseColor("#44FFFFFF"), dp(20)));
        closeBtn.setOnClickListener(v -> closeFullPlayer());

        topBar.addView(closeBtn);
        sheet.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout artWrapper = new FrameLayout(this);
        artWrapper.setElevation(dp(24));
        fullCover = new ImageView(this);
        fullCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fullCover.setBackgroundColor(Color.parseColor("#1E2430"));
        roundImageView(fullCover, dp(24));
        artWrapper.addView(fullCover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(290), dp(290));
        artParams.setMargins(0, dp(28), 0, dp(28));
        sheet.addView(artWrapper, artParams);

        fullTitle = new TextView(this);
        fullTitle.setText("Tidak ada lagu");
        fullTitle.setTextColor(Color.WHITE);
        fullTitle.setTextSize(22);
        fullTitle.setTypeface(null, Typeface.BOLD);
        fullTitle.setSingleLine(true);
        fullTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        fullTitle.setSelected(true);
        sheet.addView(fullTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fullArtist = new TextView(this);
        fullArtist.setText("YouTube Music");
        fullArtist.setTextColor(Color.parseColor("#94A3B8"));
        fullArtist.setTextSize(16);
        fullArtist.setSingleLine(true);
        LinearLayout.LayoutParams artistParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        artistParams.setMargins(0, dp(4), 0, dp(24));
        sheet.addView(fullArtist, artistParams);

        seekBar = new SeekBar(this);
        seekBar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        seekBar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#44FFFFFF")));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) currentTimeText.setText(formatTime(progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) { isSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isSeeking = false;
                if (mediaController != null) mediaController.seekTo(sb.getProgress());
            }
        });
        sheet.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        currentTimeText = new TextView(this);
        currentTimeText.setText("0:00");
        currentTimeText.setTextColor(Color.parseColor("#94A3B8"));
        currentTimeText.setTextSize(12);
        currentTimeText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        totalTimeText = new TextView(this);
        totalTimeText.setText("0:00");
        totalTimeText.setTextColor(Color.parseColor("#94A3B8"));
        totalTimeText.setTextSize(12);

        timeRow.addView(currentTimeText);
        timeRow.addView(totalTimeText);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.setMargins(0, dp(4), 0, dp(28));
        sheet.addView(timeRow, timeParams);

        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER);

        TextView prevBtn = new TextView(this);
        prevBtn.setText("⏮");
        prevBtn.setTextColor(Color.WHITE);
        prevBtn.setTextSize(24);
        prevBtn.setGravity(Gravity.CENTER);
        prevBtn.setBackground(createGlassSurface(
                Color.parseColor("#22FFFFFF"), Color.parseColor("#44FFFFFF"), dp(30)));
        applySpringTouch(prevBtn);
        prevBtn.setOnClickListener(v -> playPrevious());

        fullPlayBtn = new TextView(this);
        fullPlayBtn.setText("▶");
        fullPlayBtn.setTextColor(Color.BLACK);
        fullPlayBtn.setTextSize(28);
        fullPlayBtn.setGravity(Gravity.CENTER);
        fullPlayBtn.setBackground(createGlassSurface(
                Color.WHITE, Color.parseColor("#AAFFFFFF"), dp(38)));
        applySpringTouch(fullPlayBtn);
        fullPlayBtn.setOnClickListener(v -> togglePlayPause());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        playParams.setMargins(dp(28), 0, dp(28), 0);

        TextView nextBtn = new TextView(this);
        nextBtn.setText("⏭");
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setTextSize(24);
        nextBtn.setGravity(Gravity.CENTER);
        nextBtn.setBackground(createGlassSurface(
                Color.parseColor("#22FFFFFF"), Color.parseColor("#44FFFFFF"), dp(30)));
        applySpringTouch(nextBtn);
        nextBtn.setOnClickListener(v -> playNext());

        controlsRow.addView(prevBtn, new LinearLayout.LayoutParams(dp(60), dp(60)));
        controlsRow.addView(fullPlayBtn, playParams);
        controlsRow.addView(nextBtn, new LinearLayout.LayoutParams(dp(60), dp(60)));
        sheet.addView(controlsRow);

        return sheet;
    }

    private void performSearch(String query) {
        if (query.isEmpty()) return;
        executor.execute(() -> {
            try {
                List<YouTubeMusicClient.Song> results = YouTubeMusicClient.searchSongs(query);
                uiHandler.post(() -> {
                    songList.clear();
                    songList.addAll(results);
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(this, "Gagal mencari: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void playSongAt(int index) {
        if (index < 0 || index >= songList.size()) return;
        currentIndex = index;
        YouTubeMusicClient.Song song = songList.get(index);

        miniTitle.setText("Memuat: " + song.title);
        miniArtist.setText(song.artist);
        fullTitle.setText(song.title);
        fullArtist.setText(song.artist);
        adapter.notifyDataSetChanged();

        Glide.with(this).asBitmap().load(song.thumbnail).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                miniCover.setImageBitmap(resource);
                fullCover.setImageBitmap(resource);
                Palette.from(resource).generate(palette -> {
                    if (palette != null) {
                        int vibrant = palette.getDarkVibrantColor(
                                palette.getDominantColor(Color.parseColor("#1E3A5F")));
                        animateAmbientColor(currentAccentColor, vibrant);
                        currentAccentColor = vibrant;
                    }
                });
            }
            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {}
        });

        executor.execute(() -> {
            try {
                YouTubeMusicClient.StreamResult stream = YouTubeMusicClient.getAudioStream(song.videoId);
                if (stream == null || stream.url == null) {
                    uiHandler.post(() -> {
                        miniTitle.setText("Gagal memuat: " + song.title);
                        Toast.makeText(this, "Stream tidak tersedia untuk lagu ini", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                String streamUrl = stream.url;

                uiHandler.post(() -> {
                    if (mediaController == null) return;
                    MediaMetadata metadata = new MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(Uri.parse(song.thumbnail))
                            .build();

                    MediaItem item = new MediaItem.Builder()
                            .setUri(streamUrl)
                            .setMediaMetadata(metadata)
                            .build();

                    mediaController.setMediaItem(item);
                    mediaController.prepare();
                    mediaController.play();
                    miniTitle.setText(song.title);
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    miniTitle.setText("Error: " + song.title);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void togglePlayPause() {
        if (mediaController == null) return;
        if (mediaController.isPlaying()) mediaController.pause();
        else mediaController.play();
    }

    private void playNext() {
        if (!songList.isEmpty()) {
            playSongAt((currentIndex + 1) % songList.size());
        }
    }

    private void playPrevious() {
        if (!songList.isEmpty()) {
            int prev = (currentIndex - 1 < 0) ? songList.size() - 1 : currentIndex - 1;
            playSongAt(prev);
        }
    }

    private void openFullPlayer() {
        fullPlayerSheet.setVisibility(View.VISIBLE);
        fullPlayerSheet.setTranslationY(rootFrame.getHeight());
        fullPlayerSheet.animate()
                .translationY(0)
                .setDuration(380)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();
    }

    private void closeFullPlayer() {
        fullPlayerSheet.animate()
                .translationY(rootFrame.getHeight())
                .setDuration(280)
                .withEndAction(() -> fullPlayerSheet.setVisibility(View.GONE))
                .start();
    }

    private void initMediaController() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(this, token).buildAsync();
        future.addListener(() -> {
            try {
                mediaController = future.get();
                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        String icon = isPlaying ? "⏸" : "▶";
                        miniPlayBtn.setText(icon);
                        fullPlayBtn.setText(icon);
                    }

                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_ENDED) playNext();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void startProgressUpdater() {
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaController != null && !isSeeking) {
                    long duration = mediaController.getDuration();
                    long pos = mediaController.getCurrentPosition();
                    if (duration > 0) {
                        seekBar.setMax((int) duration);
                        seekBar.setProgress((int) pos);
                        currentTimeText.setText(formatTime(pos));
                        totalTimeText.setText(formatTime(duration));
                    }
                }
                uiHandler.postDelayed(this, 500);
            }
        }, 500);
    }

    private void updateAmbientColors(int topColor) {
        GradientDrawable grad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topColor, Color.parseColor("#0D1117"), Color.parseColor("#090B0E")});
        ambientBackdrop.setBackground(grad);

        if (fullPlayerSheet != null) {
            GradientDrawable sheetGrad = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{topColor, Color.parseColor("#090B0E")});
            fullPlayerSheet.setBackground(sheetGrad);
        }
    }

    private void animateAmbientColor(int fromColor, int toColor) {
        ValueAnimator animator = ValueAnimator.ofArgb(fromColor, toColor);
        animator.setDuration(600);
        animator.addUpdateListener(anim -> updateAmbientColors((int) anim.getAnimatedValue()));
        animator.start();
    }

    private GradientDrawable createGlassSurface(int fillColor, int strokeColor, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radiusPx);
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private void applySpringTouch(View view) {
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(110).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(240)
                        .setInterpolator(new OvershootInterpolator(1.5f)).start();
            }
            return false;
        });
    }

    private void roundImageView(ImageView iv, int radiusPx) {
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        iv.setClipToOutline(true);
    }

    private String formatTime(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format(Locale.getDefault(), "%d:%02d", min, sec);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (fullPlayerSheet.getVisibility() == View.VISIBLE) {
            closeFullPlayer();
        } else if (settingsSheet.getVisibility() == View.VISIBLE) {
            closeSettings();
        } else {
            super.onBackPressed();
        }
    }

    private class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(10), dp(14), dp(10));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(10));
            card.setLayoutParams(params);
            applySpringTouch(card);

            ImageView thumb = new ImageView(parent.getContext());
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(Color.parseColor("#1E2430"));
            roundImageView(thumb, dp(12));
            card.addView(thumb, new LinearLayout.LayoutParams(dp(52), dp(52)));

            LinearLayout textCol = new LinearLayout(parent.getContext());
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(14), 0, 0, 0);

            TextView title = new TextView(parent.getContext());
            title.setTextColor(Color.WHITE);
            title.setTextSize(15);
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);

            TextView artist = new TextView(parent.getContext());
            artist.setTextColor(Color.parseColor("#94A3B8"));
            artist.setTextSize(13);
            artist.setSingleLine(true);
            artist.setEllipsize(TextUtils.TruncateAt.END);

            textCol.addView(title);
            textCol.addView(artist);
            card.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            return new ViewHolder(card, thumb, title, artist);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            YouTubeMusicClient.Song song = songList.get(position);
            boolean isCurrent = (position == currentIndex);

            holder.card.setBackground(createGlassSurface(
                    isCurrent ? Color.parseColor("#33FFFFFF") : Color.parseColor("#14FFFFFF"),
                    isCurrent ? Color.parseColor("#66FFFFFF") : Color.parseColor("#22FFFFFF"),
                    dp(20)));
            holder.title.setTextColor(isCurrent ? Color.parseColor("#34D399") : Color.WHITE);
            holder.title.setText(song.title);
            holder.artist.setText(song.artist);

            Glide.with(holder.thumb.getContext())
                    .load(song.thumbnail)
                    .into(holder.thumb);

            holder.card.setOnClickListener(v -> playSongAt(position));
        }

        @Override
        public int getItemCount() {
            return songList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout card;
            ImageView thumb;
            TextView title, artist;

            ViewHolder(LinearLayout card, ImageView thumb, TextView title, TextView artist) {
                super(card);
                this.card = card;
                this.thumb = thumb;
                this.title = title;
                this.artist = artist;
            }
        }
    }
}
