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
            "void main(){ float d=max(dot(normalize(vNormal),normalize(vec3(0.45,0.85,0.55))),0.0); float l=0.42+d*0.58; fragColor=vec4(uColor.rgb*l,uColor.a); }\n";

    private final SceneModel scene;
    private final Mesh cube = PrimitiveFactory.cube();
    private final Mesh plane = PrimitiveFactory.plane();
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] vp = new float[16];
    private final float[] mvp = new float[16];

    private int program;
    private int uMvp;
    private int uModel;
    private int uColor;
    private FloatBuffer grid;
    private int gridVertexCount;

    private volatile float yaw = 38f;
    private volatile float pitch = 28f;
    private volatile float distance = 8.5f;

    public EditorRenderer(SceneModel scene) {
        this.scene = scene;
        buildGrid();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.105f, 0.112f, 0.118f, 1f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        uMvp = GLES30.glGetUniformLocation(program, "uMVP");
        uModel = GLES30.glGetUniformLocation(program, "uModel");
        uColor = GLES30.glGetUniformLocation(program, "uColor");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        float aspect = width / (float) Math.max(1, height);
        Matrix.perspectiveM(projection, 0, 50f, aspect, 0.05f, 100f);
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
            Mesh mesh = object.type == SceneObject.Type.CUBE ? cube : plane;
            drawObject(mesh, object, i == selectedIndex);
        }
    }

    public void orbit(float dx, float dy) {
        yaw += dx * 0.28f;
        pitch = clamp(pitch + dy * 0.22f, -80f, 80f);
    }

    public void zoom(float scaleFactor) {
        if (scaleFactor <= 0f) return;
        distance = clamp(distance / scaleFactor, 2f, 35f);
    }

    public void resetCamera() {
        yaw = 38f;
        pitch = 28f;
        distance = 8.5f;
    }

    private void updateCamera() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cp = (float) Math.cos(pitchRad);
        float x = distance * cp * (float) Math.sin(yawRad);
        float y = distance * (float) Math.sin(pitchRad) + 1.2f;
        float z = distance * cp * (float) Math.cos(yawRad);
        Matrix.setLookAtM(view, 0, x, y, z, 0f, 0.5f, 0f, 0f, 1f, 0f);
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0);
    }

    private void drawObject(Mesh mesh, SceneObject object, boolean selected) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, object.px, object.py, object.pz);
        Matrix.rotateM(model, 0, object.rx, 1f, 0f, 0f);
        Matrix.rotateM(model, 0, object.ry, 0f, 1f, 0f);
        Matrix.rotateM(model, 0, object.rz, 0f, 0f, 1f);
        Matrix.scaleM(model, 0, object.sx, object.sy, object.sz);
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);

        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0);
        if (selected) {
            GLES30.glUniform4f(uColor, 0.80f, 0.83f, 0.85f, 1f);
        } else if (object.type == SceneObject.Type.CUBE) {
            GLES30.glUniform4f(uColor, 0.47f, 0.49f, 0.51f, 1f);
        } else {
            GLES30.glUniform4f(uColor, 0.33f, 0.35f, 0.37f, 1f);
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
        GLES30.glUniform4f(uColor, 0.44f, 0.46f, 0.48f, 1f);

        grid.position(0);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, grid);
        grid.position(3);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, grid);
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, gridVertexCount);
    }

    private void buildGrid() {
        int half = 10;
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
        if (status[0] == 0) throw new RuntimeException("OpenGL program link failed: " + GLES30.glGetProgramInfoLog(program));
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
        if (status[0] == 0) throw new RuntimeException("OpenGL shader compile failed: " + GLES30.glGetShaderInfoLog(shader));
        return shader;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
