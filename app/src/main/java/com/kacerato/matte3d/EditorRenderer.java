package com.kacerato.matte3d;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class EditorRenderer implements GLSurfaceView.Renderer {
    public enum ViewPreset { PERSPECTIVE, FRONT, RIGHT, TOP }

    private static final String VERTEX_SHADER =
            "#version 300 es\n" +
            "layout(location=0) in vec3 aPosition;\n" +
            "layout(location=1) in vec3 aNormal;\n" +
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uModel;\n" +
            "out vec3 vNormal;\n" +
            "void main(){ gl_Position=uMVP*vec4(aPosition,1.0); vNormal=mat3(uModel)*aNormal; }\n";

    private static final String FRAGMENT_SHADER =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec3 vNormal;\n" +
            "uniform vec4 uColor;\n" +
            "out vec4 fragColor;\n" +
            "void main(){ float d=max(dot(normalize(vNormal),normalize(vec3(0.45,0.85,0.55))),0.0); float l=0.40+d*0.60; fragColor=vec4(uColor.rgb*l,uColor.a); }\n";

    private final SceneModel scene;
    private final Mesh cube = PrimitiveFactory.cube();
    private final Mesh plane = PrimitiveFactory.plane();
    private final Mesh sphere = PrimitiveFactory.sphere();
    private final Mesh cylinder = PrimitiveFactory.cylinder();
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] vp = new float[16];
    private final float[] mvp = new float[16];
    private final Object cameraLock = new Object();

    private int program;
    private int uMvp;
    private int uModel;
    private int uColor;
    private FloatBuffer grid;
    private final FloatBuffer lineBuffer = ByteBuffer.allocateDirect(12 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int gridVertexCount;
    private int viewportWidth = 1;
    private int viewportHeight = 1;

    private volatile float yaw = 38f;
    private volatile float pitch = 28f;
    private volatile float distance = 8.5f;
    private volatile float targetX = 0f;
    private volatile float targetY = 0.5f;
    private volatile float targetZ = 0f;
    private volatile boolean snapEnabled = false;

    public EditorRenderer(SceneModel scene) {
        this.scene = scene;
        buildGrid();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.085f, 0.090f, 0.096f, 1f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        uMvp = GLES30.glGetUniformLocation(program, "uMVP");
        uModel = GLES30.glGetUniformLocation(program, "uModel");
        uColor = GLES30.glGetUniformLocation(program, "uColor");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight);
        float aspect = viewportWidth / (float) viewportHeight;
        synchronized (cameraLock) {
            Matrix.perspectiveM(projection, 0, 50f, aspect, 0.05f, 120f);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        updateCamera();
        GLES30.glUseProgram(program);
        drawGrid();

        List<SceneObject> objects = scene.snapshot();
        int selectedIndex = scene.getSelectedIndex();
        for (int i = 0; i < objects.size(); i++) {
            SceneObject object = objects.get(i);
            if (!object.visible) continue;
            drawObject(meshFor(object.type), object, i == selectedIndex);
        }

        SceneObject selected = scene.getSelected();
        if (selected != null && selected.visible) drawGizmo(selected);
    }

    public void orbit(float dx, float dy) {
        yaw += dx * 0.28f;
        pitch = clamp(pitch + dy * 0.22f, -88f, 88f);
    }

    public void zoom(float scaleFactor) {
        if (scaleFactor <= 0f) return;
        distance = clamp(distance / scaleFactor, 1.3f, 45f);
    }

    public void resetCamera() {
        yaw = 38f;
        pitch = 28f;
        distance = 8.5f;
        targetX = 0f;
        targetY = 0.5f;
        targetZ = 0f;
    }

    public void setViewPreset(ViewPreset preset) {
        if (preset == ViewPreset.FRONT) {
            yaw = 0f;
            pitch = 0f;
        } else if (preset == ViewPreset.RIGHT) {
            yaw = 90f;
            pitch = 0f;
        } else if (preset == ViewPreset.TOP) {
            yaw = 0f;
            pitch = 88f;
        } else {
            yaw = 38f;
            pitch = 28f;
        }
    }

    public void focusSelected() {
        SceneObject o = scene.getSelected();
        if (o == null) return;
        targetX = o.px;
        targetY = o.py;
        targetZ = o.pz;
        float maxScale = Math.max(o.sx, Math.max(o.sy, o.sz));
        distance = clamp(Math.max(3f, maxScale * 4.2f), 2f, 30f);
    }

    public void setSnapEnabled(boolean enabled) {
        snapEnabled = enabled;
    }

    public boolean isSnapEnabled() {
        return snapEnabled;
    }

    public void beginEdit() {
        SceneObject o = scene.getSelected();
        if (o != null && !o.locked) scene.recordUndoPoint();
    }

    public boolean moveSelected(float dx, float dy) {
        SceneObject o = scene.getSelected();
        if (o == null || o.locked) return false;
        float yawRad = (float) Math.toRadians(yaw);
        float speed = clamp(distance * 0.0022f, 0.004f, 0.08f);
        float rightX = (float) Math.cos(yawRad);
        float rightZ = -(float) Math.sin(yawRad);
        float forwardX = -(float) Math.sin(yawRad);
        float forwardZ = -(float) Math.cos(yawRad);

        o.px += rightX * dx * speed + forwardX * dy * speed;
        o.pz += rightZ * dx * speed + forwardZ * dy * speed;
        if (snapEnabled) {
            o.px = snap(o.px, 0.25f);
            o.pz = snap(o.pz, 0.25f);
        }
        return true;
    }

    public boolean rotateSelected(float dx, float dy) {
        SceneObject o = scene.getSelected();
        if (o == null || o.locked) return false;
        o.ry += dx * 0.45f;
        o.rx += dy * 0.45f;
        if (snapEnabled) {
            o.ry = snap(o.ry, 15f);
            o.rx = snap(o.rx, 15f);
        }
        return true;
    }

    public boolean scaleSelected(float dx, float dy) {
        SceneObject o = scene.getSelected();
        if (o == null || o.locked) return false;
        float delta = (dx - dy) * 0.006f;
        float s = Math.max(0.05f, (o.sx + o.sy + o.sz) / 3f + delta);
        if (snapEnabled) s = Math.max(0.05f, snap(s, 0.1f));
        o.sx = s;
        o.sy = s;
        o.sz = s;
        return true;
    }

    public int pickObject(float screenX, float screenY) {
        if (viewportWidth <= 1 || viewportHeight <= 1) return -1;
        float ndcX = (2f * screenX / viewportWidth) - 1f;
        float ndcY = 1f - (2f * screenY / viewportHeight);
        float[] invVp = new float[16];
        float[] localVp = new float[16];
        synchronized (cameraLock) {
            System.arraycopy(vp, 0, localVp, 0, 16);
        }
        if (!Matrix.invertM(invVp, 0, localVp, 0)) return -1;

        float[] nearClip = {ndcX, ndcY, -1f, 1f};
        float[] farClip = {ndcX, ndcY, 1f, 1f};
        float[] nearWorld = new float[4];
        float[] farWorld = new float[4];
        Matrix.multiplyMV(nearWorld, 0, invVp, 0, nearClip, 0);
        Matrix.multiplyMV(farWorld, 0, invVp, 0, farClip, 0);
        divideByW(nearWorld);
        divideByW(farWorld);

        List<SceneObject> objects = scene.snapshot();
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < objects.size(); i++) {
            SceneObject o = objects.get(i);
            if (!o.visible) continue;
            float hit = intersectObject(o, nearWorld, farWorld);
            if (hit >= 0f && hit < bestDistance) {
                bestDistance = hit;
                best = i;
            }
        }

        if (best >= 0) scene.select(best);
        else scene.clearSelection();
        return best;
    }

    private float intersectObject(SceneObject o, float[] nearWorld, float[] farWorld) {
        buildModelMatrix(o, model);
        float[] invModel = new float[16];
        if (!Matrix.invertM(invModel, 0, model, 0)) return -1f;

        float[] localNear = transformPoint(invModel, nearWorld[0], nearWorld[1], nearWorld[2]);
        float[] localFar = transformPoint(invModel, farWorld[0], farWorld[1], farWorld[2]);
        float dx = localFar[0] - localNear[0];
        float dy = localFar[1] - localNear[1];
        float dz = localFar[2] - localNear[2];

        float minY = o.type == SceneObject.Type.PLANE ? -0.08f : -0.5f;
        float maxY = o.type == SceneObject.Type.PLANE ? 0.08f : 0.5f;
        return rayAabb(localNear[0], localNear[1], localNear[2], dx, dy, dz,
                -0.5f, minY, -0.5f, 0.5f, maxY, 0.5f);
    }

    private void updateCamera() {
        synchronized (cameraLock) {
            float yawRad = (float) Math.toRadians(yaw);
            float pitchRad = (float) Math.toRadians(pitch);
            float cp = (float) Math.cos(pitchRad);
            float x = targetX + distance * cp * (float) Math.sin(yawRad);
            float y = targetY + distance * (float) Math.sin(pitchRad);
            float z = targetZ + distance * cp * (float) Math.cos(yawRad);
            Matrix.setLookAtM(view, 0, x, y, z, targetX, targetY, targetZ, 0f, 1f, 0f);
            Matrix.multiplyMM(vp, 0, projection, 0, view, 0);
        }
    }

    private void drawObject(Mesh mesh, SceneObject object, boolean selected) {
        buildModelMatrix(object, model);
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);

        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0);

        if (selected) {
            GLES30.glUniform4f(uColor, 0.82f, 0.84f, 0.86f, 1f);
        } else if (object.locked) {
            GLES30.glUniform4f(uColor, 0.29f, 0.30f, 0.32f, 1f);
        } else {
            switch (object.type) {
                case PLANE:
                    GLES30.glUniform4f(uColor, 0.30f, 0.31f, 0.33f, 1f);
                    break;
                case SPHERE:
                    GLES30.glUniform4f(uColor, 0.43f, 0.45f, 0.47f, 1f);
                    break;
                case CYLINDER:
                    GLES30.glUniform4f(uColor, 0.39f, 0.41f, 0.43f, 1f);
                    break;
                default:
                    GLES30.glUniform4f(uColor, 0.47f, 0.49f, 0.51f, 1f);
                    break;
            }
        }

        mesh.vertices.position(0);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, mesh.vertices);
        mesh.vertices.position(3);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, mesh.vertices);
        mesh.indices.position(0);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_SHORT, mesh.indices);
    }

    private void drawGrid() {
        Matrix.setIdentityM(model, 0);
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES30.glUniform4f(uColor, 0.27f, 0.29f, 0.31f, 1f);

        grid.position(0);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, grid);
        grid.position(3);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, grid);
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, gridVertexCount);

        drawWorldLine(-10f, 0.005f, 0f, 10f, 0.005f, 0f, 0.34f, 0.16f, 0.16f);
        drawWorldLine(0f, 0.005f, -10f, 0f, 0.005f, 10f, 0.16f, 0.20f, 0.36f);
    }

    private void drawGizmo(SceneObject o) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        float size = clamp(distance * 0.12f, 0.7f, 2.2f);
        drawWorldLine(o.px, o.py, o.pz, o.px + size, o.py, o.pz, 0.86f, 0.24f, 0.23f);
        drawWorldLine(o.px, o.py, o.pz, o.px, o.py + size, o.pz, 0.28f, 0.78f, 0.35f);
        drawWorldLine(o.px, o.py, o.pz, o.px, o.py, o.pz + size, 0.26f, 0.45f, 0.90f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    private void drawWorldLine(float x0, float y0, float z0, float x1, float y1, float z1,
                               float r, float g, float b) {
        float[] data = {
                x0,y0,z0, 0,1,0,
                x1,y1,z1, 0,1,0
        };
        lineBuffer.position(0);
        lineBuffer.put(data);
        lineBuffer.position(0);

        Matrix.setIdentityM(model, 0);
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES30.glUniform4f(uColor, r, g, b, 1f);

        lineBuffer.position(0);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, lineBuffer);
        lineBuffer.position(3);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, lineBuffer);
        GLES30.glLineWidth(3f);
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, 2);
    }

    private void buildGrid() {
        int half = 12;
        int lines = half * 2 + 1;
        float[] data = new float[lines * 4 * 6];
        int p = 0;
        for (int i = -half; i <= half; i++) {
            p = putVertex(data, p, i, 0f, -half);
            p = putVertex(data, p, i, 0f, half);
            p = putVertex(data, p, -half, 0f, i);
            p = putVertex(data, p, half, 0f, i);
        }
        gridVertexCount = data.length / 6;
        grid = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        grid.put(data).position(0);
    }

    private Mesh meshFor(SceneObject.Type type) {
        switch (type) {
            case PLANE: return plane;
            case SPHERE: return sphere;
            case CYLINDER: return cylinder;
            default: return cube;
        }
    }

    private static void buildModelMatrix(SceneObject object, float[] out) {
        Matrix.setIdentityM(out, 0);
        Matrix.translateM(out, 0, object.px, object.py, object.pz);
        Matrix.rotateM(out, 0, object.rx, 1f, 0f, 0f);
        Matrix.rotateM(out, 0, object.ry, 0f, 1f, 0f);
        Matrix.rotateM(out, 0, object.rz, 0f, 0f, 1f);
        Matrix.scaleM(out, 0, object.sx, object.sy, object.sz);
    }

    private static float[] transformPoint(float[] matrix, float x, float y, float z) {
        float[] in = {x, y, z, 1f};
        float[] out = new float[4];
        Matrix.multiplyMV(out, 0, matrix, 0, in, 0);
        divideByW(out);
        return new float[]{out[0], out[1], out[2]};
    }

    private static void divideByW(float[] v) {
        float w = Math.abs(v[3]) < 0.000001f ? 1f : v[3];
        v[0] /= w;
        v[1] /= w;
        v[2] /= w;
        v[3] = 1f;
    }

    private static float rayAabb(float ox, float oy, float oz, float dx, float dy, float dz,
                                 float minX, float minY, float minZ,
                                 float maxX, float maxY, float maxZ) {
        float tMin = 0f;
        float tMax = Float.MAX_VALUE;
        float[] o = {ox, oy, oz};
        float[] d = {dx, dy, dz};
        float[] min = {minX, minY, minZ};
        float[] max = {maxX, maxY, maxZ};

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 0.000001f) {
                if (o[axis] < min[axis] || o[axis] > max[axis]) return -1f;
            } else {
                float inv = 1f / d[axis];
                float t1 = (min[axis] - o[axis]) * inv;
                float t2 = (max[axis] - o[axis]) * inv;
                if (t1 > t2) {
                    float temp = t1;
                    t1 = t2;
                    t2 = temp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMax < tMin) return -1f;
            }
        }
        return tMin;
    }

    private int putVertex(float[] out, int p, float x, float y, float z) {
        out[p++] = x; out[p++] = y; out[p++] = z;
        out[p++] = 0f; out[p++] = 1f; out[p++] = 0f;
        return p;
    }

    private static int linkProgram(String vs, String fs) {
        int vertex = compileShader(GLES30.GL_VERTEX_SHADER, vs);
        int fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fs);
        int program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vertex);
        GLES30.glAttachShader(program, fragment);
        GLES30.glLinkProgram(program);
        int[] status = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            throw new RuntimeException("OpenGL program link failed: " + GLES30.glGetProgramInfoLog(program));
        }
        GLES30.glDeleteShader(vertex);
        GLES30.glDeleteShader(fragment);
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            throw new RuntimeException("OpenGL shader compile failed: " + GLES30.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private static float snap(float value, float step) {
        return Math.round(value / step) * step;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
