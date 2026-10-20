#version 330 core

// World-space billboard: the quad is built from the camera's right/up vectors
// and each corner is projected individually, so the rasteriser applies
// perspective correction (no fisheye stretching at the screen edges).

layout(location = 0) in vec2 aPos;   // -0.5 .. 0.5

uniform vec3  uCenter;
uniform vec2  uSize;                 // world units
uniform vec3  uRight;
uniform vec3  uUp;
uniform vec3  uCamPos;
uniform float uYaw;
uniform float uPitch;
uniform float uFocal;
uniform vec2  uResolution;

out vec2  vUV;
out float vDepth;

void main()
{
    vec3 world = uCenter
               + uRight * (aPos.x * uSize.x)
               + uUp    * (-aPos.y * uSize.y);

    vec3 rel = world - uCamPos;

    float cy = cos(uYaw);
    float sy = sin(uYaw);
    float cp = cos(uPitch);
    float sp = sin(uPitch);

    float xx = cy * rel.x - sy * rel.z;
    float zz = sy * rel.x + cy * rel.z;
    float yy = cp * rel.y - sp * zz;
    zz = sp * rel.y + cp * zz;

    vec2 pixel = vec2(xx * uFocal / zz + uResolution.x * 0.5,
                      yy * uFocal / zz + uResolution.y * 0.5);

    gl_Position = vec4(pixel.x / uResolution.x * 2.0 - 1.0,
                       1.0 - pixel.y / uResolution.y * 2.0,
                       0.0, 1.0);

    vUV = vec2(1.0 - (aPos.x + 0.5), aPos.y + 0.5);
    vDepth = zz;
}
