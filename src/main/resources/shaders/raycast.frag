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
uniform float uTime;
uniform vec3  uSky;
uniform ivec3 uTarget;          // targeted block; x < 0 means none

const ivec3 kWorldSize = ivec3(64, 64, 64);
const ivec3 kOrigin    = ivec3(64, 64, 64);
const int   kAtlasRows = 48;    // 768 / 16: one block owns 48 atlas rows
const int   kWaterSlot = 13;    // WaterBlock's texture id

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

    float t        = 0.0;
    int   axis     = 0;
    uint  id       = 0u;

    vec3  color       = vec3(0.0);
    float coverage    = 0.0;
    float depth       = 1e9;
    bool  insideWater = false;

    for (int i = 0; i < 512; ++i)
    {
        ivec3 b = cell - kOrigin;
        if (any(lessThan(b, ivec3(0))) || any(greaterThanEqual(b, kWorldSize)))
            break;                      // left the world, same as isInWorld()

        id = texelFetch(uWorld, b, 0).r;

        if (id != 0u)
        {
            // Stored value is (textureId + 1) with bit 7 set when the block is
            // selectable, so 0 can keep meaning "air".
            int  textureSlot = int(id & 127u) - 1;
            bool selectable  = (id & 128u) != 0u;
            bool isWater     = textureSlot == kWaterSlot;

            if (isWater && insideWater)
            {
                // Internal boundary between two water cells: draw no face, so
                // the water volume does not show vertical "walls" as the ray
                // steps from cell to cell. A small tint keeps deep water from
                // washing out against the sky.
                vec3 tint = vec3(0.43, 0.29, 0.93);
                color    += (1.0 - coverage) * 0.08 * tint;
                coverage += (1.0 - coverage) * 0.08;

                if (coverage > 0.95)
                    break;
            }
            else
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

                if (depth > 1e8)
                    depth = t;

                if (isWater)
                {
                    texX = (texX + int(uTime * 6.0)) & 15;
                    texY = (texY + int(uTime * 4.0)) & 15;
                }

                vec3 texel = texelFetch(uAtlas, ivec2(texX, texY + textureSlot * kAtlasRows), 0).rgb;

                if (isWater)
                {
                    float wave = sin(hit.x * 2.2 + uTime * 2.0) * 0.5
                               + sin(hit.z * 1.7 - uTime * 1.3) * 0.5;
                    texel *= 0.85 + 0.22 * wave;
                }
                else
                {
                    bool onEdge = texX == 0 || texX == 15 || (texY & 15) == 0 || (texY & 15) == 15;
                    if (selectable && uTarget.x >= 0 && all(equal(b, uTarget)) && onEdge)
                        texel = vec3(1.0, 1.0, 0.0);
                }

                float shade = (255.0 - float((axis + 2) % 3) * 50.0) / 255.0;
                float fog   = clamp(1.0 - t / uFogDist, 0.0, 1.0);
                vec3  lit   = texel * shade * fog;

                if (isWater)
                {
                    float alpha = 0.55;
                    color    += (1.0 - coverage) * alpha * lit;
                    coverage += (1.0 - coverage) * alpha;
                    insideWater = true;

                    if (coverage > 0.95)
                        break;
                }
                else
                {
                    color += (1.0 - coverage) * lit;
                    coverage = 1.0;
                    break;
                }
            }
        }
        else
        {
            insideWater = false;
        }

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

    color += (1.0 - coverage) * uSky;

    oColor = vec4(color, 1.0);
    oDepth = depth;
    gl_FragDepth = (coverage > 0.0) ? clamp(t / uFogDist, 0.0, 1.0) : 1.0;
}
