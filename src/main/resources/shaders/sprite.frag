#version 330 core

in vec2 vUV;

layout(location = 0) out vec4  oColor;
layout(location = 1) out float oDepth;

uniform sampler2D uSprite;
uniform float uZ;
uniform float uFogDist;

void main()
{
    vec4 texel = texture(uSprite, vUV);

    // player.png has no alpha channel; black is the sprite background.
    if (texel.r < 0.004 && texel.g < 0.004 && texel.b < 0.004)
        discard;

    oColor = vec4(texel.rgb, 1.0);
    oDepth = uZ;
    gl_FragDepth = clamp(uZ / uFogDist, 0.0, 1.0);
}
