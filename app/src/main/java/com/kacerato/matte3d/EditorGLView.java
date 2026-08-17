package com.kacerato.matte3d;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public final class EditorGLView extends GLSurfaceView {
    private final EditorRenderer editorRenderer;
    private final ScaleGestureDetector scaleDetector;
    private float lastX;
    private float lastY;

    public EditorGLView(Context context, EditorRenderer renderer) {
        super(context);
        editorRenderer = renderer;
        setEGLContextClientVersion(3);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                editorRenderer.zoom(detector.getScaleFactor());
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float x = event.getX();
                    float y = event.getY();
                    editorRenderer.orbit(x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;
                    return true;
            }
        }
        return true;
    }
}
