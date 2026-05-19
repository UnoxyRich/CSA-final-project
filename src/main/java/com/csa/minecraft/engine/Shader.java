package com.csa.minecraft.engine;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public class Shader {
    private final int program;
    private final Map<String,Integer> locs = new HashMap<>();

    public Shader(String vertSrc, String fragSrc) {
        int v = compile(GL_VERTEX_SHADER, vertSrc);
        int f = compile(GL_FRAGMENT_SHADER, fragSrc);
        program = glCreateProgram();
        glAttachShader(program, v); glAttachShader(program, f);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0)
            throw new RuntimeException("link: " + glGetProgramInfoLog(program));
        glDeleteShader(v); glDeleteShader(f);
    }
    private int compile(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src); glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == 0)
            throw new RuntimeException("compile: " + glGetShaderInfoLog(s));
        return s;
    }
    public void use() { glUseProgram(program); }
    private int loc(String n) { return locs.computeIfAbsent(n, k -> glGetUniformLocation(program, k)); }
    public void setMat4(String n, Matrix4f m) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            FloatBuffer b = s.mallocFloat(16); m.get(b);
            glUniformMatrix4fv(loc(n), false, b);
        }
    }
    public void setInt(String n, int v) { glUniform1i(loc(n), v); }
    public void setFloat(String n, float v) { glUniform1f(loc(n), v); }
    public void setVec2(String n, float x, float y) { glUniform2f(loc(n), x, y); }
    public void setVec3(String n, float x, float y, float z) { glUniform3f(loc(n), x, y, z); }
    public void setVec4(String n, float x, float y, float z, float w) { glUniform4f(loc(n), x, y, z, w); }
}
