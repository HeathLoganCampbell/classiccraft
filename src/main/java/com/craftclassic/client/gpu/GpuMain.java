package com.craftclassic.client.gpu;

import com.craftclassic.client.Main;
import com.craftclassic.client.input.Input;
import com.craftclassic.client.net.NettyClient;
import com.craftclassic.common.GameSettings;
import com.craftclassic.common.entities.Entity;
import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.ClientBridge;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;
import com.craftclassic.common.network.packet.packets.EntityPositionPacket;
import com.craftclassic.common.utils.Location;
import com.craftclassic.common.utils.Vector;
import com.craftclassic.common.world.Raycast;
import com.craftclassic.common.world.World;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
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
public final class GpuMain implements ClientBridge.View
{
    /**
     * The rate the movement constants in Player.tick were tuned against: the old
     * client ticked once per frame, at roughly 60 fps.
     */
    private static final int REFERENCE_TICKS_PER_SECOND = 60;
    private static final int MAX_CATCH_UP_TICKS = 4;
    private static final int CHAT_BAND_HEIGHT = 56;

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

    private ChatOverlay chat;
    private NettyClient nettyClient;
    private String serverHost = null;
    private int serverPort = NettyClient.DEFAULT_PORT;
    private String playerName = "Player";
    private boolean chatDemo = false;
    private boolean waterDemo = false;
    private double exitAfterSeconds = -1.0;
    private double openChatAfterSeconds = -1.0;
    private boolean skipWindowDestroy = false;
    private long suppressCharUntilNanos = 0L;

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
                case "--server":
                    String spec = args[++i];
                    int colon = spec.indexOf(':');
                    if (colon > 0)
                    {
                        serverHost = spec.substring(0, colon);
                        serverPort = Integer.parseInt(spec.substring(colon + 1));
                    }
                    else
                    {
                        serverHost = spec;
                    }
                    break;
                case "--name":
                    playerName = args[++i];
                    break;
                case "--chat-demo":
                    chatDemo = true;
                    break;
                case "--water-demo":
                    waterDemo = true;
                    break;
                case "--exit-after":
                    exitAfterSeconds = Double.parseDouble(args[++i]);
                    break;
                case "--open-chat-after":
                    openChatAfterSeconds = Double.parseDouble(args[++i]);
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
            chat = new ChatOverlay(Main.WIDTH * 2, CHAT_BAND_HEIGHT);
            chat.setUsername(playerName);
            ClientBridge.view = this;

            if (serverHost != null)
            {
                nettyClient = new NettyClient(serverHost, serverPort, playerName);

                if (nettyClient.connect())
                {
                    Player.packetSink = nettyClient::sendPacket;
                    System.out.println("Server:   connected to " + serverHost + ":" + serverPort
                            + " as " + playerName);
                }
                else
                {
                    System.out.println("Server:   no server at " + serverHost + ":" + serverPort
                            + " (single-player)");
                    nettyClient = null;
                }
            }

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
            if (chat != null)
                chat.close();

            if (renderer != null)
                renderer.close();

            if (window != NULL && !skipWindowDestroy)
            {
                glfwDestroyWindow(window);
                glfwTerminate();
            }
        }

        if (skipWindowDestroy)
            System.exit(0);
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
        {
            GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (mode != null)
            {
                glfwSetWindowPos(window,
                        (mode.width() - Main.WIDTH * Main.SCALE) / 2,
                        (mode.height() - Main.HEIGHT * Main.SCALE) / 2);
            }

            glfwShowWindow(window);
            glfwFocusWindow(window);
            glfwRequestWindowAttention(window);
        }

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
            if (chat != null && chat.isOpen())
            {
                if (action == GLFW_PRESS || action == GLFW_REPEAT)
                {
                    if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER)
                    {
                        sendChat();
                        chat.setOpen(false);
                    }
                    else if (key == GLFW_KEY_ESCAPE)
                    {
                        chat.setOpen(false);
                    }
                    else if (key == GLFW_KEY_BACKSPACE)
                    {
                        chat.backspace();
                    }
                }
                return;
            }

            int awtKey = toAwtKey(key);
            if (awtKey > 0)
                Input.setKey(awtKey, action != GLFW_RELEASE);

            if (action != GLFW_PRESS)
                return;

            if (key == GLFW_KEY_T || key == GLFW_KEY_SLASH)
            {
                chat.setOpen(true);
                releaseMovementKeys();

                // GLFW sends the character for this key after the key callback.
                // Without this the 'T' that opened the chat is typed into it.
                suppressCharUntilNanos = System.nanoTime() + 30_000_000L;
                return;
            }

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

        glfwSetCharCallback(window, (handle, codepoint) ->
        {
            if (System.nanoTime() <= suppressCharUntilNanos)
            {
                suppressCharUntilNanos = 0L;
                return;
            }

            if (chat != null && chat.isOpen() && codepoint >= 32 && codepoint < 127)
                chat.append((char) codepoint);
        });

        glfwSetMouseButtonCallback(window, (handle, button, action, mods) ->
        {
            if (chat != null && chat.isOpen())
                return;

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
        long startedAt = System.nanoTime();

        while (!glfwWindowShouldClose(window))
        {
            glfwPollEvents();

            double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;

            if (openChatAfterSeconds > 0.0 && !chat.isOpen() && elapsedSeconds > openChatAfterSeconds)
            {
                chat.setOpen(true);
                releaseMovementKeys();
                System.out.println("debug: opened chat");
            }

            if (exitAfterSeconds > 0.0 && elapsedSeconds > exitAfterSeconds)
                glfwSetWindowShouldClose(window, true);

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

            if (steps > 0 && nettyClient != null && nettyClient.isConnected())
            {
                Location location = player.getLocation();
                nettyClient.sendPacket(new EntityPositionPacket(0,
                        location.getX(), location.getY(), location.getZ()));
            }

            // A long stall (window drag, breakpoint) must not replay seconds of
            // simulation in one frame.
            if (accumulator >= tickSeconds)
                accumulator = 0.0;

            if (chat.isOpen())
            {
                mouseDeltaX = 0.0;
                mouseDeltaY = 0.0;
            }
            else if (mouseDeltaX != 0.0 || mouseDeltaY != 0.0)
            {
                player.turn((float) (mouseDeltaX * MOUSE_SENSITIVITY),
                        (float) (-mouseDeltaY * MOUSE_SENSITIVITY));
                mouseDeltaX = 0.0;
                mouseDeltaY = 0.0;
                normaliseYaw();
            }

            updateTargetBlock();
            renderFrame();
            chat.update();

            glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
            if (framebufferWidth[0] > 0 && framebufferHeight[0] > 0)
            {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                renderer.blit(framebufferWidth[0], framebufferHeight[0],
                        chat.getTexture(), chat.isVisible(),
                        (float) chat.getHeight() / 2.0f / Main.HEIGHT);
            }

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

    @Override
    public World getWorld()
    {
        return world;
    }

    @Override
    public void addEntity(Entity entity)
    {
        world.addEntity(entity);
    }

    @Override
    public void onChatMessage(String sender, String message)
    {
        if (chat != null)
            chat.addMessage(sender, message);
    }

    private void sendChat()
    {
        String text = chat.getInput().trim();
        chat.clearInput();

        if (text.isEmpty())
            return;

        if (nettyClient != null && nettyClient.isConnected())
            nettyClient.sendPacket(new ChatMessagePacket(text));
        else
            chat.addMessage(playerName, text);
    }

    private static void releaseMovementKeys()
    {
        Input.setKey(KeyEvent.VK_W, false);
        Input.setKey(KeyEvent.VK_A, false);
        Input.setKey(KeyEvent.VK_S, false);
        Input.setKey(KeyEvent.VK_D, false);
        Input.setKey(KeyEvent.VK_SPACE, false);
        Input.setKey(KeyEvent.VK_CONTROL, false);
    }

    private void writeChatComposite(String path)
    {
        int width = Main.WIDTH * Main.SCALE;
        int height = Main.HEIGHT * Main.SCALE;
        int target = glGenFramebuffers();
        int colour = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, colour);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glBindFramebuffer(GL_FRAMEBUFFER, target);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colour, 0);

        renderer.blit(width, height, chat.getTexture(), chat.isVisible(),
                (float) chat.getHeight() / 2.0f / Main.HEIGHT);

        ByteBuffer composed = MemoryUtil.memAlloc(width * height * 4);
        try
        {
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, composed);
            GlUtil.writePng(width, height, composed, path);
            System.out.println("Wrote       " + path);
        }
        catch (Exception e)
        {
            System.err.println("Could not write " + path + ": " + e.getMessage());
        }
        finally
        {
            MemoryUtil.memFree(composed);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glReadBuffer(GL_BACK);
        glBindTexture(GL_TEXTURE_2D, 0);
        glDeleteFramebuffers(target);
        glDeleteTextures(colour);
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

        if (waterDemo)
        {
            // One-block-deep pool over a brick floor, straight ahead.
            for (int x = 20; x < 46; x++)
            {
                for (int z = 34; z < 48; z++)
                {
                    world.setBlock(x, 63, z, com.craftclassic.common.blocks.Block.BRICK);
                    world.setBlock(x, 62, z, com.craftclassic.common.blocks.Block.WATER);
                }
            }
            world.setBlock(30, 61, 38, com.craftclassic.common.blocks.Block.STONE);
            world.setBlock(34, 61, 40, com.craftclassic.common.blocks.Block.STONE);

            location.setYaw(0.0f);
            location.setPitch(-0.004f);
        }
        else
        {
            location.setYaw(0.7f);
            location.setPitch(-0.0012f);
        }

        updateTargetBlock();

        if (chatDemo)
        {
            chat.addMessage("STEVE", "&aHELLO &lFROM &r&cTHE SERVER");
            chat.addMessage("ALEX", "&eTHIS &cIS &9CLASSICCRAFT");
            chat.setOpen(true);
            chat.append("&cCOLOUR &lTEST");
            chat.update();
        }

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

        if (chatDemo)
        {
            writeChatComposite("gpu-chat.png");

            // Closing the input should keep the recent messages but drop the
            // input line and cursor.
            chat.setOpen(false);
            chat.update();
            writeChatComposite("gpu-chat-closed.png");

            skipWindowDestroy = true;
        }

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
