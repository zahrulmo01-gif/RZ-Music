package com.rulsz.music;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class LiquidGlassNavView extends FrameLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private final List<LinearLayout> tabSlots = new ArrayList<>();
    private final List<ImageView> tabIcons = new ArrayList<>();
    private final List<TextView> tabLabels = new ArrayList<>();

    private final GlassTrackBackground trackBackground;
    private final LiquidDropletLens dropletLens;
    private final ArgbEvaluator colorEvaluator = new ArgbEvaluator();

    private int selectedIndex = 0;
    private int lastHapticIndex = 0;
    private float lensCenterX = -1f;
    private float lensScaleX = 1.0f;
    private float lensScaleY = 1.0f;
    private boolean isDragging = false;
    private float touchDownX = 0f;
    private float dragStartCenterX = 0f;

    private ValueAnimator snapAnimator;
    private ValueAnimator morphAnimator;
    private OnTabSelectedListener listener;

    private final int activeColor = Color.parseColor("#22C55E");   // Aksen aktif Sonara
    private final int inactiveColor = Color.parseColor("#E2E8F0"); // Putih kaca

    public LiquidGlassNavView(Context context, int[] iconResIds, String[] titles) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        // 1. Latar Belakang Kapsul Frosted Liquid Glass
        trackBackground = new GlassTrackBackground(context);
        addView(trackBackground, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Lensa Tetesan Kaca Cair (Droplet Lens) yang bisa digeser-geser
        dropletLens = new LiquidDropletLens(context);
        addView(dropletLens, new LayoutParams(dp(82), dp(52)));

        // 3. Baris Slot Ikon + Label
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), 0, dp(6), 0);

        for (int i = 0; i < iconResIds.length; i++) {
            LinearLayout slot = new LinearLayout(context);
            slot.setOrientation(LinearLayout.VERTICAL);
            slot.setGravity(Gravity.CENTER);

            ImageView icon = new ImageView(context);
            icon.setImageResource(iconResIds[i]);
            icon.setColorFilter(i == 0 ? activeColor : inactiveColor);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
            slot.addView(icon, iconLp);

            TextView label = new TextView(context);
            label.setText(titles[i]);
            label.setTextSize(10f);
            label.setTextColor(i == 0 ? activeColor : inactiveColor);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(2), 0, 0);
            slot.addView(label);

            tabSlots.add(slot);
            tabIcons.add(icon);
            tabLabels.add(label);

            row.addView(slot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }

        addView(row, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (getWidth() > 0 && !tabSlots.isEmpty()) {
            float innerW = getWidth() - dp(12);
            float slotW = innerW / tabSlots.size();
            int targetLensW = (int) (slotW * 0.92f);
            int targetLensH = getHeight() - dp(10);

            if (dropletLens.getLayoutParams().width != targetLensW || dropletLens.getLayoutParams().height != targetLensH) {
                dropletLens.getLayoutParams().width = targetLensW;
                dropletLens.getLayoutParams().height = targetLensH;
                dropletLens.requestLayout();
            }

            if (lensCenterX < 0f || !isDragging) {
                lensCenterX = getSlotCenterX(selectedIndex);
                applyLensTransform();
                updateMagneticIcons();
            }
        }
    }

    private float getSlotCenterX(int index) {
        float innerW = getWidth() - dp(12);
        float slotW = innerW / tabSlots.size();
        return dp(6) + (index + 0.5f) * slotW;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() == 0 || tabSlots.isEmpty()) return false;

        float halfLens = dropletLens.getWidth() / 2f;
        float minCenter = dp(6) + halfLens;
        float maxCenter = getWidth() - dp(6) - halfLens;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (snapAnimator != null) snapAnimator.cancel();
                isDragging = true;
                touchDownX = event.getX();
                dragStartCenterX = lensCenterX;
                // Efek tetesan cairan memuai saat disentuh (Sonara stretch physics)
                animateDropletMorph(1.16f, 1.12f);
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getX() - touchDownX;
                float rawCenter = Math.abs(deltaX) > dp(4)
                        ? (dragStartCenterX + deltaX)
                        : event.getX();
                lensCenterX = Math.max(minCenter, Math.min(maxCenter, rawCenter));
                applyLensTransform();
                updateMagneticIcons();

                int hoveredIndex = getNearestTabIndex(lensCenterX);
                if (hoveredIndex != lastHapticIndex) {
                    lastHapticIndex = hoveredIndex;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                animateDropletMorph(1.0f, 1.0f);
                int targetIndex = getNearestTabIndex(lensCenterX);
                selectTab(targetIndex, true);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private int getNearestTabIndex(float centerX) {
        float innerW = getWidth() - dp(12);
        float slotW = innerW / tabSlots.size();
        int idx = (int) ((centerX - dp(6)) / slotW);
        return Math.max(0, Math.min(tabSlots.size() - 1, idx));
    }

    public void selectTab(int index, boolean notify) {
        if (index < 0 || index >= tabSlots.size()) return;
        selectedIndex = index;
        lastHapticIndex = index;

        if (getWidth() == 0) return;

        float targetCenter = getSlotCenterX(index);
        if (snapAnimator != null) snapAnimator.cancel();
        snapAnimator = ValueAnimator.ofFloat(lensCenterX, targetCenter);
        snapAnimator.setDuration(340);
        snapAnimator.setInterpolator(new OvershootInterpolator(1.25f));
        snapAnimator.addUpdateListener(anim -> {
            lensCenterX = (float) anim.getAnimatedValue();
            applyLensTransform();
            updateMagneticIcons();
        });
        snapAnimator.start();

        if (notify && listener != null) {
            listener.onTabSelected(index);
        }
    }

    private void animateDropletMorph(float targetSX, float targetSY) {
        if (morphAnimator != null) morphAnimator.cancel();
        float startSX = lensScaleX;
        float startSY = lensScaleY;
        morphAnimator = ValueAnimator.ofFloat(0f, 1f);
        morphAnimator.setDuration(200);
        morphAnimator.setInterpolator(new OvershootInterpolator(1.4f));
        morphAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            lensScaleX = startSX + (targetSX - startSX) * t;
            lensScaleY = startSY + (targetSY - startSY) * t;
            applyLensTransform();
        });
        morphAnimator.start();
    }

    private void applyLensTransform() {
        float lensW = dropletLens.getWidth() > 0 ? dropletLens.getWidth() : dp(82);
        float lensH = dropletLens.getHeight() > 0 ? dropletLens.getHeight() : dp(52);
        dropletLens.setTranslationX(lensCenterX - (lensW / 2f));
        dropletLens.setTranslationY((getHeight() - lensH) / 2f);
        dropletLens.setScaleX(lensScaleX);
        dropletLens.setScaleY(lensScaleY);
        dropletLens.invalidate();
    }

    // Interpolasi warna & pembesaran magnetik ikon saat lensa kaca melintas di atasnya
    private void updateMagneticIcons() {
        if (getWidth() == 0) return;
        float innerW = getWidth() - dp(12);
        float slotW = innerW / tabSlots.size();

        for (int i = 0; i < tabSlots.size(); i++) {
            float slotCenter = getSlotCenterX(i);
            float distance = Math.abs(lensCenterX - slotCenter);
            float proximity = Math.max(0f, 1f - (distance / (slotW * 0.75f)));

            int blendedColor = (int) colorEvaluator.evaluate(proximity, inactiveColor, activeColor);
            float iconScale = 1.0f + (0.14f * proximity);

            tabIcons.get(i).setColorFilter(blendedColor);
            tabLabels.get(i).setTextColor(blendedColor);
            tabSlots.get(i).setScaleX(iconScale);
            tabSlots.get(i).setScaleY(iconScale);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private float dpf(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    // Latar Kapsul Kaca (Dark Translucent + Specular Edge Rim)
    private class GlassTrackBackground extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint topHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public GlassTrackBackground(Context context) {
            super(context);
            topHighlightPaint.setStyle(Paint.Style.STROKE);
            topHighlightPaint.setStrokeWidth(dpf(1.1f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float r = h / 2f;
            rect.set(dpf(0.5f), dpf(0.5f), w - dpf(0.5f), h - dpf(0.5f));

            fillPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    Color.parseColor("#E6181920"), Color.parseColor("#F20F1014"),
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, r, r, fillPaint);

            topHighlightPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    new int[]{Color.parseColor("#59FFFFFF"), Color.parseColor("#14FFFFFF"), Color.parseColor("#33FFFFFF")},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, r, r, topHighlightPaint);
        }
    }

    // Lensa Tetesan Liquid Glass (Refractive Rim + Chromatic Bottom Glow)
    private class LiquidDropletLens extends View {
        private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint specularRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public LiquidDropletLens(Context context) {
            super(context);
            specularRimPaint.setStyle(Paint.Style.STROKE);
            specularRimPaint.setStrokeWidth(dpf(1.4f));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(0.5f, 0.5f, Shader.TileMode.CLAMP));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float r = h / 2f;
            rect.set(dpf(1f), dpf(1f), w - dpf(1f), h - dpf(1f));

            // 1. Pendaran cairan hijau di dasar lensa
            glowPaint.setShader(new RadialGradient(
                    w * 0.5f, h * 0.9f, w * 0.65f,
                    new int[]{Color.parseColor("#4422C55E"), Color.parseColor("#1822C55E"), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, r, r, glowPaint);

            // 2. Permukaan kaca cembung transparan
            bodyPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    new int[]{Color.parseColor("#38FFFFFF"), Color.parseColor("#14FFFFFF"), Color.parseColor("#24FFFFFF")},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, r, r, bodyPaint);

            // 3. Garis refraksi cahaya putih tajam di keliling lensa
            specularRimPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    new int[]{Color.parseColor("#CCFFFFFF"), Color.parseColor("#26FFFFFF"), Color.parseColor("#88FFFFFF")},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, r, r, specularRimPaint);
        }
    }
}
