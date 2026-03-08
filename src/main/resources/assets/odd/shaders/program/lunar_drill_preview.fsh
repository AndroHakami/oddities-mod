#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Origin;     // block pos (integer in floats)
uniform vec3 UAxis;      // axis-aligned signed unit (±1,0,0 etc)
uniform vec3 VAxis;      // axis-aligned signed unit
uniform vec3 IntoAxis;   // axis-aligned signed unit

uniform float Depth;     // 1..6
uniform float Mask0;     // 0..255 (bits 0..7)
uniform float Mask1;     // bits 8..15
uniform float Mask2;     // bits 16..23
uniform float Mask3;     // bits 24..31

uniform float Charge;     // 0..1
uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x,0.0,1.0); }

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

float hash12(vec2 p){
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}
float hash31(vec3 p){
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453123);
}

// Convert float 0..255 to int safely
int b2i(float f){ return int(floor(f + 0.5)); }

// idx 0..24 -> test bit from Mask0..Mask3
bool maskBit(int idx){
    int byte = idx >> 3;
    int bit  = idx & 7;
    int m = (byte == 0) ? b2i(Mask0) :
            (byte == 1) ? b2i(Mask1) :
            (byte == 2) ? b2i(Mask2) : b2i(Mask3);
    return ((m >> bit) & 1) == 1;
}

// dot with axis-aligned signed unit vec3 (components are -1,0,1)
int dotAxis(ivec3 a, vec3 axis){
    ivec3 ax = ivec3(int(axis.x), int(axis.y), int(axis.z));
    return a.x * ax.x + a.y * ax.y + a.z * ax.z;
}

// exact match to your layoutPatternWithDepth:
// base = origin + u*dc + v*(-dr) + into*k
bool isSelectedBlock(ivec3 bp){
    ivec3 o = ivec3(int(Origin.x), int(Origin.y), int(Origin.z));
    ivec3 d = bp - o;

    int dc = dotAxis(d, UAxis);
    int dr = -dotAxis(d, VAxis);
    int k  = dotAxis(d, IntoAxis);

    int depthBlocks = int(Depth + 0.5);

    if (k < 0 || k >= depthBlocks) return false;
    if (dc < -2 || dc > 2) return false;
    if (dr < -2 || dr > 2) return false;

    int r = dr + 2;
    int c = dc + 2;
    int idx = r * 5 + c; // 0..24
    return maskBit(idx);
}

// distance from point to expanded AABB of block [bp,bp+1] expanded by pad
float distToBlockAabb(vec3 p, ivec3 bp, float pad){
    vec3 bmin = vec3(bp) - vec3(pad);
    vec3 bmax = vec3(bp) + vec3(1.0 + pad);
    vec3 q = max(max(bmin - p, p - bmax), vec3(0.0));
    return length(q);
}

// coordinate along a signed axis inside the block (0..1), respecting axis sign
float coordAlong(vec3 rel, vec3 axis){
    if (axis.x > 0.5)  return rel.x;
    if (axis.x < -0.5) return 1.0 - rel.x;
    if (axis.y > 0.5)  return rel.y;
    if (axis.y < -0.5) return 1.0 - rel.y;
    if (axis.z > 0.5)  return rel.z;
    return 1.0 - rel.z;
}

/* ===================================================================== */
/* =====================  CRESCENT (AA + ANTI-SHIMMER) ================== */
/* ===================================================================== */

float sdCircle(vec2 p, float r) { return length(p) - r; }

// signed distance to a crescent: inside big circle AND outside shifted smaller circle
float sdCrescent(vec2 p) {
    float r1 = 0.58;
    float r2 = 0.50;
    vec2  o  = vec2(0.24, 0.0);
    float d1 = sdCircle(p, r1);
    float d2 = sdCircle(p - o, r2);
    return max(d1, -d2);
}

// AA mask 0..1 using fwidth (stable at grazing angles)
float crescentAA(vec2 p) {
    float d = sdCrescent(p * 1.25);
    float aa = fwidth(d) * 1.6 + 0.002;
    return 1.0 - smoothstep(0.0, aa, d);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;

    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float z = texture(DepthSampler, texCoord).r;
    z = min(z, 0.9995);

    vec3 rp = worldPos(vec3(texCoord, z));
    vec3 p  = rp;

    ivec3 bp = ivec3(floor(p));

    // fixed pad => slightly bigger than blocks, never scales over time
    float pad = 0.10;

    float bestDist = 999.0;
    bool selfSel = false;

    if (isSelectedBlock(bp)) {
        selfSel = true;
        bestDist = 0.0;
    } else {
        // 6-neighbor (fast)
        ivec3 nb;
        nb = bp + ivec3( 1,0,0); if (isSelectedBlock(nb)) bestDist = min(bestDist, distToBlockAabb(p, nb, pad));
        nb = bp + ivec3(-1,0,0); if (isSelectedBlock(nb)) bestDist = min(bestDist, distToBlockAabb(p, nb, pad));
        nb = bp + ivec3(0, 1,0); if (isSelectedBlock(nb)) bestDist = min(bestDist, distToBlockAabb(p, nb, pad));
        nb = bp + ivec3(0,-1,0); if (isSelectedBlock(nb)) bestDist = min(bestDist, distToBlockAabb(p, nb, pad));
        nb = bp + ivec3(0,0, 1); if (isSelectedBlock(nb)) bestDist = min(bestDist, distToBlockAabb(p, nb, pad));
        nb = bp + ivec3(0,0,-1); if (isSelectedBlock(nb)) bestDist = min(bestDist, distToBlockAabb(p, nb, pad));
    }

    if (!selfSel && bestDist > 5.0) { fragColor = vec4(base, 1.0); return; }

    float c = sat(Charge);

    vec3 yPale  = vec3(1.00, 0.98, 0.78);
    vec3 yHeavy = vec3(1.00, 0.94, 0.22);
    vec3 y = mix(yPale, yHeavy, c);

    // always visible while charging, ramps strongly with charge
    float master = A * (0.20 + 0.80 * c);

    // WORLD-STABLE falloffs (replaces depth-derivative edge => no scanlines)
    float fillFalloff = selfSel ? 1.0 : exp(-bestDist * 10.0);
    float rimFalloff  = exp(-max(bestDist - 0.01, 0.0) * 32.0); // tight rim near boundary

    vec3 col = base;

    float kFill = 0.22 + 0.28 * c; // heavier with charge
    float kRim  = 0.18 + 0.30 * c; // crisp border without screen-space stripes

    col += y      * (fillFalloff * kFill * master);
    col += yHeavy * (rimFalloff  * kRim  * master);

    // --- Crescent moons (only on selected blocks) ---
    if (selfSel) {
        vec3 rel = fract(p);

        float u = coordAlong(rel, UAxis);
        float v = coordAlong(rel, VAxis);
        vec2 uv = vec2(u, v);

        float seed = hash31(vec3(bp));
        float spd  = mix(0.06, 0.11, seed);
        vec2 drift = vec2(spd, -spd * 0.65);

        // lower tiling => less moire
        vec2 tuv = uv * 1.85 + drift * iTime + vec2(seed * 9.7, seed * 3.3);

        vec2 gid  = floor(tuv);
        vec2 cell = fract(tuv) - 0.5;

        float gseed = hash12(gid + seed * 37.0);
        float rot = gseed * 6.2831853;
        float cs = cos(rot), sn = sin(rot);
        cell = mat2(cs, -sn, sn, cs) * cell;

        float m = crescentAA(cell);

        // fade near block edges (prevents crawling/overlap)
        float edgeDist = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
        float edgeFade = smoothstep(0.06, 0.16, edgeDist);
        m *= edgeFade;

        // anti-shimmer: fade detail when pixel footprint gets too big (grazing angles)
        float pix = max(fwidth(tuv.x), fwidth(tuv.y));
        float shimmerFade = 1.0 - smoothstep(0.10, 0.30, pix);
        m *= shimmerFade;

        float moonA = m * (0.14 + 0.32 * c) * master;
        vec3 moonCol = mix(vec3(1.0, 1.0, 0.95), yHeavy, 0.35);
        col += moonCol * moonA;
    }

    fragColor = vec4(col, 1.0);
}