package com.kacerato.matte3d;

public final class PrimitiveFactory {
    private PrimitiveFactory() {}

    public static Mesh cube() {
        float[] v = {
                -0.5f,-0.5f, 0.5f, 0,0,1,   0.5f,-0.5f, 0.5f, 0,0,1,   0.5f, 0.5f, 0.5f, 0,0,1,  -0.5f, 0.5f, 0.5f, 0,0,1,
                 0.5f,-0.5f,-0.5f, 0,0,-1, -0.5f,-0.5f,-0.5f, 0,0,-1, -0.5f, 0.5f,-0.5f, 0,0,-1,  0.5f, 0.5f,-0.5f, 0,0,-1,
                -0.5f,-0.5f,-0.5f,-1,0,0,  -0.5f,-0.5f, 0.5f,-1,0,0,  -0.5f, 0.5f, 0.5f,-1,0,0,  -0.5f, 0.5f,-0.5f,-1,0,0,
                 0.5f,-0.5f, 0.5f, 1,0,0,   0.5f,-0.5f,-0.5f, 1,0,0,   0.5f, 0.5f,-0.5f, 1,0,0,   0.5f, 0.5f, 0.5f, 1,0,0,
                -0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f,-0.5f, 0,1,0,  -0.5f, 0.5f,-0.5f, 0,1,0,
                -0.5f,-0.5f,-0.5f, 0,-1,0,  0.5f,-0.5f,-0.5f, 0,-1,0,  0.5f,-0.5f, 0.5f, 0,-1,0, -0.5f,-0.5f, 0.5f, 0,-1,0
        };
        short[] i = {
                0,1,2, 0,2,3, 4,5,6, 4,6,7, 8,9,10, 8,10,11,
                12,13,14, 12,14,15, 16,17,18, 16,18,19, 20,21,22, 20,22,23
        };
        return new Mesh(v, i);
    }

    public static Mesh plane() {
        float[] v = {
                -0.5f,0,-0.5f, 0,1,0,
                 0.5f,0,-0.5f, 0,1,0,
                 0.5f,0, 0.5f, 0,1,0,
                -0.5f,0, 0.5f, 0,1,0
        };
        short[] i = {0,2,1, 0,3,2};
        return new Mesh(v, i);
    }

    public static Mesh sphere() {
        final int segments = 20;
        final int rings = 12;
        final int columns = segments + 1;
        float[] v = new float[(rings + 1) * columns * 6];
        short[] idx = new short[rings * segments * 6];

        int vp = 0;
        for (int r = 0; r <= rings; r++) {
            float vr = r / (float) rings;
            float phi = (float) (Math.PI * vr);
            float y = (float) Math.cos(phi);
            float radius = (float) Math.sin(phi);
            for (int s = 0; s <= segments; s++) {
                float u = s / (float) segments;
                float theta = (float) (Math.PI * 2.0 * u);
                float x = radius * (float) Math.cos(theta);
                float z = radius * (float) Math.sin(theta);
                v[vp++] = x * 0.5f;
                v[vp++] = y * 0.5f;
                v[vp++] = z * 0.5f;
                v[vp++] = x;
                v[vp++] = y;
                v[vp++] = z;
            }
        }

        int ip = 0;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                short a = (short) (r * columns + s);
                short b = (short) (a + columns);
                short c = (short) (b + 1);
                short d = (short) (a + 1);
                idx[ip++] = a; idx[ip++] = d; idx[ip++] = b;
                idx[ip++] = d; idx[ip++] = c; idx[ip++] = b;
            }
        }
        return new Mesh(v, idx);
    }

    public static Mesh cylinder() {
        final int segments = 20;
        final int sideVerts = (segments + 1) * 2;
        final int capVerts = (segments + 2) * 2;
        float[] v = new float[(sideVerts + capVerts) * 6];
        short[] idx = new short[segments * 12];

        int vp = 0;
        for (int s = 0; s <= segments; s++) {
            float a = (float) (Math.PI * 2.0 * s / segments);
            float x = (float) Math.cos(a);
            float z = (float) Math.sin(a);
            v[vp++] = x * 0.5f; v[vp++] = -0.5f; v[vp++] = z * 0.5f;
            v[vp++] = x; v[vp++] = 0f; v[vp++] = z;
            v[vp++] = x * 0.5f; v[vp++] =  0.5f; v[vp++] = z * 0.5f;
            v[vp++] = x; v[vp++] = 0f; v[vp++] = z;
        }

        short topCenter = (short) sideVerts;
        v[vp++] = 0f; v[vp++] = 0.5f; v[vp++] = 0f;
        v[vp++] = 0f; v[vp++] = 1f; v[vp++] = 0f;
        for (int s = 0; s <= segments; s++) {
            float a = (float) (Math.PI * 2.0 * s / segments);
            v[vp++] = (float) Math.cos(a) * 0.5f; v[vp++] = 0.5f; v[vp++] = (float) Math.sin(a) * 0.5f;
            v[vp++] = 0f; v[vp++] = 1f; v[vp++] = 0f;
        }

        short bottomCenter = (short) (sideVerts + segments + 2);
        v[vp++] = 0f; v[vp++] = -0.5f; v[vp++] = 0f;
        v[vp++] = 0f; v[vp++] = -1f; v[vp++] = 0f;
        for (int s = 0; s <= segments; s++) {
            float a = (float) (Math.PI * 2.0 * s / segments);
            v[vp++] = (float) Math.cos(a) * 0.5f; v[vp++] = -0.5f; v[vp++] = (float) Math.sin(a) * 0.5f;
            v[vp++] = 0f; v[vp++] = -1f; v[vp++] = 0f;
        }

        int ip = 0;
        for (int s = 0; s < segments; s++) {
            short b0 = (short) (s * 2);
            short t0 = (short) (b0 + 1);
            short b1 = (short) (b0 + 2);
            short t1 = (short) (b0 + 3);
            idx[ip++] = b0; idx[ip++] = t0; idx[ip++] = b1;
            idx[ip++] = t0; idx[ip++] = t1; idx[ip++] = b1;

            short top0 = (short) (topCenter + 1 + s);
            short top1 = (short) (top0 + 1);
            idx[ip++] = topCenter; idx[ip++] = top1; idx[ip++] = top0;

            short bottom0 = (short) (bottomCenter + 1 + s);
            short bottom1 = (short) (bottom0 + 1);
            idx[ip++] = bottomCenter; idx[ip++] = bottom0; idx[ip++] = bottom1;
        }
        return new Mesh(v, idx);
    }
}
