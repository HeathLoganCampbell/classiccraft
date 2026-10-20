package com.craftclassic.client.gpu;

import com.craftclassic.common.entities.Entity;
import com.craftclassic.common.world.World;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Remote-player billboards.
 *
 * <p>Each player is a world-space rectangle built from the camera's right/up
 * basis. All four corners are projected, so the rasteriser does the
 * perspective correction. Depth comes from the raycast's
 * {@code gl_FragDepth}, so players are hidden behind terrain.</p>
 */
public final class SpriteRenderer implements AutoCloseable
{
    private static final float FOG_DISTANCE = 50.0f;

    /** Collision box bottom relative to the player's camera/eye origin. */
    private static final float FEET_OFFSET = 1.45f;

    private final int program;
    private final int vao;
    private final int vbo;

    private final int texture;
    private final int textureWidth;
    private final int textureHeight;

    private final int uColor;
    private final int uSprite;
    private final int uTextured;
    private final int uCenter;
    private final int uSize;
    private final int uRight;
    private final int uUp;
    private final int uCamPos;
    private final int uYaw;
    private final int uPitch;
    private final int uFocal;
    private final int uResolution;
    private final int uFogDist;

    public SpriteRenderer()
    {
        this.program = GlUtil.program("shaders/sprite.vert", "shaders/sprite.frag");

        this.uCenter = uniform("uCenter");
        this.uSize = uniform("uSize");
        this.uRight = uniform("uRight");
        this.uUp = uniform("uUp");
        this.uCamPos = uniform("uCamPos");
        this.uYaw = uniform("uYaw");
        this.uPitch = uniform("uPitch");
        this.uFocal = uniform("uFocal");
        this.uResolution = uniform("uResolution");
        this.uFogDist = uniform("uFogDist");
        this.uColor = uniform("uColor");
        this.uSprite = uniform("uSprite");
        this.uTextured = uniform("uTextured");

        GlUtil.SpriteTexture sprite = GlUtil.playerSpriteTexture("player.png");
        this.texture = sprite.id;
        this.textureWidth = sprite.width;
        this.textureHeight = sprite.height;

        // aPos: x = left..right, y = top..bottom
        float[] quad =
        {
                -0.5f, -0.5f,
                 0.5f, -0.5f,
                 0.5f,  0.5f,
                -0.5f,  0.5f
        };

        this.vao = glGenVertexArrays();
        this.vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, quad, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private int uniform(String name)
    {
        int location = glGetUniformLocation(program, name);
        if (location < 0)
            throw new IllegalStateException("Sprite uniform not found: " + name);

        return location;
    }

    public void render(World world, float camX, float camY, float camZ,
                       float yaw, float pitch, float focal, int width, int height)
    {
        List<Entity> entities = world.getEntities();
        if (entities.isEmpty())
            return;

        // Same scale conventions as the raycast: yaw is scaled by 100, and
        // pitch is negated for the screen-space vertical axis.
        float scaledYaw = yaw * 100.0f;
        float scaledPitch = -pitch * 100.0f;

        double cy = Math.cos(scaledYaw);
        double sy = Math.sin(scaledYaw);
        // Player rectangle: 1.2 wide, 1.8 tall, in world units.
        float worldWidth = 1.2f;
        float worldHeight = 1.8f;

        glUseProgram(program);

        glUniform2f(uResolution, width, height);
        glUniform1f(uFocal, focal);
        glUniform1f(uFogDist, FOG_DISTANCE);
        glUniform3f(uCamPos, camX, camY, camZ);
        glUniform1f(uYaw, scaledYaw);
        glUniform1f(uPitch, scaledPitch);

        // Doom-style cylindrical billboard: only the horizontal facing follows
        // the camera. The quad stays vertical in the world, so looking up or
        // down foreshortens it instead of tilting it.
        glUniform3f(uRight, (float) cy, 0.0f, (float) -sy);
        glUniform3f(uUp, 0.0f, -1.0f, 0.0f);

        glUniform1i(uSprite, 0);
        GlUtil.bindTexture(0, texture, GL_TEXTURE_2D);

        // Players are drawn over the world: a billboard clipped by a block
        // corner reads as a triangular chunk taken out of the model.
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindVertexArray(vao);

        for (Entity entity : entities)
        {
            if (entity.getEntityId() == 0 || entity.getLocation() == null)
                continue;

            // Anchor the bottom of the rectangle at the player's feet.
            glUniform3f(uCenter,
                    entity.getLocation().getX(),
                    entity.getLocation().getY() + FEET_OFFSET - worldHeight * 0.5f,
                    entity.getLocation().getZ());

            // Wide panel behind the model.
            glUniform2f(uSize, worldWidth, worldHeight);
            glUniform1i(uTextured, 0);
            glUniform4f(uColor, 0.20f, 0.22f, 0.30f, 1.0f);
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

            // Bunny texture centred on the panel, preserving its aspect.
            float modelHeight = worldHeight;
            float modelWidth = modelHeight * this.textureWidth / (float) this.textureHeight;
            if (modelWidth > worldWidth)
            {
                modelWidth = worldWidth;
                modelHeight = modelWidth * this.textureHeight / (float) this.textureWidth;
            }

            glUniform2f(uSize, modelWidth, modelHeight);
            glUniform1i(uTextured, 1);
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
        }

        glBindVertexArray(0);
        glDisable(GL_BLEND);
        glDepthMask(true);
    }

    @Override
    public void close()
    {
        glDeleteProgram(program);
        glDeleteTextures(texture);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
