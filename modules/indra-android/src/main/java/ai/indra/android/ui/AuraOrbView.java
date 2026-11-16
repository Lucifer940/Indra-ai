package ai.indra.android.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import ai.indra.android.R;

/**
 * The animated holographic AI orb from the home and voice screens.
 *
 * <p>It draws the INDRA character mark inside a layered aura: a soft radial glow, a
 * rotating conic sweep and two breathing rings, all driven by the selected
 * {@link AuraStyle}. State (listening / thinking / speaking) changes the pulse speed
 * and brightness so the orb always reflects what the assistant is doing.</p>
 */
public final class AuraOrbView extends View {

    /** Mirrors the conversational states used by the rest of the app. */
    public enum State {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING
    }

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private AuraStyle aura = AuraStyle.ENERGY;
    private State state = State.IDLE;
    private float mouthLevel;
    private long startedAt = SystemClock.uptimeMillis();
    private float userSpeed = 1f;
    private Bitmap logo;

    public AuraOrbView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        haloPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStyle(Paint.Style.STROKE);
        sweepPaint.setStyle(Paint.Style.STROKE);
        logoPaint.setFilterBitmap(true);
        shadowPaint.setStyle(Paint.Style.FILL);
        try {
            logo = BitmapFactory.decodeResource(getResources(), R.drawable.indra_orb_face);
        } catch (RuntimeException error) {
            logo = null;
        }
    }

    /** User preference from the Aura settings (0.4 - 2.0). */
    public void setSpeed(float speed) {
        this.userSpeed = Math.max(0.4f, Math.min(2f, speed));
    }

    public void setAura(AuraStyle style) {
        this.aura = style == null ? AuraStyle.ENERGY : style;
        invalidate();
    }

    public AuraStyle getAura() {
        return aura;
    }

    public void setState(State next) {
        this.state = next == null ? State.IDLE : next;
        invalidate();
    }

    /** 0..1 mouth opening while the assistant speaks (used for lip sync). */
    public void setMouthLevel(float level) {
        this.mouthLevel = Math.max(0f, Math.min(1f, level));
        if (state == State.SPEAKING) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = Math.min(width, height) * 0.36f;
        float t = (SystemClock.uptimeMillis() - startedAt) / 1000f;

        float speed;
        float intensity;
        float baseSpeed = userSpeed;
        switch (state) {
            case LISTENING:
                speed = 1.6f;
                intensity = 1.15f;
                break;
            case THINKING:
                speed = 2.2f;
                intensity = 0.95f;
                break;
            case SPEAKING:
                speed = 2.8f;
                intensity = 1.0f + mouthLevelGoal() * 0.25f;
                break;
            default:
                speed = 0.7f;
                intensity = 0.85f;
                break;
        }
        speed *= baseSpeed;

        // 1. Wide soft glow behind the orb.
        int glowColor = UiKit.withAlpha(aura.getMiddle(), (int) (70 * intensity));
        glowPaint.setShader(new RadialGradient(cx, cy, radius * 2.05f,
                new int[]{glowColor, UiKit.withAlpha(aura.getOuter(), 24), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * 2.05f, glowPaint);

        // 2. Rotating conic sweep (holographic shimmer).
        float sweepRadius = radius * 1.28f;
        sweepPaint.setStrokeWidth(radius * 0.055f);
        sweepPaint.setShader(new LinearGradient(cx - sweepRadius, cy - sweepRadius,
                cx + sweepRadius, cy + sweepRadius,
                new int[]{UiKit.withAlpha(aura.getInner(), 0), UiKit.withAlpha(aura.getInner(), 210),
                        UiKit.withAlpha(aura.getOuter(), 60)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        float rotation = (t * 42f * speed) % 360f;
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        canvas.drawArc(new android.graphics.RectF(cx - sweepRadius, cy - sweepRadius,
                cx + sweepRadius, cy + sweepRadius), 0f, 250f, false, sweepPaint);
        canvas.restore();

        // 3. Two breathing rings.
        float breathe = (float) (0.5 + 0.5 * Math.sin(t * 1.6 * speed));
        for (int i = 0; i < 2; i++) {
            float ringRadius = radius * (1.08f + i * 0.16f) + breathe * radius * 0.04f;
            int alpha = (int) ((i == 0 ? 120 : 70) * (0.6f + 0.4f * breathe));
            ringPaint.setStrokeWidth(radius * (0.018f + i * 0.008f));
            ringPaint.setColor(UiKit.withAlpha(i == 0 ? aura.getMiddle() : aura.getOuter(), alpha));
            canvas.drawCircle(cx, cy, ringRadius, ringPaint);
        }

        // 4. Dark glass disc with the character mark inside.
        shadowPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{UiKit.withAlpha(aura.getInner(), 90), Color.parseColor("#12000A")},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, shadowPaint);

        haloPaint.setStrokeWidth(Math.max(2f, radius * 0.02f));
        haloPaint.setColor(UiKit.withAlpha(aura.getOuter(), 220));
        canvas.drawCircle(cx, cy, radius, haloPaint);

        if (logo != null) {
            float logoSize = radius * 1.62f;
            float left = cx - logoSize / 2f;
            float top = cy - logoSize / 2f;
            canvas.save();
            canvas.clipRect(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawBitmap(logo, null, new android.graphics.RectF(left, top, left + logoSize,
                    top + logoSize), logoPaint);
            // dark vignette so the mark blends into the glass disc
            glowPaint.setShader(new RadialGradient(cx, cy, radius,
                    new int[]{Color.TRANSPARENT, UiKit.withAlpha(Color.parseColor("#12000A"), 190)},
                    new float[]{0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, glowPaint);
            canvas.restore();
        }

        // 5. Speaking pulse ring.
        if (state == State.SPEAKING) {
            float pulse = radius * (1.3f + 0.12f * (float) Math.sin(t * 9f)) + radius * 0.12f * mouthLevel;
            haloPaint.setStrokeWidth(radius * 0.03f);
            haloPaint.setColor(UiKit.withAlpha(aura.getInner(), 170));
            canvas.drawCircle(cx, cy, pulse, haloPaint);
        }

        // Only animate while visible: an off-screen orb must not burn battery.
        if (isAttachedToWindow() && isShown()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    private float mouthLevelGoal() {
        return mouthLevel;
    }
}
