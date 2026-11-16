package ai.indra.android.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The INDRA AI character: a friendly, futuristic female face drawn entirely with
 * Canvas primitives (no image assets), so it scales to any screen and stays
 * consistent everywhere.
 *
 * <p>States drive expression and motion: idle breathing, natural blinking, slow
 * eye drift, listening rings, thinking orbit, and mouth movement synchronised
 * with text-to-speech while speaking.</p>
 */
public class CharacterView extends View {

    /** Expression / activity states. */
    public enum State {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
        HAPPY,
        CONFUSED,
        SERIOUS
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private State state = State.IDLE;
    private float phase;
    private float blink = 1f;
    private float mouthLevel;
    private float smile;
    private float eyeOffsetX;
    private float eyeOffsetY;
    private long lastBlinkAt;
    private long nextBlinkIn = 2600L;
    private ValueAnimator ticker;
    private boolean running;

    public CharacterView(Context context) {
        super(context);
        init();
    }

    public CharacterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setContentDescription("Indra, your AI assistant");
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        lastBlinkAt = System.currentTimeMillis();
        ticker = ValueAnimator.ofFloat(0f, 1f);
        ticker.setDuration(2400L);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                phase += 0.035f;
                updateBlink();
                if (state == State.SPEAKING) {
                    // Speech drives the mouth through setMouthLevel; decay keeps idle frames calm.
                    mouthLevel = Math.max(0.05f, mouthLevel * 0.92f);
                }
                invalidate();
            }
        });
    }

    private void updateBlink() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastBlinkAt;
        if (elapsed > nextBlinkIn) {
            float t = (elapsed - nextBlinkIn) / 160f;
            if (t >= 1f) {
                lastBlinkAt = now;
                nextBlinkIn = 2200L + (long) (Math.random() * 2600L);
                blink = 1f;
            } else {
                blink = Math.abs(1f - 2f * t);
            }
        } else {
            blink = 1f;
        }
    }

    public void setState(State state) {
        this.state = state == null ? State.IDLE : state;
        this.smile = state == State.HAPPY ? 1f : state == State.SERIOUS ? -0.4f
                : state == State.CONFUSED ? 0.15f : 0.55f;
        if (state == State.SPEAKING || state == State.LISTENING || state == State.THINKING) {
            start();
        }
        invalidate();
    }

    public State getState() {
        return state;
    }

    /** Amplitude 0..1 supplied by the TTS progress callback. */
    public void setMouthLevel(float level) {
        this.mouthLevel = Math.max(0f, Math.min(1f, level));
        invalidate();
    }

    /** Drives a synthetic mouth animation between real TTS callbacks. */
    public void pulseMouth() {
        mouthLevel = 0.35f + (float) (Math.random() * 0.6f);
        invalidate();
    }

    public void start() {
        if (!running) {
            running = true;
            ticker.start();
        }
    }

    public void stop() {
        if (running) {
            running = false;
            ticker.cancel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float cx = width / 2f;
        float cy = height / 2f;
        float size = Math.min(width, height);

        drawBackdrop(canvas, cx, cy, size);
        if (state == State.LISTENING) {
            drawListeningRings(canvas, cx, cy, size);
        }
        if (state == State.THINKING) {
            drawThinkingOrbit(canvas, cx, cy, size);
        }
        drawHair(canvas, cx, cy, size);
        drawFace(canvas, cx, cy, size);
        drawEyes(canvas, cx, cy, size);
        drawBrows(canvas, cx, cy, size);
        drawNose(canvas, cx, cy, size);
        drawMouth(canvas, cx, cy, size);
        drawHighlights(canvas, cx, cy, size);
    }

    private void drawBackdrop(Canvas canvas, float cx, float cy, float size) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setShader(new RadialGradient(cx, cy, size * 0.55f,
                new int[]{UiKit.withAlpha(UiKit.ACCENT, 70), UiKit.withAlpha(UiKit.SECONDARY, 26), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, size * 0.55f, paint);
        paint.setShader(null);

        // Orbiting core rings give the character a sci-fi halo.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.006f);
        paint.setColor(UiKit.withAlpha(UiKit.ACCENT, 60 + (int) (18 * Math.sin(phase))));
        canvas.drawCircle(cx, cy, size * 0.44f, paint);
        paint.setColor(UiKit.withAlpha(UiKit.SECONDARY, 45));
        canvas.drawCircle(cx, cy, size * 0.48f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawListeningRings(Canvas canvas, float cx, float cy, float size) {
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 3; i++) {
            float progress = ((phase * 0.12f) + i * 0.33f) % 1f;
            float radius = size * (0.26f + progress * 0.24f);
            int alpha = (int) (110 * (1f - progress));
            paint.setStrokeWidth(size * 0.008f);
            paint.setColor(UiKit.withAlpha(UiKit.SUCCESS, Math.max(0, alpha)));
            canvas.drawCircle(cx, cy, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawThinkingOrbit(Canvas canvas, float cx, float cy, float size) {
        paint.setColor(UiKit.withAlpha(UiKit.WARNING, 200));
        for (int i = 0; i < 4; i++) {
            double angle = phase * 0.5f + i * (Math.PI / 2d);
            float radius = size * 0.33f;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius * 0.55f;
            canvas.drawCircle(x, y, size * 0.014f, paint);
        }
    }

    private void drawHair(Canvas canvas, float cx, float cy, float size) {
        paint.setColor(Color.parseColor("#1B2440"));
        float hairWidth = size * 0.40f;
        float hairHeight = size * 0.50f;
        rect.set(cx - hairWidth, cy - hairHeight * 0.95f, cx + hairWidth, cy + hairHeight * 0.9f);
        canvas.drawRoundRect(rect, hairWidth * 0.7f, hairWidth * 0.7f, paint);

        // Side strands.
        paint.setColor(Color.parseColor("#222E52"));
        rect.set(cx - hairWidth * 1.02f, cy - hairHeight * 0.55f, cx - hairWidth * 0.62f, cy + hairHeight * 0.85f);
        canvas.drawRoundRect(rect, hairWidth * 0.3f, hairWidth * 0.3f, paint);
        rect.set(cx + hairWidth * 0.62f, cy - hairHeight * 0.55f, cx + hairWidth * 1.02f, cy + hairHeight * 0.85f);
        canvas.drawRoundRect(rect, hairWidth * 0.3f, hairWidth * 0.3f, paint);
    }

    private void drawFace(Canvas canvas, float cx, float cy, float size) {
        float faceWidth = size * 0.30f;
        float faceHeight = size * 0.36f;
        rect.set(cx - faceWidth, cy - faceHeight, cx + faceWidth, cy + faceHeight);
        paint.setShader(new LinearGradient(cx - faceWidth, cy - faceHeight, cx + faceWidth, cy + faceHeight,
                Color.parseColor("#F3E0D2"), Color.parseColor("#D9BBA6"), Shader.TileMode.CLAMP));
        canvas.drawOval(rect, paint);
        paint.setShader(null);

        // Hair fringe over the forehead keeps the face design consistent.
        paint.setColor(Color.parseColor("#1B2440"));
        path.reset();
        path.moveTo(cx - faceWidth * 1.02f, cy - faceHeight * 0.45f);
        path.cubicTo(cx - faceWidth * 0.5f, cy - faceHeight * 1.25f, cx + faceWidth * 0.55f, cy - faceHeight * 1.2f,
                cx + faceWidth * 1.02f, cy - faceHeight * 0.35f);
        path.cubicTo(cx + faceWidth * 0.6f, cy - faceHeight * 0.72f, cx - faceWidth * 0.35f, cy - faceHeight * 0.7f,
                cx - faceWidth * 1.02f, cy - faceHeight * 0.45f);
        canvas.drawPath(path, paint);

        // Neck and collar hint.
        paint.setColor(Color.parseColor("#D9BBA6"));
        rect.set(cx - size * 0.055f, cy + faceHeight * 0.75f, cx + size * 0.055f, cy + faceHeight * 1.1f);
        canvas.drawRoundRect(rect, size * 0.02f, size * 0.02f, paint);
        paint.setColor(Color.parseColor("#243055"));
        rect.set(cx - size * 0.16f, cy + faceHeight * 1.02f, cx + size * 0.16f, cy + faceHeight * 1.35f);
        canvas.drawRoundRect(rect, size * 0.05f, size * 0.05f, paint);
        paint.setColor(UiKit.withAlpha(UiKit.ACCENT, 220));
        canvas.drawCircle(cx, cy + faceHeight * 1.16f, size * 0.012f, paint);
    }

    private void drawEyes(Canvas canvas, float cx, float cy, float size) {
        float eyeY = cy - size * 0.045f;
        float eyeSpacing = size * 0.105f;
        float eyeWidth = size * 0.055f;
        float eyeHeight = size * 0.036f * Math.max(0.06f, blink);

        // Slow, natural gaze drift.
        eyeOffsetX = (float) Math.sin(phase * 0.28f) * size * 0.006f;
        eyeOffsetY = (float) Math.cos(phase * 0.19f) * size * 0.004f;

        for (int i = 0; i < 2; i++) {
            float x = cx + (i == 0 ? -eyeSpacing : eyeSpacing) + eyeOffsetX;
            rect.set(x - eyeWidth, eyeY - eyeHeight, x + eyeWidth, eyeY + eyeHeight);
            paint.setColor(Color.parseColor("#FBF7F4"));
            canvas.drawOval(rect, paint);

            // Iris
            paint.setColor(Color.parseColor("#2B4E8C"));
            canvas.drawCircle(x, eyeY + eyeOffsetY, eyeHeight * 0.82f, paint);
            // Pupil
            paint.setColor(Color.parseColor("#0B1224"));
            canvas.drawCircle(x, eyeY + eyeOffsetY, eyeHeight * 0.42f, paint);
            // Catch light
            paint.setColor(UiKit.withAlpha(Color.WHITE, 220));
            canvas.drawCircle(x - eyeHeight * 0.24f, eyeY + eyeOffsetY - eyeHeight * 0.26f,
                    eyeHeight * 0.16f, paint);
            // Upper lash line
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size * 0.005f);
            paint.setColor(Color.parseColor("#2A2233"));
            rect.set(x - eyeWidth, eyeY - eyeHeight * 1.1f, x + eyeWidth, eyeY + eyeHeight * 0.6f);
            canvas.drawArc(rect, 200f, 140f, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawBrows(Canvas canvas, float cx, float cy, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.006f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.parseColor("#2A2233"));
        float browY = cy - size * 0.115f - (state == State.SERIOUS ? size * 0.006f : 0f)
                + (state == State.CONFUSED ? size * 0.004f : 0f);
        float eyeSpacing = size * 0.105f;
        path.reset();
        path.moveTo(cx - eyeSpacing - size * 0.045f, browY + (state == State.HAPPY ? -size * 0.004f : 0f));
        path.quadTo(cx - eyeSpacing, browY - size * 0.012f, cx - eyeSpacing + size * 0.045f, browY);
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(cx + eyeSpacing - size * 0.045f, browY);
        path.quadTo(cx + eyeSpacing, browY - size * 0.012f
                - (state == State.CONFUSED ? size * 0.008f : 0f), cx + eyeSpacing + size * 0.045f, browY);
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawNose(Canvas canvas, float cx, float cy, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.004f);
        paint.setColor(UiKit.withAlpha(Color.parseColor("#B08A76"), 190));
        path.reset();
        path.moveTo(cx + size * 0.004f, cy - size * 0.005f);
        path.quadTo(cx - size * 0.006f, cy + size * 0.03f, cx + size * 0.012f, cy + size * 0.034f);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawMouth(Canvas canvas, float cx, float cy, float size) {
        float mouthY = cy + size * 0.085f;
        float mouthWidth = size * 0.052f + mouthLevel * size * 0.014f;
        float openness = (state == State.SPEAKING || state == State.LISTENING)
                ? Math.max(0.08f, mouthLevel) : 0.12f;

        // Lips
        paint.setColor(Color.parseColor("#B25A62"));
        rect.set(cx - mouthWidth, mouthY - size * 0.008f, cx + mouthWidth, mouthY + size * 0.008f
                + openness * size * 0.05f);
        canvas.drawOval(rect, paint);

        // Inner mouth while speaking
        if (openness > 0.2f) {
            paint.setColor(Color.parseColor("#4A1F27"));
            rect.set(cx - mouthWidth * 0.72f, mouthY - size * 0.002f,
                    cx + mouthWidth * 0.72f, mouthY + size * 0.004f + openness * size * 0.038f);
            canvas.drawOval(rect, paint);
        }

        // Smile curve
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.005f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.parseColor("#8E4A52"));
        path.reset();
        path.moveTo(cx - mouthWidth * 1.15f, mouthY - smile * size * 0.006f);
        path.quadTo(cx, mouthY + smile * size * 0.03f + openness * size * 0.02f,
                cx + mouthWidth * 1.15f, mouthY - smile * size * 0.006f);
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHighlights(Canvas canvas, float cx, float cy, float size) {
        // Cheek glow reacts to the mood.
        int alpha = state == State.HAPPY ? 90 : 55;
        paint.setColor(UiKit.withAlpha(Color.parseColor("#FF8FA3"), alpha));
        canvas.drawCircle(cx - size * 0.14f, cy + size * 0.02f, size * 0.035f, paint);
        canvas.drawCircle(cx + size * 0.14f, cy + size * 0.02f, size * 0.035f, paint);

        // Circuit-like ear accents.
        paint.setColor(UiKit.withAlpha(UiKit.ACCENT, 160));
        canvas.drawCircle(cx - size * 0.30f, cy - size * 0.02f, size * 0.012f, paint);
        canvas.drawCircle(cx + size * 0.30f, cy - size * 0.02f, size * 0.012f, paint);
    }
}
