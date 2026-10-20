#version 330 core

in vec2  vUV;
in float vDepth;

layout(location = 0) out vec4  oColor;
layout(location = 1) out float oDepth;

uniform vec4      uColor;
uniform sampler2D uSprite;
uniform int       uTextured;
uniform float     uFogDist;

void main()
{
    vec4 colour = uColor;

    if (uTextured != 0)
    {
        colour = texture(uSprite, vUV);
        if (colour.a < 0.5)
            discard;
    }

    oColor = vec4(colour.rgb, 1.0);
    oDepth = vDepth;
    gl_FragDepth = clamp(vDepth / uFogDist, 0.0, 1.0);
}
