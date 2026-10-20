#version 330 core

in vec2  vUV;
in float vDepth;

layout(location = 0) out vec4  oColor;
layout(location = 1) out float oDepth;

uniform vec4  uColor;
uniform float uFogDist;

void main()
{
    oColor = uColor;
    oDepth = vDepth;
    gl_FragDepth = clamp(vDepth / uFogDist, 0.0, 1.0);
}
