package com.craftclassic.client.gpu;

import com.craftclassic.client.Main;
import com.craftclassic.client.input.Input;
import com.craftclassic.common.GameSettings;
import com.craftclassic.common.entities.Player;
import com.craftclassic.common.utils.Location;
import com.craftclassic.common.utils.Vector;
import com.craftclassic.common.world.Raycast;
import com.craftclassic.common.world.World;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GPU-backed client: GLFW window, LWJGL context, fragment-shader raycaster.
 *
 * <p>Runs on the main thread and must be started with {@code -XstartOnFirstThread}
 * on macOS. Use {@code run-gpu.sh} instead of invoking the JVM by hand.</p>
 *
 * <p>The simulation still runs at the ~200 ticks/second the existing constants
 * were tuned for, decoupled from the display refresh rate.</p>
 */
public final class GpuMain
{
    /**
     * The rate the movement constants in Player.tick were tuned against: the old
     * client ticked once per frame, at roughly 60 fps.
     */
    private static final int REFERENCE_TICKS_PER_SECOND = 60;
    private static final int MAX_CATCH_UP_TICKS = 4;

    /** 2x MouseGrabberListener's 0.03 factor (Entity.turn adds 0.00015 rad per unit). */
    private static final float MOUSE_SENSITIVITY = 0.06f;

    private long window;
    private GpuWorldRenderer renderer;

    private World world;
    private Player player;

    private double mouseDeltaX;
    private double mouseDeltaY;
    private boolean mouseInitialised;
    private double lastMouseX;
    private double lastMouseY;

    private boolean debug = true;

    private int ticksPerSecond = REFERENCE_TICKS_PER_SECOND;
    private double tickSeconds = 1.0 / REFERENCE_TICKS_PER_SECOND;
    private float speedMultiplier = 2.0f;
    private int maxFramesPerSecond = 0;   // 0 = vsync
    private boolean logFps = false;

    public static void main(String[] args)
    {
        new GpuMain().run(args);
    }

    private void run(String[] args)
    {
        boolean selfTest = false;
        int selfTestFrames = 120;
        boolean naturalTerrain = true;
        long seed = 1L;

        for (int i = 0; i < args.length; i++)
        {
            switch (args[i])
            {
                case "--selftest":
                    selfTest = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--"))
                        selfTestFrames = Integer.parseInt(args[++i]);
                    break;
                case "--flat":
                    naturalTerrain = false;
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "--tps":
                    ticksPerSecond = Integer.parseInt(args[++i]);
                    break;
                case "--speed":
                    speedMultiplier = Float.parseFloat(args[++i]);
                    break;
                case "--max-fps":
                    maxFramesPerSecond = Integer.parseInt(args[++i]);
                    break;
                case "--log-fps":
                    logFps = true;
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    break;
            }
        }

        ticksPerSecond = Math.max(10, Math.min(1000, ticksPerSecond));
        speedMultiplier = Math.max(0.1f, Math.min(10.0f, speedMultiplier));
        tickSeconds = 1.0 / ticksPerSecond;

        // Keep motion in blocks per second constant as the tick rate changes.
        GameSettings.tickScale = REFERENCE_TICKS_PER_SECOND / (float) ticksPerSecond;
        GameSettings.speedMultiplier = speedMultiplier;

        try
        {
            initWindow(selfTest);
            initWorld(seed, naturalTerrain);
            initInput();

            renderer = new GpuWorldRenderer(Main.WIDTH, Main.HEIGHT, Main.FOCAL);

            System.out.println("Renderer: " + glGetString(GL_RENDERER));
            System.out.println("OpenGL:   " + glGetString(GL_VERSION));
            System.out.println("Target:   " + Main.WIDTH + "x" + Main.HEIGHT
                    + " -> " + (Main.WIDTH * Main.SCALE) + "x" + (Main.HEIGHT * Main.SCALE));
            System.out.println("World:    " + (naturalTerrain ? "natural" : "flat") + ", seed " + seed);
            System.out.println("Sim:      " + ticksPerSecond + " ticks/s, speed x" + speedMultiplier);
            System.out.println("Frames:   " + (maxFramesPerSecond > 0
                    ? maxFramesPerSecond + " fps cap (vsync off)" : "vsync"));

            if (selfTest)
                runSelfTest(selfTestFrames);
            else
                runInteractive();
        }
        finally
        {
            if (renderer != null)
                renderer.close();

            if (window != NULL)
            {
                glfwDestroyWindow(window);
                glfwTerminate();
            }
        }
    }

    private void initWindow(boolean hidden)
    {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit())
            throw new IllegalStateException("glfwInit() failed (on macOS, start with -XstartOnFirstThread)");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, hidden ? GLFW_FALSE : GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(Main.WIDTH * Main.SCALE, Main.HEIGHT * Main.SCALE,
                "ClassicCraft (GPU)", NULL, NULL);

        if (window == NULL)
            throw new IllegalStateException("Could not create a GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(hidden || maxFramesPerSecond > 0 ? 0 : 1);

        if (!hidden)
            glfwFocusWindow(window);

        GL.createCapabilities();
    }

    private void initWorld(long seed, boolean naturalTerrain)
    {
        world = new World(64, 64, 64, seed, naturalTerrain);

        player = new Player(null, "Player1", 0);
        player.setLocation(new Location(world, 64 + 32.5f, 64 + 1.0f, 64 + 32.5f));
        player.setVelocity(new Vector(0f, 60f, 0f));

        world.addEntity(player);
    }

    private void initInput()
    {
        glfwSetKeyCallback(window, (handle, key, scancode, action, mods) ->
        {
            int awtKey = toAwtKey(key);
            if (awtKey > 0)
                Input.setKey(awtKey, action != GLFW_RELEASE);

            if (action != GLFW_PRESS)
                return;

            switch (key)
            {
                case GLFW_KEY_ESCAPE:
                    glfwSetWindowShouldClose(handle, true);
                    break;
                case GLFW_KEY_F2:
                    debug = !debug;
                    break;
                case GLFW_KEY_RIGHT:
                    GameSettings.placeBlockTypeId++;
                    player.turn((float) Math.PI, 0);
                    break;
                case GLFW_KEY_LEFT:
                    GameSettings.placeBlockTypeId--;
                    player.turn((float) -Math.PI, 0);
                    break;
                case GLFW_KEY_UP:
                    player.turn(0, (float) Math.PI);
                    break;
                case GLFW_KEY_DOWN:
                    player.turn(0, (float) -Math.PI);
                    break;
                default:
                    break;
            }
        });

        glfwSetMouseButtonCallback(window, (handle, button, action, mods) ->
        {
            boolean pressed = action == GLFW_PRESS;

            if (button == GLFW_MOUSE_BUTTON_LEFT)
                Input.setMouse(MouseEvent.BUTTON1, pressed);
            else if (button == GLFW_MOUSE_BUTTON_RIGHT)
                Input.setMouse(MouseEvent.BUTTON3, pressed);
        });

        glfwSetCursorPosCallback(window, (handle, x, y) ->
        {
            if (!mouseInitialised)
            {
                lastMouseX = x;
                lastMouseY = y;
                mouseInitialised = true;
                return;
            }

            mouseDeltaX += x - lastMouseX;
            mouseDeltaY += y - lastMouseY;
            lastMouseX = x;
            lastMouseY = y;
        });

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        if (glfwRawMouseMotionSupported())
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
    }

    private void runInteractive()
    {
        double accumulator = 0.0;
        long last = System.nanoTime();
        long fpsTimestamp = last;
        int fpsFrames = 0;
        int[] framebufferWidth = new int[1];
        int[] framebufferHeight = new int[1];

        long frameNanos = maxFramesPerSecond > 0 ? 1_000_000_000L / maxFramesPerSecond : 0L;
        long nextFrame = System.nanoTime();

        while (!glfwWindowShouldClose(window))
        {
            glfwPollEvents();

            long now = System.nanoTime();
            accumulator += (now - last) / 1_000_000_000.0;
            last = now;

            if (accumulator > 0.25)
                accumulator = 0.25;

            int steps = 0;
            while (accumulator >= tickSeconds && steps < MAX_CATCH_UP_TICKS)
            {
                world.tick();
                accumulator -= tickSeconds;
                steps++;
            }

            // A long stall (window drag, breakpoint) must not replay seconds of
            // simulation in one frame.
            if (accumulator >= tickSeconds)
                accumulator = 0.0;

            if (mouseDeltaX != 0.0 || mouseDeltaY != 0.0)
            {
                player.turn((float) (mouseDeltaX * MOUSE_SENSITIVITY),
                        (float) (-mouseDeltaY * MOUSE_SENSITIVITY));
                mouseDeltaX = 0.0;
                mouseDeltaY = 0.0;
                normaliseYaw();
            }

            updateTargetBlock();
            renderFrame();

            glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
            if (framebufferWidth[0] > 0 && framebufferHeight[0] > 0)
                renderer.blit(framebufferWidth[0], framebufferHeight[0]);

            glfwSwapBuffers(window);

            if (frameNanos > 0)
            {
                nextFrame += frameNanos;
                long frameNow = System.nanoTime();

                if (nextFrame > frameNow)
                    LockSupport.parkNanos(nextFrame - frameNow);
                else if (frameNow - nextFrame > frameNanos * 4)
                    nextFrame = frameNow;
            }

            fpsFrames++;
            if (now > fpsTimestamp + 1_000_000_000L)
            {
                if (debug)
                {
                    Location location = player.getLocation();
                    glfwSetWindowTitle(window, String.format(
                            "ClassicCraft (GPU) - %dx%d -> %dx%d - %d fps - %d tps - %.1f %.1f %.1f - %s",
                            Main.WIDTH, Main.HEIGHT,
                            Main.WIDTH * Main.SCALE, Main.HEIGHT * Main.SCALE,
                            fpsFrames, ticksPerSecond,
                            location.getX(), location.getY(), location.getZ(),
                            com.craftclassic.common.blocks.Block
                                    .getBlockById(GameSettings.placeBlockTypeId).getName()));
                }

                if (logFps)
                    System.out.println("fps: " + fpsFrames);

                fpsTimestamp = now;
                fpsFrames = 0;
            }
        }
    }

    /**
     * The CPU renderer discovered the targeted block inside World.render(). The
     * GPU client does not run that loop, so cast one ray through the crosshair.
     */
    private void updateTargetBlock()
    {
        Location location = player.getLocation();

        Raycast.Hit hit = Raycast.cast(world,
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), Raycast.DEFAULT_REACH);

        if (hit == null)
        {
            player.setTargetBlock(null);
            player.setTargetBlockPlace(null);
            renderer.clearTarget();
            return;
        }

        player.setTargetBlock(new Location(world, hit.getX(), hit.getY(), hit.getZ()));
        player.setTargetBlockPlace(new Location(world, hit.getPlaceX(), hit.getPlaceY(), hit.getPlaceZ()));
        renderer.setTarget(hit.getX(), hit.getY(), hit.getZ());
    }

    private void renderFrame()
    {
        Location location = player.getLocation();

        float fov = player.isSprinting() && !player.getVelocity().isZero() ? 0.95f : 1.0f;

        renderer.render(world,
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), fov);
    }

    /**
     * Headless frame measurement: renders to the offscreen target, blocks on the
     * GPU, writes gpu-frame.png and exits. Used to compare against the CPU path.
     */
    private void runSelfTest(int frames)
    {
        // Let gravity drop the player onto the terrain first (10 simulated seconds).
        int warmupTicks = (int) Math.ceil(10.0 / tickSeconds);
        long simStart = System.nanoTime();
        for (int i = 0; i < warmupTicks; i++)
            world.tick();
        double simMilliseconds = (System.nanoTime() - simStart) / 1_000_000.0 / warmupTicks;

        Location location = player.getLocation();
        location.setYaw(0.7f);
        location.setPitch(-0.0012f);
        updateTargetBlock();

        renderFrame();
        glFinish();

        long start = System.nanoTime();

        for (int i = 0; i < frames; i++)
        {
            renderFrame();
            glFinish();
        }

        long elapsed = System.nanoTime() - start;
        double milliseconds = elapsed / 1_000_000.0 / frames;

        ByteBuffer pixels = renderer.readPixels();
        try
        {
            GlUtil.writePng(Main.WIDTH, Main.HEIGHT, pixels, "gpu-frame.png");
        }
        catch (Exception e)
        {
            System.err.println("Could not write gpu-frame.png: " + e.getMessage());
        }
        finally
        {
            MemoryUtil.memFree(pixels);
        }

        System.out.printf("Self-test:  %d frames, %.3f ms/frame, %.0f fps (CPU-serialised)%n",
                frames, milliseconds, 1000.0 / milliseconds);
        System.out.printf("Tick:       %.3f ms/tick at %d ticks/s%n", simMilliseconds, ticksPerSecond);
        System.out.printf("Pixels:     %d  (%.1f M rays/s)%n",
                Main.WIDTH * Main.HEIGHT,
                (Main.WIDTH * Main.HEIGHT) / (milliseconds / 1000.0) / 1_000_000.0);
        System.out.println("Wrote       gpu-frame.png");

        // Touch the world and render again: this must trigger exactly one
        // re-upload. Guards the blockVersion contract between World and GPU.
        world.setBlock(32, 20, 32, com.craftclassic.common.blocks.Block.STONE);
        renderFrame();
        glFinish();
        System.out.println("Uploads:    " + renderer.getUploadCount() + " (expected 2)");
    }

    private void normaliseYaw()
    {
        final float twoPi = (float) (Math.PI * 2.0);
        Location location = player.getLocation();

        float yaw = location.getYaw() % twoPi;
        if (yaw > Math.PI)
            yaw -= twoPi;
        else if (yaw < -Math.PI)
            yaw += twoPi;

        location.setYaw(yaw);
    }

    private static int toAwtKey(int glfwKey)
    {
        // GLFW uses ASCII for letters and digits, which matches AWT's VK_ codes.
        if (glfwKey >= GLFW_KEY_A && glfwKey <= GLFW_KEY_Z)
            return KeyEvent.VK_A + (glfwKey - GLFW_KEY_A);

        if (glfwKey >= GLFW_KEY_0 && glfwKey <= GLFW_KEY_9)
            return KeyEvent.VK_0 + (glfwKey - GLFW_KEY_0);

        switch (glfwKey)
        {
            case GLFW_KEY_SPACE:          return KeyEvent.VK_SPACE;
            case GLFW_KEY_LEFT_CONTROL:
            case GLFW_KEY_RIGHT_CONTROL:  return KeyEvent.VK_CONTROL;
            case GLFW_KEY_LEFT_SHIFT:
            case GLFW_KEY_RIGHT_SHIFT:    return KeyEvent.VK_SHIFT;
            case GLFW_KEY_LEFT:           return KeyEvent.VK_LEFT;
            case GLFW_KEY_RIGHT:          return KeyEvent.VK_RIGHT;
            case GLFW_KEY_UP:             return KeyEvent.VK_UP;
            case GLFW_KEY_DOWN:           return KeyEvent.VK_DOWN;
            case GLFW_KEY_ESCAPE:         return KeyEvent.VK_ESCAPE;
            case GLFW_KEY_F2:             return KeyEvent.VK_F2;
            default:                      return -1;
        }
    }
}
