package com.rulsz.music;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.HorizontalScrollView;
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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private MediaController mediaController;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final List<YouTubeMusicClient.Song> activeQueue = new ArrayList<>();
    private final List<YouTubeMusicClient.Song> searchResults = new ArrayList<>();
    private SongListAdapter searchAdapter;
    private int currentIndex = -1;
    private int currentAccentColor = Color.parseColor("#18181B");

    private FrameLayout rootFrame;
    private View ambientBackdrop;

    private ScrollView feedScrollView;
    private LinearLayout feedContainer;
    private LinearLayout searchOverlay;
    private LinearLayout playlistDetailOverlay;
    private LinearLayout playlistSongsContainer;
    private TextView playlistHeaderTitle;

    // Bottom Dock (Expanded with Draggable Liquid Glass vs Compact Scroll Mode)
    private LinearLayout expandedDock;
    private LinearLayout compactDock;
    private LiquidGlassNavBar liquidNavBar;
    private boolean isCompactDockMode = false;

    // Profile & Settings
    private ImageView profileIconBtn;
    private LinearLayout settingsSheet;
    private TextView accountStatusText;
    private TextView logoutRowBtn;

    // Mini Player Views (Expanded & Compact)
    private ImageView miniCover, miniPlayBtn, miniNextBtn;
    private TextView miniTitle, miniArtist;
    private ImageView compactCover, compactPlayBtn;
    private TextView compactTitle;

    // Full Screen Now Playing Views
    private LinearLayout fullPlayerSheet;
    private ImageView fullCover, fullPlayBtn;
    private TextView fullTitle, fullArtist, currentTimeText, totalTimeText;
    private SeekBar seekBar;
    private boolean isSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YouTubeMusicClient.init(this);

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.parseColor("#050507"));
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(Color.parseColor("#050507"));

        ambientBackdrop = new View(this);
        updateAmbientColors(Color.parseColor("#141218"));
        rootFrame.addView(ambientBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        feedScrollView = new ScrollView(this);
        feedScrollView.setVerticalScrollBarEnabled(false);
        feedContainer = new LinearLayout(this);
        feedContainer.setOrientation(LinearLayout.VERTICAL);
        feedContainer.setPadding(dp(18), dp(46), dp(18), dp(170));
        feedScrollView.addView(feedContainer);
        rootFrame.addView(feedScrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Deteksi Scroll untuk Morphing Navbar persis seperti di video (0:00 -> 0:02)
        feedScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > dp(45) && !isCompactDockMode) {
                switchToCompactDock(true);
            } else if (scrollY <= dp(30) && isCompactDockMode) {
                switchToCompactDock(false);
            }
        });

        searchOverlay = buildSearchOverlay();
        searchOverlay.setVisibility(View.GONE);
        rootFrame.addView(searchOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        playlistDetailOverlay = buildPlaylistDetailOverlay();
        playlistDetailOverlay.setVisibility(View.GONE);
        rootFrame.addView(playlistDetailOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Expanded Bottom Dock (Mini Player + Draggable Liquid Glass Nav)
        expandedDock = buildExpandedDock();
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockParams.gravity = Gravity.BOTTOM;
        dockParams.setMargins(dp(14), 0, dp(14), dp(16));
        rootFrame.addView(expandedDock, dockParams);

        // Compact Scroll Dock ([Home] [Cover + Judul + Play] [Search])
        compactDock = buildCompactDock();
        compactDock.setVisibility(View.GONE);
        FrameLayout.LayoutParams compactParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        compactParams.gravity = Gravity.BOTTOM;
        compactParams.setMargins(dp(14), 0, dp(14), dp(16));
        rootFrame.addView(compactDock, compactParams);

        settingsSheet = buildSettingsSheet();
        settingsSheet.setVisibility(View.GONE);
        rootFrame.addView(settingsSheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fullPlayerSheet = buildFullPlayerSheet();
        fullPlayerSheet.setVisibility(View.GONE);
        rootFrame.addView(fullPlayerSheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(rootFrame);
        refreshAccountState();
        initMediaController();
        startProgressUpdater();

        if (YouTubeMusicClient.isLoggedIn()) {
            executor.execute(() -> {
                YouTubeMusicClient.fetchAccountProfile(this);
                uiHandler.post(this::refreshAccountState);
            });
        }

        loadFeedTab("HOME", "FEmusic_home", "");
    }

    // Custom Draggable Liquid Glass Navigation Bar (Persis seperti detik 0:03 - 0:06 di video)
    private class LiquidGlassNavBar extends FrameLayout {
        private final List<ImageView> tabIcons = new ArrayList<>();
        private final GlassLensView glassLens;
        private int selectedIndex = 0;
        private float currentLensX = 0f;
        private float lensScale = 1.0f;
        private boolean isDragging = false;
        private float downX = 0f;
        private float startLensX = 0f;
        private ValueAnimator snapAnimator;
        private ValueAnimator scaleAnimator;

        private final int activeNeonGreen = Color.parseColor("#22C55E");
        private final int inactiveWhite = Color.parseColor("#E2E8F0");

        public LiquidGlassNavBar(Context context, int[] iconResIds) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
            setBackground(createGlassSurface(
                    Color.parseColor("#EB15161B"), Color.parseColor("#38FFFFFF"), dp(34)));

            // 1. Lensa Liquid Glass yang bisa digeser-geser
            glassLens = new GlassLensView(context);
            addView(glassLens, new LayoutParams(dp(76), dp(50)));

            // 2. Baris 4 Ikon Tab (Home, Explore, Library, Settings/Create)
            LinearLayout iconsRow = new LinearLayout(context);
            iconsRow.setOrientation(LinearLayout.HORIZONTAL);
            iconsRow.setGravity(Gravity.CENTER);

            for (int i = 0; i < iconResIds.length; i++) {
                FrameLayout slot = new FrameLayout(context);
                ImageView iv = new ImageView(context);
                iv.setImageResource(iconResIds[i]);
                iv.setColorFilter(i == 0 ? activeNeonGreen : inactiveWhite);
                LayoutParams ivLp = new LayoutParams(dp(24), dp(24));
                ivLp.gravity = Gravity.CENTER;
                slot.addView(iv, ivLp);
                tabIcons.add(iv);

                iconsRow.addView(slot, new LinearLayout.LayoutParams(0, dp(62), 1f));
            }
            addView(iconsRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (!isDragging && getWidth() > 0 && !tabIcons.isEmpty()) {
                float slotW = (float) getWidth() / tabIcons.size();
                int lensW = (int) (slotW * 0.88f);
                if (glassLens.getLayoutParams().width != lensW) {
                    glassLens.getLayoutParams().width = lensW;
                    glassLens.requestLayout();
                }
                currentLensX = getTargetXForIndex(selectedIndex);
                glassLens.setTranslationX(currentLensX);
                glassLens.setTranslationY((getHeight() - glassLens.getHeight()) / 2f);
            }
        }

        private float getTargetXForIndex(int index) {
            if (tabIcons.isEmpty()) return 0f;
            float slotW = (float) getWidth() / tabIcons.size();
            float lensW = glassLens.getWidth() > 0 ? glassLens.getWidth() : (slotW * 0.88f);
            return (index * slotW) + (slotW - lensW) / 2f;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return true; // Tangkap semua sentuhan & geseran secara halus
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (getWidth() == 0 || tabIcons.isEmpty()) return false;
            float slotW = (float) getWidth() / tabIcons.size();
            float maxLensX = getWidth() - glassLens.getWidth() - dp(4);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (snapAnimator != null) snapAnimator.cancel();
                    isDragging = true;
                    downX = event.getX();
                    startLensX = currentLensX;
                    animateLensScale(1.14f); // Membesar seperti tetesan liquid glass saat disentuh
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - downX;
                    // Jika user hanya tap ringan di tab lain, geser ke posisi jari
                    float rawX = Math.abs(dx) > dp(6)
                            ? (startLensX + dx)
                            : (event.getX() - glassLens.getWidth() / 2f);
                    currentLensX = Math.max(dp(4), Math.min(maxLensX, rawX));
                    glassLens.setTranslationX(currentLensX);
                    updateIconHighlightsByLensPosition();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    animateLensScale(1.0f);
                    float center = currentLensX + (glassLens.getWidth() / 2f);
                    int targetIdx = Math.max(0, Math.min(tabIcons.size() - 1, (int) (center / slotW)));
                    selectTab(targetIdx, true);
                    return true;
            }
            return super.onTouchEvent(event);
        }

        public void selectTab(int index, boolean triggerAction) {
            selectedIndex = index;
            float targetX = getTargetXForIndex(index);
            if (snapAnimator != null) snapAnimator.cancel();
            snapAnimator = ValueAnimator.ofFloat(currentLensX, targetX);
            snapAnimator.setDuration(320);
            snapAnimator.setInterpolator(new OvershootInterpolator(1.35f));
            snapAnimator.addUpdateListener(anim -> {
                currentLensX = (float) anim.getAnimatedValue();
                glassLens.setTranslationX(currentLensX);
                updateIconHighlightsByLensPosition();
            });
            snapAnimator.start();

            if (triggerAction) {
                onNavTabSelected(index);
            }
        }

        private void animateLensScale(float targetScale) {
            if (scaleAnimator != null) scaleAnimator.cancel();
            scaleAnimator = ValueAnimator.ofFloat(lensScale, targetScale);
            scaleAnimator.setDuration(180);
            scaleAnimator.addUpdateListener(anim -> {
                lensScale = (float) anim.getAnimatedValue();
                glassLens.setScaleX(lensScale);
                glassLens.setScaleY(lensScale);
            });
            scaleAnimator.start();
        }

        private void updateIconHighlightsByLensPosition() {
            float lensCenter = currentLensX + glassLens.getWidth() / 2f;
            float slotW = (float) getWidth() / tabIcons.size();
            for (int i = 0; i < tabIcons.size(); i++) {
                float iconCenter = (i + 0.5f) * slotW;
                float dist = Math.abs(lensCenter - iconCenter);
                if (dist < slotW * 0.45f) {
                    tabIcons.get(i).setColorFilter(activeNeonGreen);
                    tabIcons.get(i).setScaleX(1.08f);
                    tabIcons.get(i).setScaleY(1.08f);
                } else {
                    tabIcons.get(i).setColorFilter(inactiveWhite);
                    tabIcons.get(i).setScaleX(1.0f);
                    tabIcons.get(i).setScaleY(1.0f);
                }
            }
        }
    }

    // Custom Canvas View untuk Efek Refraksi Liquid Glass (Specular Rim + Inner Glow)
    private class GlassLensView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public GlassLensView(Context context) {
            super(context);
            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(dp(1.4f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float r = h / 2f;
            rect.set(dp(1), dp(1), w - dp(1), h - dp(1));

            // 1. Pendaran cairan hijau/kaca halus di bagian bawah lensa
            glowPaint.setShader(new RadialGradient(
                    w * 0.5f, h * 0.85f, w * 0.65f,
                    new int[]{Color.parseColor("#3822C55E"), Color.parseColor("#18FFFFFF"), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, r, r, glowPaint);

            // 2. Permukaan kaca translucent
            fillPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    Color.parseColor("#2EFFFFFF"), Color.parseColor("#12FFFFFF"), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, r, r, fillPaint);

            // 3. Specular Refractive Border (cahaya putih tajam di atas & bawah kapsul)
            rimPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    new int[]{Color.parseColor("#AAFFFFFF"), Color.parseColor("#22FFFFFF"), Color.parseColor("#77FFFFFF")},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, r, r, rimPaint);
        }
    }

    private void onNavTabSelected(int index) {
        if (index == 0) {
            loadFeedTab("HOME", "FEmusic_home", "");
        } else if (index == 1) {
            playlistDetailOverlay.setVisibility(View.GONE);
            searchOverlay.setVisibility(View.VISIBLE);
        } else if (index == 2) {
            loadFeedTab("LIBRARY", "FEmusic_liked_playlists", "");
        } else if (index == 3) {
            loadFeedTab("EXPLORE", "FEmusic_explore", "");
        }
    }

    private void switchToCompactDock(boolean compact) {
        isCompactDockMode = compact;
        if (compact) {
            compactDock.setVisibility(View.VISIBLE);
            compactDock.setAlpha(0f);
            compactDock.setScaleX(0.92f);
            compactDock.setScaleY(0.92f);
            compactDock.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(260).setInterpolator(new OvershootInterpolator(1.1f)).start();

            expandedDock.animate().alpha(0f).translationY(dp(24)).setDuration(180)
                    .withEndAction(() -> expandedDock.setVisibility(View.GONE)).start();
        } else {
            expandedDock.setVisibility(View.VISIBLE);
            expandedDock.setAlpha(0f);
            expandedDock.setTranslationY(dp(24));
            expandedDock.animate().alpha(1f).translationY(0)
                    .setDuration(260).setInterpolator(new OvershootInterpolator(1.1f)).start();

            compactDock.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(180)
                    .withEndAction(() -> compactDock.setVisibility(View.GONE)).start();
        }
    }

    private ImageView createSvgIcon(int resId, int sizeDp, int tintColor) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tintColor != 0) {
            iv.setColorFilter(tintColor);
        }
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    private String getDynamicGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 0 && hour < 5) return "Up late, RZ";
        if (hour < 12) return "Good morning, RZ";
        if (hour < 18) return "Good afternoon, RZ";
        return "Good evening, RZ";
    }

    private void buildFeedHeader(String subtitleHeader) {
        feedContainer.removeAllViews();

        LinearLayout topIcons = new LinearLayout(this);
        topIcons.setOrientation(LinearLayout.HORIZONTAL);
        topIcons.setGravity(Gravity.CENTER_VERTICAL);
        topIcons.setPadding(0, 0, 0, dp(12));

        ImageView waveLogo = createSvgIcon(R.drawable.ic_convx_logo, 34, 0);
        FrameLayout logoHolder = new FrameLayout(this);
        logoHolder.addView(waveLogo);
        logoHolder.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        profileIconBtn = new ImageView(this);
        profileIconBtn.setScaleType(ImageView.ScaleType.CENTER_CROP);
        roundView(profileIconBtn, dp(22));
        applySpringTouch(profileIconBtn);
        profileIconBtn.setOnClickListener(v -> openSettings());

        topIcons.addView(logoHolder);
        topIcons.addView(profileIconBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        feedContainer.addView(topIcons);
        refreshAccountState();

        TextView bigTitle = new TextView(this);
        bigTitle.setText(subtitleHeader);
        bigTitle.setTextColor(Color.WHITE);
        bigTitle.setTextSize(30);
        bigTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        bigTitle.setPadding(0, 0, 0, dp(18));
        feedContainer.addView(bigTitle);
    }

    private void loadFeedTab(String tabName, String browseId, String params) {
        searchOverlay.setVisibility(View.GONE);
        playlistDetailOverlay.setVisibility(View.GONE);
        feedScrollView.setVisibility(View.VISIBLE);
        feedScrollView.scrollTo(0, 0);
        if (isCompactDockMode) switchToCompactDock(false);

        String headerTitle = tabName.equals("HOME") ? getDynamicGreeting()
                : (tabName.equals("EXPLORE") ? "Explore" : "Library");
        buildFeedHeader(headerTitle);

        TextView loadingTv = new TextView(this);
        loadingTv.setText("Memuat katalog YouTube Music...");
        loadingTv.setTextColor(Color.parseColor("#94A3B8"));
        loadingTv.setTextSize(14);
        feedContainer.addView(loadingTv);

        executor.execute(() -> {
            try {
                List<YouTubeMusicClient.HomeSection> sections = YouTubeMusicClient.getBrowseSections(browseId, params);
                uiHandler.post(() -> {
                    feedContainer.removeView(loadingTv);
                    if (sections.isEmpty()) {
                        TextView emptyTv = new TextView(this);
                        emptyTv.setText("Tidak ada data. Ketuk ikon profil kanan atas untuk login dengan token.");
                        emptyTv.setTextColor(Color.parseColor("#94A3B8"));
                        feedContainer.addView(emptyTv);
                        return;
                    }

                    for (int sIdx = 0; sIdx < sections.size(); sIdx++) {
                        YouTubeMusicClient.HomeSection sec = sections.get(sIdx);
                        boolean isMoodSection = !sec.items.isEmpty() && sec.items.get(0).isMood;
                        addSectionHeader(sec.strapline, sec.title, isMoodSection || sec.items.size() > 4);

                        if (isMoodSection) {
                            addMoodCardsRow(sec.items);
                        } else if (tabName.equals("HOME") && sIdx == 0) {
                            addHeroCardsRow(sec.items);
                        } else if (tabName.equals("HOME") && sIdx == 1) {
                            addStackedSongGrid(sec.items);
                        } else {
                            addSquareCardsRow(sec.items);
                        }
                    }
                });
            } catch (Exception e) {
                uiHandler.post(() -> loadingTv.setText("Gagal memuat: " + e.getMessage()));
            }
        });
    }

    private void addSectionHeader(String strapline, String title, boolean showSeeAll) {
        if (strapline != null && !strapline.isEmpty()) {
            TextView strapTv = new TextView(this);
            strapTv.setText(strapline.toUpperCase(Locale.getDefault()));
            strapTv.setTextColor(Color.parseColor("#94A3B8"));
            strapTv.setTextSize(11);
            strapTv.setTypeface(null, Typeface.BOLD);
            strapTv.setPadding(0, dp(10), 0, dp(2));
            feedContainer.addView(strapTv);
        }

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, (strapline == null || strapline.isEmpty()) ? dp(12) : 0, 0, dp(12));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(20);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(titleTv);

        if (showSeeAll) {
            TextView seeAllPill = new TextView(this);
            seeAllPill.setText("Lihat semua ›");
            seeAllPill.setTextColor(Color.parseColor("#E2E8F0"));
            seeAllPill.setTextSize(12);
            seeAllPill.setTypeface(null, Typeface.BOLD);
            seeAllPill.setPadding(dp(12), dp(5), dp(12), dp(5));
            seeAllPill.setBackground(createGlassSurface(
                    Color.parseColor("#1FFFFFFF"), Color.parseColor("#38FFFFFF"), dp(16)));
            headerRow.addView(seeAllPill);
        }

        feedContainer.addView(headerRow);
    }

    private void addMoodCardsRow(List<YouTubeMusicClient.Song> items) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        int[][] paletteGradients = {
                {Color.parseColor("#00C853"), Color.parseColor("#007E33")},
                {Color.parseColor("#0284C7"), Color.parseColor("#0369A1")},
                {Color.parseColor("#4F46E5"), Color.parseColor("#312E81")},
                {Color.parseColor("#E11D48"), Color.parseColor("#9F1239")},
                {Color.parseColor("#D97706"), Color.parseColor("#92400E")}
        };

        for (int i = 0; i < items.size(); i++) {
            YouTubeMusicClient.Song mood = items.get(i);
            FrameLayout card = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(156), dp(112));
            lp.setMargins(0, 0, dp(12), dp(16));
            card.setLayoutParams(lp);
            applySpringTouch(card);

            int[] colors = paletteGradients[i % paletteGradients.length];
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
            bg.setCornerRadius(dp(18));
            card.setBackground(bg);

            ImageView cornerWave = createSvgIcon(R.drawable.ic_convx_logo, 20, Color.WHITE);
            FrameLayout.LayoutParams waveLp = new FrameLayout.LayoutParams(dp(20), dp(20));
            waveLp.gravity = Gravity.TOP | Gravity.END;
            waveLp.setMargins(0, dp(12), dp(12), 0);
            card.addView(cornerWave, waveLp);

            TextView moodLabel = new TextView(this);
            moodLabel.setText(mood.title);
            moodLabel.setTextColor(Color.WHITE);
            moodLabel.setTextSize(15);
            moodLabel.setTypeface(null, Typeface.BOLD);
            FrameLayout.LayoutParams lblLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lblLp.gravity = Gravity.BOTTOM | Gravity.START;
            lblLp.setMargins(dp(14), 0, dp(12), dp(12));
            card.addView(moodLabel, lblLp);

            card.setOnClickListener(v -> openPlaylistDetail(mood));
            row.addView(card);
        }

        hsv.addView(row);
        feedContainer.addView(hsv);
    }

    private void addHeroCardsRow(List<YouTubeMusicClient.Song> items) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (YouTubeMusicClient.Song item : items) {
            FrameLayout card = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(235), dp(315));
            lp.setMargins(0, 0, dp(14), dp(14));
            card.setLayoutParams(lp);
            applySpringTouch(card);

            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackgroundColor(Color.parseColor("#1E293B"));
            roundView(img, dp(22));
            Glide.with(this).load(item.thumbnail).into(img);
            card.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            View scrim = new View(this);
            GradientDrawable scrimGrad = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, Color.parseColor("#66000000"), Color.parseColor("#EE000000")});
            scrimGrad.setCornerRadius(dp(22));
            scrim.setBackground(scrimGrad);
            card.addView(scrim, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setGravity(Gravity.BOTTOM);
            textBox.setPadding(dp(16), dp(16), dp(16), dp(18));

            TextView sub = new TextView(this);
            sub.setText(item.subtitle);
            sub.setTextColor(Color.parseColor("#CBD5E1"));
            sub.setTextSize(12);
            sub.setSingleLine(true);

            TextView tit = new TextView(this);
            tit.setText(item.title);
            tit.setTextColor(Color.WHITE);
            tit.setTextSize(19);
            tit.setTypeface(null, Typeface.BOLD);
            tit.setMaxLines(2);
            tit.setEllipsize(TextUtils.TruncateAt.END);

            textBox.addView(sub);
            textBox.addView(tit);
            card.addView(textBox, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            card.setOnClickListener(v -> handleItemClick(item, items));
            row.addView(card);
        }

        hsv.addView(row);
        feedContainer.addView(hsv);
    }

    private void addStackedSongGrid(List<YouTubeMusicClient.Song> items) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout columnsRow = new LinearLayout(this);
        columnsRow.setOrientation(LinearLayout.HORIZONTAL);

        int rowsPerCol = 4;
        for (int i = 0; i < items.size(); i += rowsPerCol) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT);
            colParams.setMargins(0, 0, dp(16), dp(12));
            col.setLayoutParams(colParams);

            for (int r = 0; r < rowsPerCol && (i + r) < items.size(); r++) {
                YouTubeMusicClient.Song song = items.get(i + r);
                col.addView(createCompactSongRow(song, items));
            }
            columnsRow.addView(col);
        }

        hsv.addView(columnsRow);
        feedContainer.addView(hsv);
    }

    private LinearLayout createCompactSongRow(YouTubeMusicClient.Song song, List<YouTubeMusicClient.Song> contextList) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), dp(6), dp(6));
        applySpringTouch(row);

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(Color.parseColor("#1E2430"));
        roundView(thumb, dp(10));
        Glide.with(this).load(song.thumbnail).into(thumb);
        row.addView(thumb, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);

        TextView tit = new TextView(this);
        tit.setText(song.title);
        tit.setTextColor(Color.WHITE);
        tit.setTextSize(15);
        tit.setTypeface(null, Typeface.BOLD);
        tit.setSingleLine(true);
        tit.setEllipsize(TextUtils.TruncateAt.END);

        TextView sub = new TextView(this);
        sub.setText(song.subtitle);
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setTextSize(13);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);

        info.addView(tit);
        info.addView(sub);
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView dots = createSvgIcon(R.drawable.ic_more_vert, 20, Color.parseColor("#94A3B8"));
        row.addView(dots);

        row.setOnClickListener(v -> handleItemClick(song, contextList));
        return row;
    }

    private void addSquareCardsRow(List<YouTubeMusicClient.Song> items) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (YouTubeMusicClient.Song item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(152), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(14), dp(16));
            card.setLayoutParams(lp);
            applySpringTouch(card);

            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackgroundColor(Color.parseColor("#1E2430"));
            roundView(img, dp(16));
            Glide.with(this).load(item.thumbnail).into(img);
            card.addView(img, new LinearLayout.LayoutParams(dp(152), dp(152)));

            TextView tit = new TextView(this);
            tit.setText(item.title);
            tit.setTextColor(Color.WHITE);
            tit.setTextSize(14);
            tit.setTypeface(null, Typeface.BOLD);
            tit.setSingleLine(true);
            tit.setEllipsize(TextUtils.TruncateAt.END);
            tit.setPadding(0, dp(8), 0, dp(2));

            TextView sub = new TextView(this);
            sub.setText(item.subtitle);
            sub.setTextColor(Color.parseColor("#94A3B8"));
            sub.setTextSize(12);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);

            card.addView(tit);
            card.addView(sub);
            card.setOnClickListener(v -> handleItemClick(item, items));
            row.addView(card);
        }

        hsv.addView(row);
        feedContainer.addView(hsv);
    }

    private void handleItemClick(YouTubeMusicClient.Song item, List<YouTubeMusicClient.Song> parentList) {
        if (item.isPlaylist || item.videoId.isEmpty()) {
            openPlaylistDetail(item);
        } else {
            activeQueue.clear();
            for (YouTubeMusicClient.Song s : parentList) {
                if (!s.videoId.isEmpty()) activeQueue.add(s);
            }
            int idx = activeQueue.indexOf(item);
            if (idx < 0) {
                activeQueue.add(0, item);
                idx = 0;
            }
            playSongAt(idx);
        }
    }

    private LinearLayout buildPlaylistDetailOverlay() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(48), dp(18), 0);
        sheet.setClickable(true);
        sheet.setBackgroundColor(Color.parseColor("#FA07080B"));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(16));

        ImageView backBtn = createSvgIcon(R.drawable.ic_back, 28, Color.WHITE);
        backBtn.setPadding(0, 0, dp(10), 0);
        backBtn.setOnClickListener(v -> playlistDetailOverlay.setVisibility(View.GONE));

        playlistHeaderTitle = new TextView(this);
        playlistHeaderTitle.setText("Playlist");
        playlistHeaderTitle.setTextColor(Color.WHITE);
        playlistHeaderTitle.setTextSize(20);
        playlistHeaderTitle.setTypeface(null, Typeface.BOLD);
        playlistHeaderTitle.setSingleLine(true);

        topBar.addView(backBtn);
        topBar.addView(playlistHeaderTitle);
        sheet.addView(topBar);

        ScrollView sv = new ScrollView(this);
        playlistSongsContainer = new LinearLayout(this);
        playlistSongsContainer.setOrientation(LinearLayout.VERTICAL);
        playlistSongsContainer.setPadding(0, 0, 0, dp(170));
        sv.addView(playlistSongsContainer);
        sheet.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return sheet;
    }

    private void openPlaylistDetail(YouTubeMusicClient.Song playlistItem) {
        playlistHeaderTitle.setText(playlistItem.title);
        playlistSongsContainer.removeAllViews();
        playlistDetailOverlay.setVisibility(View.VISIBLE);

        TextView loading = new TextView(this);
        loading.setText("Memuat daftar lagu...");
        loading.setTextColor(Color.parseColor("#94A3B8"));
        playlistSongsContainer.addView(loading);

        executor.execute(() -> {
            try {
                List<YouTubeMusicClient.HomeSection> secs = YouTubeMusicClient.getBrowseSections(
                        playlistItem.browseId, playlistItem.browseParams);
                List<YouTubeMusicClient.Song> tracks = new ArrayList<>();
                for (YouTubeMusicClient.HomeSection s : secs) {
                    for (YouTubeMusicClient.Song song : s.items) {
                        if (!song.videoId.isEmpty()) tracks.add(song);
                    }
                }
                uiHandler.post(() -> {
                    playlistSongsContainer.removeAllViews();
                    if (tracks.isEmpty()) {
                        loading.setText("Tidak ada lagu ditemukan.");
                        playlistSongsContainer.addView(loading);
                        return;
                    }
                    for (YouTubeMusicClient.Song t : tracks) {
                        playlistSongsContainer.addView(createCompactSongRow(t, tracks));
                    }
                });
            } catch (Exception e) {
                uiHandler.post(() -> loading.setText("Error: " + e.getMessage()));
            }
        });
    }

    private LinearLayout buildSearchOverlay() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(48), dp(18), 0);
        container.setClickable(true);
        container.setBackgroundColor(Color.parseColor("#F507080B"));

        LinearLayout searchPill = new LinearLayout(this);
        searchPill.setOrientation(LinearLayout.HORIZONTAL);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setPadding(dp(14), dp(4), dp(8), dp(4));
        searchPill.setBackground(createGlassSurface(
                Color.parseColor("#24FFFFFF"), Color.parseColor("#4DFFFFFF"), dp(28)));

        ImageView searchIconLead = createSvgIcon(R.drawable.ic_search, 20, Color.parseColor("#94A3B8"));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconLp.setMargins(0, 0, dp(10), 0);
        searchPill.addView(searchIconLead, iconLp);

        EditText searchInput = new EditText(this);
        searchInput.setHint("Cari lagu, artis, atau album di YT Music...");
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
        searchBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        searchBtn.setBackground(createGlassSurface(
                Color.parseColor("#38FFFFFF"), Color.parseColor("#66FFFFFF"), dp(22)));

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

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setClipToPadding(false);
        rv.setPadding(0, dp(14), 0, dp(170));
        searchAdapter = new SongListAdapter();
        rv.setAdapter(searchAdapter);
        container.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return container;
    }

    private void performSearch(String query) {
        if (query.isEmpty()) return;
        Toast.makeText(this, "Mencari...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                List<YouTubeMusicClient.Song> results = YouTubeMusicClient.searchSongs(query);
                uiHandler.post(() -> {
                    searchResults.clear();
                    searchResults.addAll(results);
                    searchAdapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(this, "Gagal mencari: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // Dock Bawah Mode Normal (Mini Player + Draggable Liquid Glass NavBar)
    private LinearLayout buildExpandedDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setClipChildren(false);

        LinearLayout miniPlayerCard = new LinearLayout(this);
        miniPlayerCard.setOrientation(LinearLayout.HORIZONTAL);
        miniPlayerCard.setGravity(Gravity.CENTER_VERTICAL);
        miniPlayerCard.setPadding(dp(10), dp(8), dp(12), dp(8));
        miniPlayerCard.setElevation(dp(14));
        miniPlayerCard.setBackground(createGlassSurface(
                Color.parseColor("#E616171C"), Color.parseColor("#3DFFFFFF"), dp(22)));
        applySpringTouch(miniPlayerCard);

        miniCover = new ImageView(this);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniCover.setBackgroundColor(Color.parseColor("#2A303C"));
        roundView(miniCover, dp(12));
        miniPlayerCard.addView(miniCover, new LinearLayout.LayoutParams(dp(44), dp(44)));

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
        miniArtist.setText("YouTube Music");
        miniArtist.setTextColor(Color.parseColor("#94A3B8"));
        miniArtist.setTextSize(12);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(TextUtils.TruncateAt.END);

        infoCol.addView(miniTitle);
        infoCol.addView(miniArtist);
        miniPlayerCard.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        miniPlayBtn = createSvgIcon(R.drawable.ic_play, 26, Color.WHITE);
        miniPlayBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        miniPlayBtn.setOnClickListener(v -> togglePlayPause());
        miniPlayerCard.addView(miniPlayBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));

        miniNextBtn = createSvgIcon(R.drawable.ic_next, 24, Color.WHITE);
        miniNextBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        miniNextBtn.setOnClickListener(v -> playNext());
        miniPlayerCard.addView(miniNextBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));

        miniPlayerCard.setOnClickListener(v -> openFullPlayer());
        LinearLayout.LayoutParams miniLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        miniLp.setMargins(0, 0, 0, dp(10));
        dock.addView(miniPlayerCard, miniLp);

        // Draggable Liquid Glass Navigation Pill (4 Tab: Home, Search, Library, Explore)
        liquidNavBar = new LiquidGlassNavBar(this, new int[]{
                R.drawable.ic_home,
                R.drawable.ic_search,
                R.drawable.ic_library,
                R.drawable.ic_explore
        });
        dock.addView(liquidNavBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        return dock;
    }

    // Dock Bawah Mode Scroll (Persis detik 0:02 di video: [Home] [Cover + Judul Lagu + Play] [Search])
    private LinearLayout buildCompactDock() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView homeCircle = new ImageView(this);
        homeCircle.setImageResource(R.drawable.ic_home);
        homeCircle.setColorFilter(Color.parseColor("#22C55E"));
        homeCircle.setPadding(dp(15), dp(15), dp(15), dp(15));
        homeCircle.setBackground(createGlassSurface(
                Color.parseColor("#EB15161B"), Color.parseColor("#4DFFFFFF"), dp(27)));
        applySpringTouch(homeCircle);
        homeCircle.setOnClickListener(v -> {
            feedScrollView.smoothScrollTo(0, 0);
            liquidNavBar.selectTab(0, true);
        });
        row.addView(homeCircle, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout centerPill = new LinearLayout(this);
        centerPill.setOrientation(LinearLayout.HORIZONTAL);
        centerPill.setGravity(Gravity.CENTER_VERTICAL);
        centerPill.setPadding(dp(8), dp(6), dp(12), dp(6));
        centerPill.setBackground(createGlassSurface(
                Color.parseColor("#EB15161B"), Color.parseColor("#4DFFFFFF"), dp(27)));
        applySpringTouch(centerPill);

        compactCover = new ImageView(this);
        compactCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        compactCover.setBackgroundColor(Color.parseColor("#2A303C"));
        roundView(compactCover, dp(19));
        centerPill.addView(compactCover, new LinearLayout.LayoutParams(dp(38), dp(38)));

        compactTitle = new TextView(this);
        compactTitle.setText("Pilih lagu");
        compactTitle.setTextColor(Color.WHITE);
        compactTitle.setTextSize(14);
        compactTitle.setTypeface(null, Typeface.BOLD);
        compactTitle.setSingleLine(true);
        compactTitle.setEllipsize(TextUtils.TruncateAt.END);
        compactTitle.setPadding(dp(10), 0, dp(6), 0);
        centerPill.addView(compactTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        compactPlayBtn = createSvgIcon(R.drawable.ic_play, 24, Color.WHITE);
        compactPlayBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        compactPlayBtn.setOnClickListener(v -> togglePlayPause());
        centerPill.addView(compactPlayBtn, new LinearLayout.LayoutParams(dp(34), dp(34)));

        centerPill.setOnClickListener(v -> openFullPlayer());
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        centerLp.setMargins(dp(8), 0, dp(8), 0);
        row.addView(centerPill, centerLp);

        ImageView searchCircle = new ImageView(this);
        searchCircle.setImageResource(R.drawable.ic_search);
        searchCircle.setColorFilter(Color.WHITE);
        searchCircle.setPadding(dp(15), dp(15), dp(15), dp(15));
        searchCircle.setBackground(createGlassSurface(
                Color.parseColor("#EB15161B"), Color.parseColor("#4DFFFFFF"), dp(27)));
        applySpringTouch(searchCircle);
        searchCircle.setOnClickListener(v -> {
            switchToCompactDock(false);
            liquidNavBar.selectTab(1, true);
        });
        row.addView(searchCircle, new LinearLayout.LayoutParams(dp(54), dp(54)));

        return row;
    }

    private LinearLayout buildSettingsSheet() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(48), dp(20), dp(24));
        sheet.setClickable(true);
        sheet.setBackgroundColor(Color.parseColor("#FA07080B"));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(24));

        ImageView backBtn = createSvgIcon(R.drawable.ic_back, 28, Color.WHITE);
        backBtn.setPadding(0, 0, dp(10), 0);
        backBtn.setOnClickListener(v -> closeSettings());

        TextView title = new TextView(this);
        title.setText("Pengaturan");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);

        topBar.addView(backBtn);
        topBar.addView(title);
        sheet.addView(topBar);

        TextView sectionLabel = new TextView(this);
        sectionLabel.setText("PENGATURAN AKUN");
        sectionLabel.setTextColor(Color.parseColor("#94A3B8"));
        sectionLabel.setTextSize(12);
        sectionLabel.setTypeface(null, Typeface.BOLD);
        sectionLabel.setPadding(dp(8), 0, 0, dp(10));
        sheet.addView(sectionLabel);

        LinearLayout accountCard = new LinearLayout(this);
        accountCard.setOrientation(LinearLayout.VERTICAL);
        accountCard.setBackground(createGlassSurface(
                Color.parseColor("#18FFFFFF"), Color.parseColor("#33FFFFFF"), dp(22)));

        LinearLayout tokenRow = new LinearLayout(this);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setGravity(Gravity.CENTER_VERTICAL);
        tokenRow.setPadding(dp(18), dp(16), dp(18), dp(16));
        applySpringTouch(tokenRow);

        ImageView keyIcon = createSvgIcon(R.drawable.ic_key, 22, Color.parseColor("#F43F5E"));
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        keyLp.setMargins(0, 0, dp(14), 0);

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

        ImageView chevron = createSvgIcon(R.drawable.ic_chevron_right, 22, Color.parseColor("#94A3B8"));

        tokenRow.addView(keyIcon, keyLp);
        tokenRow.addView(tokenTextCol);
        tokenRow.addView(chevron);
        tokenRow.setOnClickListener(v -> showTokenLoginDialog());

        accountCard.addView(tokenRow);

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
            loadFeedTab("HOME", "FEmusic_home", "");
            Toast.makeText(this, "Token dihapus", Toast.LENGTH_SHORT).show();
        });
        accountCard.addView(logoutRowBtn);

        sheet.addView(accountCard);
        return sheet;
    }

    private void showTokenLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout dialogCard = new LinearLayout(this);
        dialogCard.setOrientation(LinearLayout.VERTICAL);
        dialogCard.setPadding(dp(20), dp(20), dp(20), dp(16));
        dialogCard.setBackground(createGlassSurface(
                Color.parseColor("#F222242A"), Color.parseColor("#44FFFFFF"), dp(24)));

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

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(dp(4), dp(16), dp(4), dp(16));

        ImageView infoIcon = createSvgIcon(R.drawable.ic_info, 22, Color.parseColor("#CBD5E1"));
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
            executor.execute(() -> {
                YouTubeMusicClient.fetchAccountProfile(this);
                uiHandler.post(() -> {
                    refreshAccountState();
                    loadFeedTab("HOME", "FEmusic_home", "");
                });
            });
            Toast.makeText(this, "Token berhasil disimpan! Mengambil profil akun...", Toast.LENGTH_SHORT).show();
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
        if (profileIconBtn == null) return;
        boolean loggedIn = YouTubeMusicClient.isLoggedIn();
        String avatarUrl = YouTubeMusicClient.getSavedAvatarUrl();

        if (loggedIn && !avatarUrl.isEmpty()) {
            profileIconBtn.setPadding(0, 0, 0, 0);
            profileIconBtn.setColorFilter(null);
            profileIconBtn.setBackground(createGlassSurface(
                    Color.TRANSPARENT, Color.parseColor("#F43F5E"), dp(22)));
            Glide.with(this).load(avatarUrl).circleCrop().into(profileIconBtn);
        } else {
            profileIconBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
            profileIconBtn.setImageResource(R.drawable.ic_profile);
            profileIconBtn.setColorFilter(Color.WHITE);
            profileIconBtn.setBackground(createGlassSurface(
                    loggedIn ? Color.parseColor("#33F43F5E") : Color.parseColor("#26FFFFFF"),
                    loggedIn ? Color.parseColor("#F43F5E") : Color.parseColor("#55FFFFFF"),
                    dp(22)));
        }

        if (loggedIn) {
            if (accountStatusText != null) {
                accountStatusText.setText("Terhubung sebagai " + YouTubeMusicClient.getSavedAccountName() + " • Aktif");
                accountStatusText.setTextColor(Color.parseColor("#34D399"));
            }
            if (logoutRowBtn != null) logoutRowBtn.setVisibility(View.VISIBLE);
        } else {
            if (accountStatusText != null) {
                accountStatusText.setText("Belum login • Ketuk untuk memasukkan InnerTube Cookie");
                accountStatusText.setTextColor(Color.parseColor("#94A3B8"));
            }
            if (logoutRowBtn != null) logoutRowBtn.setVisibility(View.GONE);
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

    private LinearLayout buildFullPlayerSheet() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setGravity(Gravity.CENTER_HORIZONTAL);
        sheet.setPadding(dp(26), dp(48), dp(26), dp(36));
        sheet.setClickable(true);
        sheet.setBackgroundColor(Color.parseColor("#F507080B"));

        View dragPill = new View(this);
        dragPill.setBackground(createGlassSurface(
                Color.parseColor("#55FFFFFF"), Color.TRANSPARENT, dp(4)));
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(dp(42), dp(5));
        pillParams.setMargins(0, 0, 0, dp(18));
        sheet.addView(dragPill, pillParams);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout closePill = new LinearLayout(this);
        closePill.setOrientation(LinearLayout.HORIZONTAL);
        closePill.setGravity(Gravity.CENTER_VERTICAL);
        closePill.setPadding(dp(12), dp(6), dp(14), dp(6));
        closePill.setBackground(createGlassSurface(
                Color.parseColor("#22FFFFFF"), Color.parseColor("#44FFFFFF"), dp(20)));
        closePill.addView(createSvgIcon(R.drawable.ic_chevron_down, 18, Color.parseColor("#E2E8F0")));
        TextView closeText = new TextView(this);
        closeText.setText(" Tutup");
        closeText.setTextColor(Color.parseColor("#E2E8F0"));
        closeText.setTextSize(13);
        closePill.addView(closeText);
        closePill.setOnClickListener(v -> closeFullPlayer());

        topBar.addView(closePill);
        sheet.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout artWrapper = new FrameLayout(this);
        artWrapper.setElevation(dp(24));
        fullCover = new ImageView(this);
        fullCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fullCover.setBackgroundColor(Color.parseColor("#1E2430"));
        roundView(fullCover, dp(24));
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

        ImageView prevBtn = new ImageView(this);
        prevBtn.setImageResource(R.drawable.ic_prev);
        prevBtn.setColorFilter(Color.WHITE);
        prevBtn.setPadding(dp(16), dp(16), dp(16), dp(16));
        prevBtn.setBackground(createGlassSurface(
                Color.parseColor("#22FFFFFF"), Color.parseColor("#44FFFFFF"), dp(30)));
        applySpringTouch(prevBtn);
        prevBtn.setOnClickListener(v -> playPrevious());

        fullPlayBtn = new ImageView(this);
        fullPlayBtn.setImageResource(R.drawable.ic_play);
        fullPlayBtn.setColorFilter(Color.BLACK);
        fullPlayBtn.setPadding(dp(20), dp(20), dp(20), dp(20));
        fullPlayBtn.setBackground(createGlassSurface(
                Color.WHITE, Color.parseColor("#AAFFFFFF"), dp(38)));
        applySpringTouch(fullPlayBtn);
        fullPlayBtn.setOnClickListener(v -> togglePlayPause());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        playParams.setMargins(dp(28), 0, dp(28), 0);

        ImageView nextBtn = new ImageView(this);
        nextBtn.setImageResource(R.drawable.ic_next);
        nextBtn.setColorFilter(Color.WHITE);
        nextBtn.setPadding(dp(16), dp(16), dp(16), dp(16));
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

    private void playSongAt(int index) {
        if (index < 0 || index >= activeQueue.size()) return;
        currentIndex = index;
        YouTubeMusicClient.Song song = activeQueue.get(index);

        miniTitle.setText("Memuat: " + song.title);
        miniArtist.setText(song.subtitle);
        compactTitle.setText(song.title);
        fullTitle.setText(song.title);
        fullArtist.setText(song.subtitle);

        Glide.with(this).asBitmap().load(song.thumbnail).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                miniCover.setImageBitmap(resource);
                compactCover.setImageBitmap(resource);
                fullCover.setImageBitmap(resource);
                Palette.from(resource).generate(palette -> {
                    if (palette != null) {
                        int vibrant = palette.getDarkVibrantColor(
                                palette.getDominantColor(Color.parseColor("#18181B")));
                        animateAmbientColor(currentAccentColor, vibrant);
                        currentAccentColor = vibrant;
                    }
                });
            }
            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {}
        });

        executor.execute(() -> {
            YouTubeMusicClient.StreamResult stream = YouTubeMusicClient.getAudioStream(song.videoId);
            if (stream == null || stream.url == null) {
                String err = (stream != null && stream.errorDetail != null) ? stream.errorDetail : "Stream tidak tersedia";
                uiHandler.post(() -> {
                    miniTitle.setText("Gagal: " + song.title);
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                });
                return;
            }
            String streamUrl = stream.url;

            uiHandler.post(() -> {
                if (mediaController == null) return;
                MediaMetadata metadata = new MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.subtitle)
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
        });
    }

    private void togglePlayPause() {
        if (mediaController == null) return;
        if (mediaController.isPlaying()) mediaController.pause();
        else mediaController.play();
    }

    private void playNext() {
        if (!activeQueue.isEmpty()) {
            playSongAt((currentIndex + 1) % activeQueue.size());
        }
    }

    private void playPrevious() {
        if (!activeQueue.isEmpty()) {
            int prev = (currentIndex - 1 < 0) ? activeQueue.size() - 1 : currentIndex - 1;
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
                        int iconRes = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
                        miniPlayBtn.setImageResource(iconRes);
                        compactPlayBtn.setImageResource(iconRes);
                        fullPlayBtn.setImageResource(iconRes);
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
                new int[]{topColor, Color.parseColor("#08080A"), Color.parseColor("#050507")});
        ambientBackdrop.setBackground(grad);

        if (fullPlayerSheet != null) {
            GradientDrawable sheetGrad = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{topColor, Color.parseColor("#050507")});
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

    private void roundView(View v, int radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
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
        } else if (playlistDetailOverlay.getVisibility() == View.VISIBLE) {
            playlistDetailOverlay.setVisibility(View.GONE);
        } else if (searchOverlay.getVisibility() == View.VISIBLE) {
            liquidNavBar.selectTab(0, true);
        } else {
            super.onBackPressed();
        }
    }

    private class SongListAdapter extends RecyclerView.Adapter<SongListAdapter.ViewHolder> {
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
            roundView(thumb, dp(12));
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
            YouTubeMusicClient.Song song = searchResults.get(position);
            holder.card.setBackground(createGlassSurface(
                    Color.parseColor("#14FFFFFF"), Color.parseColor("#22FFFFFF"), dp(20)));
            holder.title.setText(song.title);
            holder.artist.setText(song.subtitle);
            Glide.with(holder.thumb.getContext()).load(song.thumbnail).into(holder.thumb);
            holder.card.setOnClickListener(v -> handleItemClick(song, searchResults));
        }

        @Override
        public int getItemCount() {
            return searchResults.size();
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
