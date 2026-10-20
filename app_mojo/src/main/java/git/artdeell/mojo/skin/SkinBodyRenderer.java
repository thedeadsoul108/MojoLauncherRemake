package git.artdeell.mojo.skin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * A tiny front-view paper-doll renderer for 64x32 and 64x64 skins. It is the fallback when a
 * Crafatar render is unavailable, and the live preview for a skin the user has not uploaded yet.
 */
public final class SkinBodyRenderer {
    private static final int SKIN_HEIGHT_MODERN = 64;

    private SkinBodyRenderer() {
    }

    public static Bitmap render(byte[] png, boolean slim, int scale) {
        if (png == null || scale <= 0) return null;
        Bitmap source = BitmapFactory.decodeByteArray(png, 0, png.length);
        if (source == null) return null;
        try {
            int width = 16 * scale;
            int height = 32 * scale;
            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint();
            paint.setFilterBitmap(false);
            boolean modern = source.getHeight() >= SKIN_HEIGHT_MODERN;
            boolean slimArms = slim;

            // Legs behind the body.
            drawPart(canvas, source, paint, 4, 20, 4, 12, 4, 20, 4, 12, scale);
            if (modern) {
                drawPart(canvas, source, paint, 20, 52, 4, 12, 8, 20, 4, 12, scale);
            } else {
                drawMirrored(canvas, source, paint, 4, 20, 4, 12, 8, 20, 4, 12, scale);
            }

            // Torso.
            drawPart(canvas, source, paint, 20, 20, 8, 12, 4, 8, 8, 12, scale);

            // Arms. Slim skins use a 3px arm and leave a one-pixel gap next to the torso.
            int armWidth = slimArms ? 3 : 4;
            int armLeft = slimArms ? 0 : 0;
            int armRight = slimArms ? 13 : 12;
            drawPart(canvas, source, paint, 44, 20, armWidth, 12, armLeft, 8, armWidth, 12, scale);
            if (modern) {
                drawPart(canvas, source, paint, 36, 52, armWidth, 12, armRight, 8, armWidth, 12, scale);
            } else {
                drawMirrored(canvas, source, paint, 44, 20, armWidth, 12, armRight, 8, armWidth, 12, scale);
            }

            // Head over the torso.
            drawPart(canvas, source, paint, 8, 8, 8, 8, 4, 0, 8, 8, scale);

            // Overlay layers.
            drawPart(canvas, source, paint, 20, 36, 8, 12, 4, 8, 8, 12, scale);
            drawPart(canvas, source, paint, 4, 36, 4, 12, 4, 20, 4, 12, scale);
            if (modern) {
                drawPart(canvas, source, paint, 4, 52, 4, 12, 8, 20, 4, 12, scale);
            } else {
                drawMirrored(canvas, source, paint, 4, 36, 4, 12, 8, 20, 4, 12, scale);
            }
            drawPart(canvas, source, paint, 44, 36, armWidth, 12, armLeft, 8, armWidth, 12, scale);
            if (modern) {
                drawPart(canvas, source, paint, 52, 52, armWidth, 12, armRight, 8, armWidth, 12, scale);
            } else {
                drawMirrored(canvas, source, paint, 44, 36, armWidth, 12, armRight, 8, armWidth, 12, scale);
            }
            drawPart(canvas, source, paint, 40, 8, 8, 8, 4, 0, 8, 8, scale);
            return output;
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            source.recycle();
        }
    }

    private static void drawPart(Canvas canvas, Bitmap source, Paint paint,
                                 int srcX, int srcY, int srcW, int srcH,
                                 int dstX, int dstY, int dstW, int dstH, int scale) {
        canvas.drawBitmap(source,
                new Rect(srcX, srcY, srcX + srcW, srcY + srcH),
                new Rect(dstX * scale, dstY * scale,
                        (dstX + dstW) * scale, (dstY + dstH) * scale),
                paint);
    }

    private static void drawMirrored(Canvas canvas, Bitmap source, Paint paint,
                                     int srcX, int srcY, int srcW, int srcH,
                                     int dstX, int dstY, int dstW, int dstH, int scale) {
        canvas.save();
        canvas.translate((dstX + dstW) * scale, dstY * scale);
        canvas.scale(-1f, 1f);
        canvas.drawBitmap(source,
                new Rect(srcX, srcY, srcX + srcW, srcY + srcH),
                new Rect(0, 0, dstW * scale, dstH * scale),
                paint);
        canvas.restore();
    }
}
