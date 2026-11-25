package com.moiclathy.screencoach.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.moiclathy.screencoach.util.BitmapUtils;

import java.nio.ByteBuffer;

/**
 * MediaProjection capture path. The virtual display is downscaled before
 * frames are produced so each 3-second model request stays small.
 */
@SuppressLint("WrongConstant")
public final class ProjectionCapture {
    public interface Callback {
        void onCaptured(Bitmap bitmap);
        void onError(String message);
    }

    public interface Listener {
        void onStopped();
    }

    private static final int MAX_EDGE = 1440;
    private static final long SEED_INTERVAL_MS = 1000L;

    private final MediaProjection projection;
    private final ImageReader reader;
    private final VirtualDisplay virtualDisplay;
    private final HandlerThread thread;
    private final Handler handler;
    private final Handler mainHandler;
    private final Listener listener;
    private Bitmap latest;
    private long lastSeedAt;
    private volatile Pending pending;
    private volatile boolean released;

    private final Runnable timeout = new Runnable() {
        @Override public void run() {
            if (released || pending == null) return;
            Pending request = pending;
            pending = null;
            Bitmap copy = latest != null && !latest.isRecycled()
                    ? latest.copy(Bitmap.Config.ARGB_8888, false) : null;
            if (copy == null) {
                deliverError(request, "截屏尚未就绪，请稍后重试");
            } else {
                deliverBitmap(request, copy);
            }
        }
    };

    private ProjectionCapture(Context context, int resultCode, Intent data,
                              Listener listener) throws Exception {
        this.listener = listener;
        MediaProjectionManager manager = (MediaProjectionManager)
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) throw new IllegalStateException("系统不支持屏幕采集");
        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) throw new IllegalStateException("屏幕采集授权无效");

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null || windowManager.getDefaultDisplay() == null) {
            throw new IllegalStateException("无法读取屏幕尺寸");
        }
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int screenWidth = Math.max(1, metrics.widthPixels);
        int screenHeight = Math.max(1, metrics.heightPixels);
        int longest = Math.max(screenWidth, screenHeight);
        float scale = longest > MAX_EDGE ? MAX_EDGE / (float) longest : 1f;
        int width = Math.max(2, Math.round(screenWidth * scale));
        int height = Math.max(2, Math.round(screenHeight * scale));
        int density = Math.max(1, Math.round(metrics.densityDpi * scale));

        thread = new HandlerThread("ScreenCoachCapture");
        thread.start();
        handler = new Handler(thread.getLooper());
        mainHandler = new Handler(android.os.Looper.getMainLooper());
        // RGBA_8888 is supported by the screen-capture ImageReader on the
        // devices this project targets; lint's ImageFormat allow-list is narrower.
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(this::onImageAvailable, handler);
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                if (!released) {
                    mainHandler.post(() -> {
                        if (!released && listener != null) listener.onStopped();
                    });
                }
            }
        }, handler);
        virtualDisplay = projection.createVirtualDisplay("ScreenCoach",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, handler);
        if (virtualDisplay == null) {
            throw new IllegalStateException("无法创建屏幕采集显示");
        }
    }

    public static ProjectionCapture start(Context context, int resultCode, Intent data,
                                          Listener listener) throws Exception {
        return new ProjectionCapture(context, resultCode, data, listener);
    }

    public void capture(Callback callback) {
        handler.post(() -> {
            if (released) {
                callback.onError("屏幕采集已停止");
                return;
            }
            if (pending != null) {
                callback.onError("上一张截图尚未完成");
                return;
            }
            pending = new Pending(callback);
            handler.removeCallbacks(timeout);
            handler.postDelayed(timeout, 800L);
        });
    }

    private void onImageAvailable(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null || released) return;
            long now = android.os.SystemClock.elapsedRealtime();
            boolean needed = pending != null || latest == null
                    || now - lastSeedAt >= SEED_INTERVAL_MS;
            if (!needed) return;
            Bitmap bitmap = imageToBitmap(image);
            if (bitmap == null) {
                if (pending != null) {
                    Pending request = pending;
                    pending = null;
                    deliverError(request, "截屏转换失败");
                }
                return;
            }
            if (latest != null && !latest.isRecycled()) latest.recycle();
            latest = bitmap;
            lastSeedAt = now;
            if (pending != null) {
                Pending request = pending;
                pending = null;
                handler.removeCallbacks(timeout);
                Bitmap copy = latest.copy(Bitmap.Config.ARGB_8888, false);
                if (copy == null) {
                    deliverError(request, "截屏复制失败");
                } else {
                    deliverBitmap(request, copy);
                }
            }
        } catch (Throwable ignored) {
            if (pending != null) {
                Pending request = pending;
                pending = null;
                deliverError(request, "截屏读取失败");
            }
        } finally {
            if (image != null) {
                try { image.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int width = image.getWidth();
            int height = image.getHeight();
            int paddedWidth = rowStride / Math.max(1, pixelStride);
            Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
            padded.recycle();
            return BitmapUtils.maxDimension(cropped, MAX_EDGE);
        } catch (Throwable t) {
            return null;
        }
    }

    private void deliverBitmap(Pending request, Bitmap bitmap) {
        mainHandler.post(() -> {
            if (released) {
                bitmap.recycle();
                request.callback.onError("屏幕采集已停止");
            } else {
                request.callback.onCaptured(bitmap);
            }
        });
    }

    private void deliverError(Pending request, String message) {
        mainHandler.post(() -> request.callback.onError(message));
    }

    public void release() {
        if (released) return;
        released = true;
        if (pending != null) {
            Pending request = pending;
            pending = null;
            request.callback.onError("屏幕采集已停止");
        }
        handler.post(() -> {
            try { reader.setOnImageAvailableListener(null, null); } catch (Throwable ignored) { }
            try { reader.close(); } catch (Throwable ignored) { }
            try { virtualDisplay.release(); } catch (Throwable ignored) { }
            try { projection.stop(); } catch (Throwable ignored) { }
            if (latest != null) {
                latest.recycle();
                latest = null;
            }
            thread.quitSafely();
        });
    }

    private static final class Pending {
        final Callback callback;
        Pending(Callback callback) { this.callback = callback; }
    }
}
