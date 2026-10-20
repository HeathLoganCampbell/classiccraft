package com.craftclassic.client.gpu;

import com.craftclassic.client.assets.Bitmap;
import com.craftclassic.client.assets.Textures;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Bitmap-font chat band drawn over the bottom of the frame.
 *
 * <p>The 5x6 font is rasterised on the CPU into an RGBA texture only when the
 * text changes; the blit shader composites that texture over the upscaled
 * frame. This keeps the text crisp at the internal resolution and avoids a
 * glyph-quad renderer.</p>
 */
public final class ChatOverlay implements AutoCloseable
{
    private static final String FONT_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!?()*.^$#=+-/\\\";:[]{}|&@";

    private static final int GLYPH_WIDTH = 5;
    private static final int GLYPH_HEIGHT = 6;
    private static final int ADVANCE = 6;
    private static final int LINE_HEIGHT = 7;
    private static final int MAX_MESSAGES = 6;
    private static final long VISIBLE_MILLIS = 10_000L;
    private static final int MAX_INPUT = 100;

    private static final int BACKGROUND = 0x66000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int INPUT_TEXT = 0xFFFFFF00;

    private final int width;
    private final int height;
    private final int[] pixels;
    private final ByteBuffer upload;
    private final int texture;

    private final List<String> messages = new ArrayList<>();
    private final StringBuilder input = new StringBuilder();

    private boolean open;
    private long showUntil;
    private boolean dirty = true;
    private boolean wasVisible;

    public ChatOverlay(int width, int bandHeight)
    {
        this.width = width;
        this.height = bandHeight;
        this.pixels = new int[width * bandHeight];
        this.upload = MemoryUtil.memAlloc(width * bandHeight * 4);

        this.texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, bandHeight, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    public void addMessage(String sender, String message)
    {
        String line = (sender == null || sender.isEmpty()) ? message : sender + ": " + message;

        messages.add(line);
        while (messages.size() > MAX_MESSAGES)
            messages.remove(0);

        showUntil = System.currentTimeMillis() + VISIBLE_MILLIS;
        dirty = true;
    }

    public boolean isOpen()
    {
        return open;
    }

    public void setOpen(boolean open)
    {
        this.open = open;
        this.showUntil = open ? Long.MAX_VALUE : System.currentTimeMillis() + VISIBLE_MILLIS;
        this.dirty = true;
    }

    public void append(char character)
    {
        if (input.length() >= MAX_INPUT)
            return;

        input.append(character);
        dirty = true;
    }

    public void append(String text)
    {
        for (int i = 0; i < text.length(); i++)
            append(text.charAt(i));
    }

    public void backspace()
    {
        if (input.length() == 0)
            return;

        input.setLength(input.length() - 1);
        dirty = true;
    }

    public void clearInput()
    {
        input.setLength(0);
        dirty = true;
    }

    public String getInput()
    {
        return input.toString();
    }

    public boolean isVisible()
    {
        return open || System.currentTimeMillis() < showUntil;
    }

    /**
     * Rasterises and uploads if the text or visibility changed. Call once per
     * frame before blitting.
     */
    public void update()
    {
        if (!isVisible())
        {
            wasVisible = false;
            return;
        }

        if (dirty || !wasVisible)
        {
            rasterise();
            upload();
            dirty = false;
            wasVisible = true;
        }
    }

    private void rasterise()
    {
        Arrays.fill(pixels, BACKGROUND);

        int inputY = height - LINE_HEIGHT - 3;
        String prompt = "MSG: " + input;
        drawText(4, inputY, prompt, INPUT_TEXT);

        // Cursor: the font has no underscore, so draw a small block.
        int cursorX = 4 + prompt.length() * ADVANCE;
        for (int y = inputY; y < inputY + GLYPH_HEIGHT; y++)
            putPixel(cursorX, y, INPUT_TEXT);

        int lineY = inputY - LINE_HEIGHT;
        for (int i = messages.size() - 1; i >= 0 && lineY >= 2; i--)
        {
            drawText(4, lineY, messages.get(i), TEXT);
            lineY -= LINE_HEIGHT;
        }
    }

    private void drawText(int startX, int startY, String text, int colour)
    {
        Bitmap font = Textures.fontTilesheet;
        String upper = text.toUpperCase();

        int x = startX;
        for (int i = 0; i < upper.length(); i++)
        {
            if (x + GLYPH_WIDTH > width - 2)
                break;

            int index = FONT_CHARS.indexOf(upper.charAt(i));
            if (index < 0)
            {
                if (upper.charAt(i) == ' ')
                {
                    x += ADVANCE;
                    continue;
                }
                index = FONT_CHARS.indexOf('?');
            }

            int glyphX = (index % 26) * GLYPH_WIDTH;
            int glyphY = (index / 26) * GLYPH_HEIGHT;

            for (int py = 0; py < GLYPH_HEIGHT; py++)
            {
                for (int px = 0; px < GLYPH_WIDTH; px++)
                {
                    if (font.getPixel(glyphX + px, glyphY + py) > 0)
                        putPixel(x + px, startY + py, colour);
                }
            }

            x += ADVANCE;
        }
    }

    private void putPixel(int x, int y, int argb)
    {
        if (x < 0 || x >= width || y < 0 || y >= height)
            return;

        pixels[x + y * width] = argb;
    }

    private void upload()
    {
        upload.clear();

        for (int argb : pixels)
        {
            upload.put((byte) ((argb >> 16) & 0xFF));
            upload.put((byte) ((argb >> 8) & 0xFF));
            upload.put((byte) (argb & 0xFF));
            upload.put((byte) ((argb >>> 24) & 0xFF));
        }

        upload.flip();

        glBindTexture(GL_TEXTURE_2D, texture);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL_RGBA, GL_UNSIGNED_BYTE, upload);
    }

    public int getTexture()
    {
        return texture;
    }

    public int getHeight()
    {
        return height;
    }

    @Override
    public void close()
    {
        glDeleteTextures(texture);
        MemoryUtil.memFree(upload);
    }
}
