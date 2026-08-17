package com.kacerato.matte3d;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

public final class EditorGLView extends GLSurfaceView {
    public enum ToolMode { NAVIGATE, MOVE, ROTATE, SCALE }

    public interface Listener {
        void onSelectionChanged();
        void onTransformChanged();
    }

    private final EditorRenderer editorRenderer;
    private final ScaleGestureDetector scaleDetector;
    private final float touchSlop;
    private ToolMode toolMode = ToolMode.NAVIGATE;
    private Listener listener;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean moved;
    private boolean editStarted;
    private boolean hadMultiTouch;
    private long lastTapTime;

    public EditorGLView(Context context, EditorRenderer renderer) {
        super(context);
        editorRenderer = renderer;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setEGLContextClientVersion(3);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setFocusable(true);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        editorRenderer.zoom(detector.getScaleFactor());
                        return true;
                    }
                });
    }

    public void setToolMode(ToolMode mode) {
        toolMode = mode == null ? ToolMode.NAVIGATE : mode;
    }

    public ToolMode getToolMode() {
        return toolMode;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        if (event.getPointerCount() > 1) hadMultiTouch = true;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                moved = false;
                editStarted = false;
                hadMultiTouch = false;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                hadMultiTouch = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() != 1 || scaleDetector.isInProgress()) return true;

                float x = event.getX();
                float y = event.getY();
                float dx = x - lastX;
                float dy = y - lastY;
                if (!moved) {
                    float totalX = x - downX;
                    float totalY = y - downY;
                    moved = Math.hypot(totalX, totalY) >= touchSlop;
                }

                if (moved) {
                    if (toolMode == ToolMode.NAVIGATE) {
                        editorRenderer.orbit(dx, dy);
                    } else {
                        if (!editStarted) {
                            editorRenderer.beginEdit();
                            editStarted = true;
                        }
                        if (toolMode == ToolMode.MOVE) editorRenderer.moveSelected(dx, dy);
                        else if (toolMode == ToolMode.ROTATE) editorRenderer.rotateSelected(dx, dy);
                        else if (toolMode == ToolMode.SCALE) editorRenderer.scaleSelected(dx, dy);
                    }
                }

                lastX = x;
                lastY = y;
                return true;

            case MotionEvent.ACTION_UP:
                if (!moved && !hadMultiTouch) {
                    int selected = editorRenderer.pickObject(event.getX(), event.getY());
                    long now = System.currentTimeMillis();
                    if (selected >= 0 && now - lastTapTime < 300L) {
                        editorRenderer.focusSelected();
                    }
                    lastTapTime = now;
                    if (listener != null) listener.onSelectionChanged();
                } else if (editStarted && listener != null) {
                    listener.onTransformChanged();
                }
                editStarted = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                editStarted = false;
                return true;

            default:
                return true;
        }
    }
}
