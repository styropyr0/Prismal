package com.matrix.prismal.utils;

import android.content.Context;
import android.opengl.GLES20;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * ShaderUtils class for loading and compiling shaders.
 *
 * @author Saurav Sajeev
 */

public class ShaderUtils {

    /**
     * Loads a shader from an asset file.
     * @param context Context
     * @param filename Asset file name
     * @return Shader code as a string
     */
    public static String loadFromAssets(Context context, String filename) {
        StringBuilder sb = new StringBuilder();
        try {
            InputStream is = context.getAssets().open(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            is.close();
        } catch (Exception e) {
            throw new RuntimeException("Error reading shader file: " + filename, e);
        }
        return sb.toString();
    }

    /**
     * Loads a shader from a raw resource.
     * @param context Context
     * @param resourceId Raw resource ID
     * @return Shader code as a string
     */
    public static String loadShaderSource(Context context, int resourceId) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getResources().openRawResource(resourceId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading shader resource: " + resourceId, e);
        }
        return sb.toString();
    }

    /**
     * Compiles a shader.
     * @param type Shader type (GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER)
     * @param shaderCode Shader code
     * @return Shader ID
     */
    public static int compileShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile error: " + error);
        }
        return shader;
    }

    /**
     * Links a vertex and fragment shader into a program.
     * @param vertexShader Vertex shader ID
     * @param fragmentShader Fragment shader ID
     * @return Program ID
     */
    public static int linkProgram(int vertexShader, int fragmentShader) {
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String error = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("Program link error: " + error);
        }
        return program;
    }
}
