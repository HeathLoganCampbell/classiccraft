#version 330 core

// Nearest-neighbour upscale of the low-resolution framebuffer, plus the
// crosshair that CrossHair.java used to poke into the int[] by hand.

in  vec2 vUV;
out vec4 oColor;

uniform sampler2D uFrame;
uniform vec2  uLowRes;
uniform ivec2 uCrosshair;

void main()
{
    vec3 color = texture(uFrame, vUV).rgb;

    // vUV is bottom-up; crosshair coordinates are top-down screen pixels.
    ivec2 px = ivec2(floor(vec2(vUV.x, 1.0 - vUV.y) * uLowRes));

    bool vertical   = px.x == uCrosshair.x && px.y >= uCrosshair.y - 3 && px.y <= uCrosshair.y + 1;
    bool horizontal = px.y == uCrosshair.y - 1 && px.x >= uCrosshair.x - 2 && px.x <= uCrosshair.x + 2;

    if (vertical || horizontal)
        color = 1.0 - color;        // 0xFFFFFF - pixel

    oColor = vec4(color, 1.0);
}
