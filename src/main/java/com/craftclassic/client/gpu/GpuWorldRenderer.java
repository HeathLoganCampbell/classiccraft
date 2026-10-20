package com.craftclassic.client.gpu;

import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.world.World;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Fragment-shader port of {@link World#render()}.
 *
 * <p>The 64x64x64 block array is uploaded once as a 256 KB R8UI 3D texture and
 * re-uploaded only when {@link World#blockVersion} changes. The raycast, the
 * texture lookup, the face shading and the fog all happen in
 * {@code shaders/raycast.frag}; the CPU only pushes a handful of uniforms and
 * issues one fullscreen triangle.</p>
 */
public final class GpuWorldRenderer implements AutoCloseable
{
    public static final int WORLD_SIZE = 64;
    public static final int ATLAS_WIDTH = 16;
    public static final int ATLAS_ROWS_PER_BLOCK = 48;

    private final int width;
    private final int height;

    private final int framebuffer;
    private final int colorTexture;
    private final int depthTexture;

    private final int worldTexture;
    private final int atlasTexture;

    private final int raycastProgram;
    private final int blitProgram;
    private final int emptyVao;

    private final int uWorld;
    private final int uAtlas;
    private final int uCamPos;
    private final int uYaw;
    private final int uPitch;
    private final int uResolution;
    private final int uFov;
    private final int uFogDist;
    private final int uSky;

    private final int uFrame;
    private final int uLowRes;
    private final int uCrosshair;

    private final ByteBuffer worldUpload = MemoryUtil.memAlloc(WORLD_SIZE * WORLD_SIZE * WORLD_SIZE);
    private long uploadedVersion = Long.MIN_VALUE;
    private int uploadCount;

    public GpuWorldRenderer(int width, int height)
    {
        this.width = width;
        this.height = height;

        this.raycastProgram = GlUtil.program("shaders/fullscreen.vert", "shaders/raycast.frag");
        this.blitProgram = GlUtil.program("shaders/fullscreen.vert", "shaders/blit.frag");

        this.emptyVao = glGenVertexArrays();

        this.uWorld = uniform(raycastProgram, "uWorld");
        this.uAtlas = uniform(raycastProgram, "uAtlas");
        this.uCamPos = uniform(raycastProgram, "uCamPos");
        this.uYaw = uniform(raycastProgram, "uYaw");
        this.uPitch = uniform(raycastProgram, "uPitch");
        this.uResolution = uniform(raycastProgram, "uResolution");
        this.uFov = uniform(raycastProgram, "uFov");
        this.uFogDist = uniform(raycastProgram, "uFogDist");
        this.uSky = uniform(raycastProgram, "uSky");

        this.uFrame = uniform(blitProgram, "uFrame");
        this.uLowRes = uniform(blitProgram, "uLowRes");
        this.uCrosshair = uniform(blitProgram, "uCrosshair");

        // --- world: one byte per block -------------------------------------
        this.worldTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, worldTexture);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage3D(GL_TEXTURE_3D, 0, GL_R8UI, WORLD_SIZE, WORLD_SIZE, WORLD_SIZE,
                0, GL_RED_INTEGER, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        // --- block atlas ----------------------------------------------------
        // texelFetch indexes rows top-down, exactly like Bitmap.textureData, so
        // the image must NOT be flipped on upload.
        this.atlasTexture = GlUtil.texture2D("saved-texturess.png", false);

        // --- offscreen low-res target ---------------------------------------
        this.colorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Distance along the ray, in blocks. The counterpart of World.zBuffer.
        this.depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, width, height, 0, GL_RED, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        this.framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, depthTexture, 0);
        glDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 });

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Framebuffer incomplete: 0x" + Integer.toHexString(status));

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindTexture(GL_TEXTURE_3D, 0);
    }

    private static int uniform(int program, String name)
    {
        int location = glGetUniformLocation(program, name);
        if (location < 0)
            throw new IllegalStateException("Uniform not found: " + name);

        return location;
    }

    /**
     * Renders one frame into the low-res target.
     *
     * @param yaw   player yaw in radians (scaled by 100 here, as the CPU path does)
     * @param pitch player pitch in radians (scaled by 100 here)
     */
    public void render(World world, float camX, float camY, float camZ, float yaw, float pitch, float fov)
    {
        if (world.blockVersion != uploadedVersion)
            uploadWorld(world);

        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);

        glUseProgram(raycastProgram);

        glUniform3f(uCamPos, camX, camY, camZ);
        glUniform1f(uYaw, yaw * 100.0f);
        glUniform1f(uPitch, pitch * 100.0f);
        glUniform2f(uResolution, width, height);
        glUniform1f(uFov, fov);
        glUniform1f(uFogDist, 50.0f);
        glUniform3f(uSky, 0xA7 / 255.0f, 0xC9 / 255.0f, 0xEB / 255.0f);

        GlUtil.bindTexture(0, worldTexture, GL_TEXTURE_3D);
        GlUtil.bindTexture(1, atlasTexture, GL_TEXTURE_2D);
        glUniform1i(uWorld, 0);
        glUniform1i(uAtlas, 1);

        glBindVertexArray(emptyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }

    /** Nearest-neighbour upscale from the low-res target to the window. */
    public void blit(int windowWidth, int windowHeight)
    {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, windowWidth, windowHeight);

        glUseProgram(blitProgram);

        GlUtil.bindTexture(0, colorTexture, GL_TEXTURE_2D);
        glUniform1i(uFrame, 0);
        glUniform2f(uLowRes, width, height);
        glUniform2i(uCrosshair, width / 2, height / 2);

        glBindVertexArray(emptyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }

    private void uploadWorld(World world)
    {
        Block[] data = world.blockData;

        for (int i = 0; i < data.length; i++)
        {
            Block block = data[i];
            // Texture id, not block id: grass is block 2 but atlas slot 0.
            // +1 keeps 0 free for air.
            worldUpload.put(i, (byte) (block == null ? 0 : block.getTextureId() + 1));
        }

        glBindTexture(GL_TEXTURE_3D, worldTexture);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, 0, WORLD_SIZE, WORLD_SIZE, WORLD_SIZE,
                GL_RED_INTEGER, GL_UNSIGNED_BYTE, worldUpload);

        uploadedVersion = world.blockVersion;
        uploadCount++;
    }

    /** Number of times the world texture has been (re)uploaded. */
    public int getUploadCount()
    {
        return uploadCount;
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    /** Reads the low-res color attachment back as bottom-up RGBA. */
    public ByteBuffer readPixels()
    {
        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);

        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        return buffer;
    }

    @Override
    public void close()
    {
        glDeleteProgram(raycastProgram);
        glDeleteProgram(blitProgram);
        glDeleteTextures(new int[] { colorTexture, depthTexture, worldTexture, atlasTexture });
        glDeleteFramebuffers(framebuffer);
        glDeleteVertexArrays(emptyVao);
        MemoryUtil.memFree(worldUpload);
    }
}
