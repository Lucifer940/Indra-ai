package ai.indra.desktop;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;

import java.util.Random;

/**
 * Desktop version of the INDRA AI character. Same identity as the mobile app:
 * a friendly, futuristic female face with blinking, gaze drift, a mouth that
 * follows speech and state-driven motion.
 */
public class CharacterCanvas extends Canvas {

    /** Character state, mirroring the Android version. */
    public enum State {
        IDLE, LISTENING, THINKING, SPEAKING, HAPPY, CONFUSED, SERIOUS
    }

    private final AnimationTimer timer;
    private final Random random = new Random();

    private State state = State.IDLE;
    private double phase;
    private double blink = 1;
    private double mouthLevel;
    private double smile = 0.55;
    private long nextBlinkAt;
    private long lastMouthPulse;

    public CharacterCanvas(double width, double height) {
        super(width, height);
        nextBlinkAt = System.currentTimeMillis() + 2400;
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                phase += 0.03;
                update();
                draw();
            }
        };
        timer.start();
    }

    public void setState(State state) {
        this.state = state;
        switch (state) {
            case HAPPY:
                smile = 1.0;
                break;
            case SERIOUS:
                smile = -0.4;
                break;
            case CONFUSED:
                smile = 0.15;
                break;
            default:
                smile = 0.55;
                break;
        }
    }

    public void setMouthLevel(double level) {
        this.mouthLevel = Math.max(0, Math.min(1, level));
        this.lastMouthPulse = System.currentTimeMillis();
    }

    private void update() {
        long now = System.currentTimeMillis();
        if (now >= nextBlinkAt) {
            double progress = (now - nextBlinkAt) / 150.0;
            if (progress >= 1) {
                blink = 1;
                nextBlinkAt = now + 2000 + random.nextInt(2600);
            } else {
                blink = Math.abs(1 - 2 * progress);
            }
        }
        if (state == State.SPEAKING) {
            if (now - lastMouthPulse > 90) {
                mouthLevel = 0.15 + random.nextDouble() * 0.7;
                lastMouthPulse = now;
            }
        } else if (state != State.LISTENING) {
            mouthLevel = Math.max(0.06, mouthLevel * 0.9);
        }
    }

    private void draw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        g.clearRect(0, 0, w, h);
        double cx = w / 2;
        double cy = h / 2;
        double size = Math.min(w, h);

        g.setFill(new RadialGradient(0, 0, cx, cy, size * 0.55, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#5B8CFF", 0.30)),
                new Stop(0.6, Color.web("#8A5BFF", 0.12)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillOval(cx - size * 0.55, cy - size * 0.55, size * 1.1, size * 1.1);

        g.setLineWidth(size * 0.005);
        g.setStroke(Color.web("#5B8CFF", 0.35 + 0.1 * Math.sin(phase)));
        g.strokeOval(cx - size * 0.44, cy - size * 0.44, size * 0.88, size * 0.88);
        g.setStroke(Color.web("#8A5BFF", 0.28));
        g.strokeOval(cx - size * 0.48, cy - size * 0.48, size * 0.96, size * 0.96);

        if (state == State.LISTENING) {
            g.setStroke(Color.web("#3DD68C", 0.5));
            for (int i = 0; i < 3; i++) {
                double progress = ((phase * 0.14) + i * 0.33) % 1;
                double radius = size * (0.26 + progress * 0.24);
                g.setStroke(Color.web("#3DD68C", Math.max(0, 0.45 * (1 - progress))));
                g.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
            }
        }
        if (state == State.THINKING) {
            g.setFill(Color.web("#FFB84D"));
            for (int i = 0; i < 4; i++) {
                double angle = phase * 0.6 + i * (Math.PI / 2);
                double radius = size * 0.33;
                g.fillOval(cx + Math.cos(angle) * radius - size * 0.01,
                        cy + Math.sin(angle) * radius * 0.55 - size * 0.01, size * 0.02, size * 0.02);
            }
        }

        double faceW = size * 0.30;
        double faceH = size * 0.36;

        // hair
        g.setFill(Color.web("#1B2440"));
        g.fillRoundRect(cx - faceW * 1.35, cy - faceH * 1.15, faceW * 2.7, faceH * 2.2, faceW * 1.3, faceW * 1.3);
        g.setFill(Color.web("#222E52"));
        g.fillRoundRect(cx - faceW * 1.5, cy - faceH * 0.6, faceW * 0.5, faceH * 1.7, faceW * 0.3, faceW * 0.3);
        g.fillRoundRect(cx + faceW, cy - faceH * 0.6, faceW * 0.5, faceH * 1.7, faceW * 0.3, faceW * 0.3);

        // face
        g.setFill(Color.web("#F0DCCE"));
        g.fillOval(cx - faceW, cy - faceH, faceW * 2, faceH * 2);

        // fringe
        g.setFill(Color.web("#1B2440"));
        g.fillArc(cx - faceW * 1.05, cy - faceH * 1.55, faceW * 2.1, faceH * 1.55, 200, 160, ArcType.ROUND);

        // neck + collar
        g.setFill(Color.web("#DCC0AE"));
        g.fillRoundRect(cx - size * 0.05, cy + faceH * 0.78, size * 0.1, size * 0.12, size * 0.02, size * 0.02);
        g.setFill(Color.web("#243055"));
        g.fillRoundRect(cx - size * 0.16, cy + faceH * 1.05, size * 0.32, size * 0.12, size * 0.04, size * 0.04);
        g.setFill(Color.web("#5B8CFF"));
        g.fillOval(cx - size * 0.012, cy + faceH * 1.13, size * 0.024, size * 0.024);

        // eyes
        double eyeY = cy - size * 0.045;
        double eyeSpacing = size * 0.105;
        double eyeW = size * 0.055;
        double eyeH = Math.max(size * 0.004, size * 0.036 * blink);
        double driftX = Math.sin(phase * 0.28) * size * 0.006;
        double driftY = Math.cos(phase * 0.19) * size * 0.004;
        for (int i = 0; i < 2; i++) {
            double x = cx + (i == 0 ? -eyeSpacing : eyeSpacing) + driftX;
            g.setFill(Color.web("#FBF7F4"));
            g.fillOval(x - eyeW, eyeY - eyeH, eyeW * 2, eyeH * 2);
            g.setFill(Color.web("#2B4E8C"));
            g.fillOval(x - eyeH * 0.82, eyeY + driftY - eyeH * 0.82, eyeH * 1.64, eyeH * 1.64);
            g.setFill(Color.web("#0B1224"));
            g.fillOval(x - eyeH * 0.4, eyeY + driftY - eyeH * 0.4, eyeH * 0.8, eyeH * 0.8);
            g.setFill(Color.web("#FFFFFF", 0.85));
            g.fillOval(x - eyeH * 0.45, eyeY + driftY - eyeH * 0.6, eyeH * 0.28, eyeH * 0.28);
            g.setStroke(Color.web("#2A2233", 0.9));
            g.setLineWidth(size * 0.004);
            g.strokeArc(x - eyeW, eyeY - eyeH * 1.2, eyeW * 2, eyeH * 1.8, 20, 140, ArcType.OPEN);
        }

        // brows
        g.setStroke(Color.web("#2A2233"));
        g.setLineWidth(size * 0.006);
        g.setLineCap(StrokeLineCap.ROUND);
        double browY = cy - size * 0.115;
        g.strokeLine(cx - eyeSpacing - size * 0.045, browY, cx - eyeSpacing + size * 0.045, browY);
        double rightLift = state == State.CONFUSED ? size * 0.008 : 0;
        g.strokeLine(cx + eyeSpacing - size * 0.045, browY, cx + eyeSpacing + size * 0.045, browY - rightLift);

        // nose
        g.setStroke(Color.web("#B08A76", 0.75));
        g.setLineWidth(size * 0.004);
        g.strokeLine(cx + size * 0.004, cy - size * 0.005, cx + size * 0.012, cy + size * 0.03);

        // mouth
        double mouthY = cy + size * 0.085;
        double mouthW = size * 0.052 + mouthLevel * size * 0.014;
        double openness = state == State.SPEAKING || state == State.LISTENING
                ? Math.max(0.1, mouthLevel) : 0.15;
        g.setFill(Color.web("#B25A62"));
        g.fillOval(cx - mouthW, mouthY - size * 0.008, mouthW * 2,
                size * 0.016 + openness * size * 0.05);
        if (openness > 0.22) {
            g.setFill(Color.web("#4A1F27"));
            g.fillOval(cx - mouthW * 0.7, mouthY, mouthW * 1.4, openness * size * 0.04);
        }
        g.setStroke(Color.web("#8E4A52"));
        g.setLineWidth(size * 0.004);
        g.strokeArc(cx - mouthW * 1.15, mouthY - size * 0.02, mouthW * 2.3,
                size * 0.04 + smile * size * 0.01, 200, 140, ArcType.OPEN);

        // cheeks + ear accents
        g.setFill(Color.web("#FF8FA3", state == State.HAPPY ? 0.35 : 0.22));
        g.fillOval(cx - size * 0.175, cy + size * 0.005, size * 0.07, size * 0.045);
        g.fillOval(cx + size * 0.105, cy + size * 0.005, size * 0.07, size * 0.045);
        g.setFill(Color.web("#5B8CFF", 0.65));
        g.fillOval(cx - size * 0.31, cy - size * 0.03, size * 0.022, size * 0.022);
        g.fillOval(cx + size * 0.29, cy - size * 0.03, size * 0.022, size * 0.022);
    }

    public void dispose() {
        timer.stop();
    }
}
