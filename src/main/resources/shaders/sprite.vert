#version 330 core

// Doom-style sprite quad: vertical in world space, turned to face the camera
// only around the vertical axis, feet anchored by the CPU side.

layout(location = 0) in vec2 aPos;   // x: left..right, y: top..bottom

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

    // Real clip-space position with w = view depth. Dividing here and passing
    // w = 1 would make UV/depth interpolation affine and warp the sprite when
    // the top and bottom corners have different depths (looking up or down).
    gl_Position = vec4(xx * uFocal * 2.0 / uResolution.x,
                       -yy * uFocal * 2.0 / uResolution.y,
                       0.0,
                       zz);

    // Upright and unmirrored: the texture's left column is the sprite's left.
    vUV = vec2(aPos.x + 0.5, aPos.y + 0.5);
    vDepth = zz;
}
