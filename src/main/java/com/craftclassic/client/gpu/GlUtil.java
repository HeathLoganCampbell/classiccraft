package com.craftclassic.client.gpu;

import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.*;

final class GlUtil
{
    private GlUtil() {}

    static String resource(String path)
    {
        try (InputStream in = GlUtil.class.getClassLoader().getResourceAsStream(path))
        {
            if (in == null)
                throw new IllegalStateException("Missing classpath resource: " + path);

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private static int compile(int type, String source, String label)
    {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE)
            throw new IllegalStateException(label + " failed to compile:\n" + glGetShaderInfoLog(shader));

        return shader;
    }

    static int program(String vertexPath, String fragmentPath)
    {
        int vertex = compile(GL_VERTEX_SHADER, resource(vertexPath), vertexPath);
        int fragment = compile(GL_FRAGMENT_SHADER, resource(fragmentPath), fragmentPath);

        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);

        glDeleteShader(vertex);
        glDeleteShader(fragment);

        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new IllegalStateException("Link failed for " + fragmentPath + ":\n" + glGetProgramInfoLog(program));

        return program;
    }

    /**
     * Loads a classpath PNG into an RGBA8 texture with nearest filtering.
     * When {@code flipY} is set the rows are reversed so texel row 0 is the top
     * of the image, which is what the CPU renderer's (int) casts assume.
     */
    static int texture2D(String resourcePath, boolean flipY)
    {
        try (InputStream in = GlUtil.class.getClassLoader().getResourceAsStream(resourcePath))
        {
            if (in == null)
                throw new IllegalStateException("Missing classpath resource: " + resourcePath);

            BufferedImage image = ImageIO.read(in);
            int width = image.getWidth();
            int height = image.getHeight();

            ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
            try
            {
                for (int row = 0; row < height; row++)
                {
                    int y = flipY ? height - 1 - row : row;

                    for (int x = 0; x < width; x++)
                    {
                        int argb = image.getRGB(x, y);
                        pixels.put((byte) ((argb >> 16) & 0xFF));
                        pixels.put((byte) ((argb >> 8) & 0xFF));
                        pixels.put((byte) (argb & 0xFF));
                        pixels.put((byte) ((argb >>> 24) & 0xFF));
                    }
                }
                pixels.flip();

                int texture = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, texture);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

                return texture;
            }
            finally
            {
                MemoryUtil.memFree(pixels);
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Could not load " + resourcePath, e);
        }
    }

    /**
     * Sprite texture with the black background made transparent by a border
     * flood fill. Interior black pixels (eyes, ear outlines) are kept, unlike
     * a flat colour key, which erases the silhouette.
     */
    static int playerSpriteTexture(String resourcePath)
    {
        try (InputStream in = GlUtil.class.getClassLoader().getResourceAsStream(resourcePath))
        {
            if (in == null)
                throw new IllegalStateException("Missing classpath resource: " + resourcePath);

            BufferedImage image = ImageIO.read(in);
            int width = image.getWidth();
            int height = image.getHeight();
            int[] argb = new int[width * height];
            image.getRGB(0, 0, width, height, argb, 0, width);

            boolean[] background = new boolean[width * height];
            ArrayDeque<Integer> queue = new ArrayDeque<>();

            for (int x = 0; x < width; x++)
            {
                seedBackground(argb, background, queue, width, x, 0);
                seedBackground(argb, background, queue, width, x, height - 1);
            }

            for (int y = 0; y < height; y++)
            {
                seedBackground(argb, background, queue, width, 0, y);
                seedBackground(argb, background, queue, width, width - 1, y);
            }

            while (!queue.isEmpty())
            {
                int index = queue.poll();
                if (background[index])
                    continue;

                background[index] = true;

                int x = index % width;
                int y = index / width;

                if (x > 0) seedBackground(argb, background, queue, width, x - 1, y);
                if (x + 1 < width) seedBackground(argb, background, queue, width, x + 1, y);
                if (y > 0) seedBackground(argb, background, queue, width, x, y - 1);
                if (y + 1 < height) seedBackground(argb, background, queue, width, x, y + 1);
            }

            ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
            try
            {
                for (int i = 0; i < argb.length; i++)
                {
                    int colour = argb[i];
                    pixels.put((byte) ((colour >> 16) & 0xFF));
                    pixels.put((byte) ((colour >> 8) & 0xFF));
                    pixels.put((byte) (colour & 0xFF));
                    pixels.put((byte) (background[i] ? 0 : 0xFF));
                }
                pixels.flip();

                int texture = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, texture);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                        GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

                return texture;
            }
            finally
            {
                MemoryUtil.memFree(pixels);
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Could not load " + resourcePath, e);
        }
    }

    private static void seedBackground(int[] argb, boolean[] background,
                                       ArrayDeque<Integer> queue, int width, int x, int y)
    {
        int index = x + y * width;
        if (background[index])
            return;

        int colour = argb[index];
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;

        if (r < 8 && g < 8 && b < 8)
            queue.add(index);
    }

    /** Writes a bottom-up RGBA buffer (as returned by glReadPixels) to a PNG. */
    static void writePng(int width, int height, ByteBuffer rgba, String path) throws IOException
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++)
        {
            int sourceRow = (height - 1 - y) * width * 4;

            for (int x = 0; x < width; x++)
            {
                int i = sourceRow + x * 4;
                int r = rgba.get(i) & 0xFF;
                int g = rgba.get(i + 1) & 0xFF;
                int b = rgba.get(i + 2) & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        ImageIO.write(image, "png", new File(path));
    }

    static void bindTexture(int unit, int texture, int target)
    {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(target, texture);
    }
}
