package com.kacerato.matte3d;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public final class Mesh {
    public final FloatBuffer vertices;
    public final ShortBuffer indices;
    public final int indexCount;

    public Mesh(float[] vertexData, short[] indexData) {
        vertices = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertices.put(vertexData).position(0);

        indices = ByteBuffer.allocateDirect(indexData.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        indices.put(indexData).position(0);
        indexCount = indexData.length;
    }
}
