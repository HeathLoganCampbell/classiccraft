#version 330 core

in vec2  vUV;
in float vDepth;

layout(location = 0) out vec4  oColor;
layout(location = 1) out float oDepth;

uniform sampler2D uSprite;
uniform float uFogDist;

void main()
{
    vec4 texel = texture(uSprite, vUV);

    // The loader already removed the black background (and one-pixel tips),
    // so a plain alpha test is enough.
    if (texel.a < 0.5)
        discard;

    oColor = vec4(texel.rgb, 1.0);
    oDepth = vDepth;
    gl_FragDepth = clamp(vDepth / uFogDist, 0.0, 1.0);
}


