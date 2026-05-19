package com.csa.minecraft.engine;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Mesh {
    private final int vao, vbo;
    private int vertexCount;

    public Mesh() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
    }

    /** Upload interleaved data. Strides expressed in floats. */
    public void upload(float[] data, int[] sizes) {
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        int stride = 0;
        for (int s : sizes) stride += s;
        int offset = 0;
        for (int i = 0; i < sizes.length; i++) {
            glVertexAttribPointer(i, sizes[i], GL_FLOAT, false, stride * 4, offset * 4L);
            glEnableVertexAttribArray(i);
            offset += sizes[i];
        }
        vertexCount = data.length / stride;
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void draw() {
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public int vertexCount() { return vertexCount; }
    public void destroy() { glDeleteBuffers(vbo); glDeleteVertexArrays(vao); }
}
