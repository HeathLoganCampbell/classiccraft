#version 330 core

layout(location = 0) in vec2 aPos;   // -0.5 .. 0.5 quad

uniform vec2 uCenter;                // low-res pixels, y down
uniform vec2 uSize;                  // low-res pixels
uniform vec2 uResolution;

out vec2 vUV;

void main()
{
    vec2 pixel = uCenter + aPos * uSize;
    vec2 ndc = vec2(pixel.x / uResolution.x * 2.0 - 1.0,
                    1.0 - pixel.y / uResolution.y * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);

    // The CPU renderer flips both texture axes when it blits sprites.
    vUV = vec2(1.0 - (aPos.x + 0.5), aPos.y + 0.5);
}
