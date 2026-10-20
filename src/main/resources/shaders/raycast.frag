#version 330 core

// GPU port of World.render(). Same ray construction, same one-dimensional
// DDA along each axis, same texture lookup and the same per-face shade/fog.
// The only intentional differences are:
//   - fog/sky are per fragment instead of leaking across pixels (see ADR 0016)
//   - the three axis walks are replaced by one nearest-hit voxel DDA
//
// Coordinate convention is unchanged: the world is 64^3 block indices, and a
// camera-space position p addresses block (floor(p) - 64).

layout(location = 0) out vec4  oColor;
layout(location = 1) out float oDepth;   // replaces World.zBuffer

uniform usampler3D uWorld;      // 64x64x64 R8UI, 0 = air, otherwise Block.getTextureId() + 1
uniform sampler2D   uAtlas;     // 16x768 block atlas, GL_NEAREST

uniform vec3  uCamPos;
uniform float uYaw;             // already multiplied by 100, as in World.render()
uniform float uPitch;           // already multiplied by 100
uniform vec2  uResolution;
uniform float uFov;
uniform float uFocal;
uniform float uFogDist;
uniform vec3  uSky;
uniform ivec3 uTarget;          // targeted block; x < 0 means none

const ivec3 kWorldSize = ivec3(64, 64, 64);
const ivec3 kOrigin    = ivec3(64, 64, 64);
const int   kAtlasRows = 48;    // 768 / 16: one block owns 48 atlas rows

void main()
{
    // World.render() advances screenY downwards; gl_FragCoord.y goes up.
    vec2 fc = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);
    vec2 p  = (fc - uResolution * 0.5) / uFocal;

    float cy = cos(uYaw),   sy = sin(uYaw);
    float cp = cos(uPitch), sp = sin(uPitch);

    float rz = uFov * cp + p.y * sp;
    float ry = p.y * cp - uFov * sp;
    vec3  dir = vec3(p.x * cy + rz * sy,
                     ry,
                     rz * cy - p.x * sy);

    vec3  cam  = uCamPos;
    ivec3 cell = ivec3(floor(cam));

    ivec3 step = ivec3(dir.x < 0.0 ? -1 : 1,
                       dir.y < 0.0 ? -1 : 1,
                       dir.z < 0.0 ? -1 : 1);

    vec3 inv = 1.0 / max(abs(dir), vec3(1e-9));
    vec3 tMax = vec3(
        (dir.x > 0.0 ? float(cell.x + 1) - cam.x : cam.x - float(cell.x)) * inv.x,
        (dir.y > 0.0 ? float(cell.y + 1) - cam.y : cam.y - float(cell.y)) * inv.y,
        (dir.z > 0.0 ? float(cell.z + 1) - cam.z : cam.z - float(cell.z)) * inv.z);

    float t    = 0.0;
    int   axis = 0;
    uint  id   = 0u;

    for (int i = 0; i < 512; ++i)
    {
        ivec3 b = cell - kOrigin;
        if (any(lessThan(b, ivec3(0))) || any(greaterThanEqual(b, kWorldSize)))
            break;                      // left the world, same as isInWorld()

        id = texelFetch(uWorld, b, 0).r;
        if (id != 0u)
            break;

        if (tMax.x < tMax.y)
        {
            if (tMax.x < tMax.z) { cell.x += step.x; t = tMax.x; tMax.x += inv.x; axis = 0; }
            else                 { cell.z += step.z; t = tMax.z; tMax.z += inv.z; axis = 2; }
        }
        else
        {
            if (tMax.y < tMax.z) { cell.y += step.y; t = tMax.y; tMax.y += inv.y; axis = 1; }
            else                 { cell.z += step.z; t = tMax.z; tMax.z += inv.z; axis = 2; }
        }

        if (t >= uFogDist)
            break;
    }

    vec3 color = uSky;
    float depth = 1e9;

    if (id != 0u)
    {
        vec3 hit = cam + dir * t;

        int texX;
        int texY;

        if (axis == 1)
        {
            // Top/bottom face.
            texX = int(hit.x * 16.0) & 15;
            texY = int(hit.z * 16.0) & 15;
            if (dir.y < 0.0)
                texY += 32;
        }
        else
        {
            // Side faces, projected on the (x + z) diagonal like the CPU path.
            texX = int((hit.x + hit.z) * 16.0) & 15;
            texY = (int(hit.y * 16.0) & 15) + 16;
        }

        // Stored value is (textureId + 1) with bit 7 set when the block is
        // selectable, so 0 can keep meaning "air".
        int  textureSlot = int(id & 127u) - 1;
        bool selectable  = (id & 128u) != 0u;

        vec3 texel = texelFetch(uAtlas, ivec2(texX, texY + textureSlot * kAtlasRows), 0).rgb;

        // Same highlight rule as CrossHair/World.render: the edge texels of the
        // targeted block's face, for selectable blocks only.
        bool onEdge = texX == 0 || texX == 15 || (texY & 15) == 0 || (texY & 15) == 15;
        if (selectable && uTarget.x >= 0 && all(equal(cell - kOrigin, uTarget)) && onEdge)
            texel = vec3(1.0, 1.0, 0.0);

        color = texel;

        float shade = (255.0 - float((axis + 2) % 3) * 50.0) / 255.0;   // fake face lighting
        float fog   = clamp(1.0 - t / uFogDist, 0.0, 1.0);

        color *= shade * fog;
        depth  = t;
    }

    oColor = vec4(color, 1.0);
    oDepth = depth;
}
