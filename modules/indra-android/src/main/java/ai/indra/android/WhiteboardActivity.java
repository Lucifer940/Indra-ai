package ai.indra.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.indra.android.platform.FileExport;
import ai.indra.android.ui.UiKit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple, fast whiteboard: freehand strokes, colour palette and undo, with the
 * drawing saved as a PNG inside the app's private storage (no storage permission
 * needed, shared only when the user taps share).
 */
public class WhiteboardActivity extends AppCompatActivity {

    private DrawingView board;
    private TextView hint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout column = UiKit.vertical(this);
        column.setBackgroundColor(UiKit.BG);
        column.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 12), UiKit.dp(this, 12), UiKit.dp(this, 12));

        column.addView(UiKit.title(this, "Whiteboard"));
        hint = UiKit.subtitle(this, "Draw with your finger. Everything stays on this device until you share it.");
        column.addView(hint);

        board = new DrawingView();
        column.addView(board, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout tools = UiKit.horizontal(this);
        int[] palette = {Color.WHITE, UiKit.ACCENT, UiKit.SECONDARY, UiKit.SUCCESS, UiKit.WARNING, UiKit.ERROR};
        for (final int colour : palette) {
            Button swatch = UiKit.smallButton(this, "●");
            swatch.setTextColor(colour);
            swatch.setContentDescription("Colour");
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    board.setColour(colour);
                }
            });
            tools.addView(swatch);
        }
        column.addView(tools);

        LinearLayout actions = UiKit.horizontal(this);
        Button thicker = UiKit.smallButton(this, "Thicker");
        thicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                board.changeWidth(4f);
            }
        });
        actions.addView(thicker);
        Button thinner = UiKit.smallButton(this, "Thinner");
        thinner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                board.changeWidth(-4f);
            }
        });
        actions.addView(thinner);
        Button undo = UiKit.smallButton(this, "Undo");
        undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                board.undo();
            }
        });
        actions.addView(undo);
        Button clear = UiKit.smallButton(this, "Clear");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                board.clear();
            }
        });
        actions.addView(clear);
        Button save = UiKit.button(this, "Save", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveDrawing();
            }
        });
        actions.addView(save);
        column.addView(actions);

        setContentView(column);
    }

    private void saveDrawing() {
        Bitmap bitmap = board.toBitmap();
        if (bitmap == null) {
            hint.setText("Draw something first, then save.");
            return;
        }
        final File file = FileExport.saveDrawing(this, bitmap, "indra-whiteboard");
        if (file == null) {
            hint.setText("The drawing could not be saved.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Saved")
                .setMessage("Your drawing is stored privately in the app.\n\n" + file.getName())
                .setPositiveButton("Share", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FileExport.shareFile(WhiteboardActivity.this, file, "image/png");
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    /** Canvas-backed drawing surface with undo history. */
    private static final class DrawingView extends View {

        private final List<Stroke> strokes = new ArrayList<Stroke>();
        private Stroke current;
        private int colour = Color.WHITE;
        private float width = 6f;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DrawingView() {
            setBackgroundColor(Color.parseColor("#05070F"));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(colour);
            paint.setStrokeWidth(width);
        }

        void setColour(int colour) {
            this.colour = colour;
        }

        void changeWidth(float delta) {
            width = Math.max(2f, Math.min(40f, width + delta));
        }

        void undo() {
            if (!strokes.isEmpty()) {
                strokes.remove(strokes.size() - 1);
                invalidate();
            }
        }

        void clear() {
            strokes.clear();
            invalidate();
        }

        Bitmap toBitmap() {
            if (getWidth() <= 0 || getHeight() <= 0) {
                return null;
            }
            Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.parseColor("#05070F"));
            for (Stroke stroke : strokes) {
                paint.setColor(stroke.colour);
                paint.setStrokeWidth(stroke.width);
                canvas.drawPath(stroke.path, paint);
            }
            return bitmap;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    current = new Stroke(colour, width);
                    current.path.moveTo(x, y);
                    current.path.lineTo(x + 0.1f, y + 0.1f);
                    strokes.add(current);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (current != null) {
                        current.path.lineTo(x, y);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    current = null;
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (Stroke stroke : strokes) {
                paint.setColor(stroke.colour);
                paint.setStrokeWidth(stroke.width);
                canvas.drawPath(stroke.path, paint);
            }
        }
    }

    /** One continuous line. */
    private static final class Stroke {
        final Path path = new Path();
        final int colour;
        final float width;

        Stroke(int colour, float width) {
            this.colour = colour;
            this.width = width;
        }
    }
}
