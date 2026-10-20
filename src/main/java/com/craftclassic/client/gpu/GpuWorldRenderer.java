package com.craftclassic.client.gpu;

import com.craftclassic.client.Main;
import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.entities.Entity;
import com.craftclassic.common.world.World;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL15.*;
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
    private final float focal;

    private final int framebuffer;
    private final int colorTexture;
    private final int depthTexture;
    private final int depthRenderbuffer;

    private final int spriteProgram;
    private final int spriteVao;
    private final int spriteVbo;
    private final int spriteTexture;
    private final int uSpriteCenter;
    private final int uSpriteSize;
    private final int uSpriteResolution;
    private final int uSpriteTexture;
    private final int uSpriteZ;
    private final int uSpriteFog;

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
    private final int uFocal;
    private final int uFogDist;
    private final int uTime;
    private final int uSky;
    private final int uTarget;

    private final int uFrame;
    private final int uLowRes;
    private final int uCrosshair;
    private final int uChat;
    private final int uChatVisible;
    private final int uChatFraction;
    private final int uChatWidth;

    private final ByteBuffer worldUpload = MemoryUtil.memAlloc(WORLD_SIZE * WORLD_SIZE * WORLD_SIZE);
    private long uploadedVersion = Long.MIN_VALUE;
    private int uploadCount;
    private int targetX = -1, targetY = -1, targetZ = -1;

    public GpuWorldRenderer(int width, int height, float focal)
    {
        this.width = width;
        this.height = height;
        this.focal = focal;

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
        this.uFocal = uniform(raycastProgram, "uFocal");
        this.uFogDist = uniform(raycastProgram, "uFogDist");
        this.uTime = uniform(raycastProgram, "uTime");
        this.uSky = uniform(raycastProgram, "uSky");
        this.uTarget = uniform(raycastProgram, "uTarget");

        this.uFrame = uniform(blitProgram, "uFrame");
        this.uLowRes = uniform(blitProgram, "uLowRes");
        this.uCrosshair = uniform(blitProgram, "uCrosshair");
        this.uChat = uniform(blitProgram, "uChat");
        this.uChatVisible = uniform(blitProgram, "uChatVisible");
        this.uChatFraction = uniform(blitProgram, "uChatFraction");
        this.uChatWidth = uniform(blitProgram, "uChatWidth");

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

        // Real depth buffer for the sprite pass. The raycast writes gl_FragDepth
        // from its hit distance, so sprites are occluded without sampling the
        // depth attachment (which would be a feedback loop).
        this.depthRenderbuffer = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depthRenderbuffer);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRenderbuffer);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Framebuffer incomplete: 0x" + Integer.toHexString(status));

        // --- sprite pass ---------------------------------------------------
        this.spriteProgram = GlUtil.program("shaders/sprite.vert", "shaders/sprite.frag");
        this.uSpriteCenter = uniform(spriteProgram, "uCenter");
        this.uSpriteSize = uniform(spriteProgram, "uSize");
        this.uSpriteResolution = uniform(spriteProgram, "uResolution");
        this.uSpriteTexture = uniform(spriteProgram, "uSprite");
        this.uSpriteZ = uniform(spriteProgram, "uZ");
        this.uSpriteFog = uniform(spriteProgram, "uFogDist");
        this.spriteTexture = GlUtil.texture2D("player.png", false);

        float[] quad =
        {
                -0.5f, -0.5f,
                 0.5f, -0.5f,
                 0.5f,  0.5f,
                -0.5f,  0.5f
        };

        this.spriteVao = glGenVertexArrays();
        this.spriteVbo = glGenBuffers();
        glBindVertexArray(spriteVao);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVbo);
        glBufferData(GL_ARRAY_BUFFER, quad, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

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
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_ALWAYS);
        glDepthMask(true);
        glClearDepth(1.0);
        glClear(GL_DEPTH_BUFFER_BIT);

        glUseProgram(raycastProgram);

        glUniform3f(uCamPos, camX, camY, camZ);
        glUniform1f(uYaw, yaw * 100.0f);
        glUniform1f(uPitch, pitch * 100.0f);
        glUniform2f(uResolution, width, height);
        glUniform1f(uFov, fov);
        glUniform1f(uFocal, focal);
        glUniform1f(uFogDist, 50.0f);
        glUniform1f(uTime, (System.nanoTime() / 1_000_000_000L) % 1000L);
        glUniform3f(uSky, 0xA7 / 255.0f, 0xC9 / 255.0f, 0xEB / 255.0f);
        glUniform3i(uTarget, targetX, targetY, targetZ);

        GlUtil.bindTexture(0, worldTexture, GL_TEXTURE_3D);
        GlUtil.bindTexture(1, atlasTexture, GL_TEXTURE_2D);
        glUniform1i(uWorld, 0);
        glUniform1i(uAtlas, 1);

        glBindVertexArray(emptyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);

        drawSprites(world, camX, camY, camZ, yaw, pitch);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }

    /**
     * Billboard sprites for remote players, using the same projection as the
     * CPU renderer's renderSprite(). The raycast's gl_FragDepth occludes them.
     */
    private void drawSprites(World world, float camX, float camY, float camZ, float yaw, float pitch)
    {
        List<Entity> entities = world.getEntities();
        if (entities.isEmpty())
            return;

        float scaledYaw = yaw * 100.0f;
        float scaledPitch = -pitch * 100.0f;

        double cosYaw = Math.cos(scaledYaw);
        double sinYaw = Math.sin(scaledYaw);
        double cosPitch = Math.cos(scaledPitch);
        double sinPitch = Math.sin(scaledPitch);

        float baseWidth = 32.0f * 4.0f * Main.RESOLUTION_SCALE;
        float baseHeight = 64.0f * 4.0f * Main.RESOLUTION_SCALE;

        glUseProgram(spriteProgram);
        glUniform2f(uSpriteResolution, width, height);
        glUniform1f(uSpriteFog, 50.0f);
        GlUtil.bindTexture(0, spriteTexture, GL_TEXTURE_2D);
        glUniform1i(uSpriteTexture, 0);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindVertexArray(spriteVao);

        for (Entity entity : entities)
        {
            if (entity.getEntityId() == 0 || entity.getLocation() == null)
                continue;

            double diffX = entity.getLocation().getX() - camX;
            double diffY = entity.getLocation().getY() - camY;
            double diffZ = entity.getLocation().getZ() - camZ;

            if (diffY == 0.0)
                diffY = 0.005;

            double xx = cosYaw * diffX - sinYaw * diffZ;
            double zz = sinYaw * diffX + cosYaw * diffZ;
            double yy = cosPitch * diffY - sinPitch * zz;
            zz = sinPitch * diffY + cosPitch * zz;

            if (zz < 0.0)
                continue;

            float screenX = (float) (xx * focal / zz) + width * 0.5f;
            float screenY = (float) (yy * focal / zz) + height * 0.5f;

            glUniform2f(uSpriteCenter, screenX, screenY);
            glUniform2f(uSpriteSize, baseWidth / (float) zz, baseHeight / (float) zz);
            glUniform1f(uSpriteZ, (float) zz);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        }

        glBindVertexArray(0);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
    }

    /** Nearest-neighbour upscale from the low-res target to the window. */
    public void blit(int windowWidth, int windowHeight)
    {
        blit(windowWidth, windowHeight, 0, false, 0.0f, 1.0f);
    }

    /**
     * @param chatTexture  RGBA chat band, or 0 when there is no chat
     * @param chatFraction chat band height as a fraction of the low-res height
     */
    public void blit(int windowWidth, int windowHeight, int chatTexture,
                     boolean chatVisible, float chatFraction, float chatWidthFraction)
    {
        // Framebuffer is chosen by the caller: the window in the game loop,
        // an offscreen target in the self-test.
        glViewport(0, 0, windowWidth, windowHeight);

        glUseProgram(blitProgram);

        GlUtil.bindTexture(0, colorTexture, GL_TEXTURE_2D);
        glUniform1i(uFrame, 0);
        glUniform2f(uLowRes, width, height);
        glUniform2i(uCrosshair, width / 2, height / 2);

        boolean showChat = chatVisible && chatTexture != 0;
        glUniform1i(uChatVisible, showChat ? 1 : 0);
        glUniform1f(uChatFraction, chatFraction);
        glUniform1f(uChatWidth, chatWidthFraction);

        if (showChat)
        {
            GlUtil.bindTexture(1, chatTexture, GL_TEXTURE_2D);
            glUniform1i(uChat, 1);
        }

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
            // +1 keeps 0 free for air, bit 7 carries "selectable".
            // Air is stored as a real Block.AIR instance, not null.
            if (block == null || block.getTextureId() < 0)
                worldUpload.put(i, (byte) 0);
            else
                worldUpload.put(i, (byte) ((block.getTextureId() + 1)
                        | (block.isSelectable() ? 0x80 : 0)));
        }

        glBindTexture(GL_TEXTURE_3D, worldTexture);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, 0, WORLD_SIZE, WORLD_SIZE, WORLD_SIZE,
                GL_RED_INTEGER, GL_UNSIGNED_BYTE, worldUpload);

        uploadedVersion = world.blockVersion;
        uploadCount++;
    }

    /** Block under the crosshair, or x < 0 for none; used for the highlight. */
    public void setTarget(int x, int y, int z)
    {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    public void clearTarget()
    {
        setTarget(-1, -1, -1);
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
        glDeleteProgram(spriteProgram);
        glDeleteTextures(new int[] { colorTexture, depthTexture, worldTexture, atlasTexture, spriteTexture });
        glDeleteFramebuffers(framebuffer);
        glDeleteRenderbuffers(depthRenderbuffer);
        glDeleteVertexArrays(emptyVao);
        glDeleteVertexArrays(spriteVao);
        glDeleteBuffers(spriteVbo);
        MemoryUtil.memFree(worldUpload);
    }
}
