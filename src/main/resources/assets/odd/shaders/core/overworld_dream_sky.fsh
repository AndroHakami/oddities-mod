#version 330 compatibility

uniform float iTime;
uniform float NightAmount;
uniform float Rain;
uniform float Thunder;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform mat4 SkyInvRotMat;

in vec2 vUv;
out vec4 fragColor;

#define PI 3.14159265359
#define TAU 6.28318530718

float sat(float x) { return clamp(x, 0.0, 1.0); }

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec2 hash22(vec2 p) {
    float n = hash21(p);
    return vec2(n, hash21(p + n + 19.19));
}

float valueNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);

    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.54;
    for (int i = 0; i < 5; i++) {
        v += a * valueNoise2(p);
        p *= 2.03;
        a *= 0.52;
    }
    return v;
}

vec3 viewDirFromUv(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 clip = vec4(ndc, 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    view /= max(view.w, 1e-6);

    vec3 dirView = normalize(view.xyz);
    return normalize((InvViewMat * vec4(dirView, 0.0)).xyz);
}

vec2 equalAreaUv(vec3 d) {
    float theta = atan(d.z, d.x) / TAU + 0.5;
    float v = d.y * 0.5 + 0.5;
    return vec2(theta, v);
}

vec3 dirFromEqualAreaUv(vec2 uv) {
    float theta = (uv.x - 0.5) * TAU;
    float y = clamp(uv.y * 2.0 - 1.0, -1.0, 1.0);
    float r = sqrt(max(0.0, 1.0 - y * y));
    return vec3(cos(theta) * r, y, sin(theta) * r);
}

vec2 billboardLocal(vec3 skyDir, vec3 centerDir) {
    vec3 helper = abs(centerDir.y) < 0.98 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(helper, centerDir));
    vec3 bitangent = cross(centerDir, tangent);
    return vec2(dot(skyDir, tangent), dot(skyDir, bitangent));
}

float sqSoft(vec2 p, float halfSize, float blur) {
    float d = max(abs(p.x), abs(p.y));
    return 1.0 - smoothstep(halfSize, halfSize + blur, d);
}

vec2 quantize(vec2 p, float cells) {
    return floor(p * cells + 0.5) / cells;
}

float star5(vec2 p, float r) {
    float ang = atan(p.y, p.x);
    float k = 0.5 + 0.5 * cos(5.0 * ang);
    float target = mix(r * 0.55, r, pow(k, 2.1));
    float d = length(p);
    return 1.0 - smoothstep(target, target + r * 0.18, d);
}

vec3 softStarBillboard(vec3 skyDir, vec3 centerDir, float size, vec3 color) {
    vec2 p = billboardLocal(skyDir, centerDir);
    float core = star5(p, size);
    float d = length(p);
    float halo = exp(-(d * d) / max(1e-5, size * size * 2.35));
    return color * (core + halo * 0.34);
}

vec3 pixelSquareBillboard(vec3 skyDir, vec3 centerDir, float size, vec3 color, float pixelCells) {
    vec2 p = billboardLocal(skyDir, centerDir) / max(size, 1e-6);
    vec2 q = quantize(p, pixelCells);

    float core = sqSoft(q, 0.28, 0.10);
    float halo = sqSoft(q, 0.62, 0.22) * 0.14;

    return color * (core + halo);
}

vec3 staticStars(vec3 skyDir) {
    vec2 uv = equalAreaUv(normalize(skyDir));
    float skyFade = mix(0.44, 1.0, smoothstep(-0.08, 0.78, skyDir.y));
    vec3 col = vec3(0.0);

    // Big stars
    {
        vec2 grid = vec2(30.0, 15.0);
        vec2 id = floor(uv * grid);

        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            vec2 cid = id + vec2(float(i), float(j));
            cid.x = mod(cid.x + grid.x, grid.x);
            cid.y = clamp(cid.y, 0.0, grid.y - 1.0);

            vec2 rnd = hash22(cid + 17.3);
            float spawn = step(0.905, rnd.x);

            vec2 cuv = (cid + vec2(rnd.x, rnd.y)) / grid;
            vec3 centerDir = dirFromEqualAreaUv(cuv);

            float size = mix(0.0095, 0.0155, rnd.y);
            vec3 starCol = mix(vec3(0.76, 0.84, 1.00), vec3(1.00, 0.90, 0.98), rnd.y);
            col += softStarBillboard(skyDir, centerDir, size, starCol) * spawn * 1.02;
        }
    }

    // Medium stars
    {
        vec2 grid = vec2(72.0, 36.0);
        vec2 id = floor(uv * grid);

        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            vec2 cid = id + vec2(float(i), float(j));
            cid.x = mod(cid.x + grid.x, grid.x);
            cid.y = clamp(cid.y, 0.0, grid.y - 1.0);

            vec2 rnd = hash22(cid + 43.7);
            float spawn = step(0.932, rnd.x);

            vec2 cuv = (cid + vec2(rnd.x, rnd.y)) / grid;
            vec3 centerDir = dirFromEqualAreaUv(cuv);

            float size = mix(0.0035, 0.0062, rnd.y);
            vec3 starCol = mix(vec3(0.74, 0.82, 1.00), vec3(0.98, 0.86, 1.00), rnd.y);
            col += pixelSquareBillboard(skyDir, centerDir, size, starCol, 5.0) * spawn;
        }
    }

    // Tiny stars
    {
        vec2 grid = vec2(150.0, 75.0);
        vec2 id = floor(uv * grid);

        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            vec2 cid = id + vec2(float(i), float(j));
            cid.x = mod(cid.x + grid.x, grid.x);
            cid.y = clamp(cid.y, 0.0, grid.y - 1.0);

            vec2 rnd = hash22(cid + 91.3);
            float spawn = step(0.952, rnd.x);

            vec2 cuv = (cid + vec2(rnd.x, rnd.y)) / grid;
            vec3 centerDir = dirFromEqualAreaUv(cuv);

            float size = mix(0.0013, 0.0022, rnd.y);
            vec3 starCol = mix(vec3(0.72, 0.80, 1.00), vec3(0.94, 0.88, 1.00), rnd.y);
            col += pixelSquareBillboard(skyDir, centerDir, size, starCol, 4.0) * spawn * 0.92;
        }
    }

    return col * skyFade;
}

float curtainWindow(float h, float edge, float depth, float feather) {
    float top = 1.0 - smoothstep(edge - feather, edge + feather, h);
    float bottom = smoothstep(edge - depth - feather, edge - depth + feather, h);
    return top * bottom;
}

vec3 auroraLayer(
vec3 skyDir,
float t,
vec2 orient,
float scale,
float speed,
float edgeBase,
float edgeAmp,
float depthBase,
float depthAmp,
vec3 cA,
vec3 cB,
vec3 cC,
float intensity
) {
    vec2 o = normalize(orient);
    vec2 n = vec2(-o.y, o.x);

    float sweep = dot(skyDir.xz, o);
    float cross = dot(skyDir.xz, n);
    float h = skyDir.y * 0.5 + 0.5;

    vec2 p0 = vec2(sweep * scale + t * speed, h * 2.3 + cross * 0.70);
    vec2 p1 = vec2(sweep * scale * 1.9 - t * speed * 0.55, cross * 1.8 + h * 4.1);
    vec2 p2 = vec2(cross * scale * 1.1 + t * speed * 0.28, sweep * 1.4 - h * 2.9);

    float n0 = fbm(p0 + vec2(3.2, -1.7));
    float n1 = fbm(p1 + vec2(-2.4, 4.7));
    float n2 = fbm(p2 + vec2(6.1, 1.8));

    float fineA = valueNoise2(vec2(sweep * scale * 6.0 - t * speed * 1.8, cross * 7.0 + h * 10.0));
    float fineB = valueNoise2(vec2(cross * scale * 8.0 + t * speed * 1.2, sweep * 9.0 - h * 8.0));

    float edge = edgeBase + (n0 - 0.5) * edgeAmp + cross * 0.07 + (n1 - 0.5) * 0.06;
    float depth = depthBase + n2 * depthAmp;

    edge = clamp(edge, 0.12, 0.98);
    depth = clamp(depth, 0.10, 0.55);

    float band = curtainWindow(h, edge, depth, 0.030);
    float strands = 0.58 + 0.42 * pow(smoothstep(0.48, 0.92, fineA), 1.8);
    float wisps = 0.70 + 0.30 * pow(smoothstep(0.58, 0.96, fineB), 2.4);
    float shimmer = 0.94 + 0.06 * sin(t * 0.42 + sweep * 6.0 + n1 * 4.0);

    float alpha = band * strands * wisps * shimmer * intensity;

    vec3 col = mix(cA, cB, smoothstep(0.35, 0.80, n1));
    col = mix(col, cC, smoothstep(0.65, 1.00, n2) * 0.60);
    col *= 0.62 + 0.38 * smoothstep(0.40, 0.90, n0);

    return col * alpha;
}

vec3 dreamyAurora(vec3 skyDir, float t) {
    float h = skyDir.y * 0.5 + 0.5;

    // make it cover almost the whole sky dome
    float domeMask = smoothstep(0.02, 0.08, h) * (1.0 - smoothstep(0.985, 0.999, h));
    if (domeMask <= 0.0) return vec3(0.0);

    float slowT = t * 0.085;

    vec3 deepBlue   = vec3(0.12, 0.20, 0.52);
    vec3 blue       = vec3(0.18, 0.34, 0.74);
    vec3 cyan       = vec3(0.34, 0.84, 0.88);
    vec3 purple     = vec3(0.44, 0.26, 0.78);
    vec3 lavender   = vec3(0.72, 0.56, 0.92);

    vec3 col = vec3(0.0);

    // huge broad veil
    col += auroraLayer(
    skyDir, slowT,
    vec2(1.0, 0.35),
    2.0, 0.28,
    0.98, 0.40,
    0.36, 0.22,
    deepBlue, blue, cyan,
    0.12
    );

    // medium broad veil
    col += auroraLayer(
    skyDir, slowT,
    vec2(-0.55, 1.0),
    2.8, 0.24,
    0.82, 0.34,
    0.30, 0.18,
    deepBlue, purple, lavender,
    0.11
    );

    // overhead / central activity
    col += auroraLayer(
    skyDir, slowT,
    vec2(0.75, -0.68),
    3.6, 0.32,
    0.66, 0.28,
    0.24, 0.15,
    blue, cyan, lavender,
    0.10
    );

    // lower-mid dome ribbons
    col += auroraLayer(
    skyDir, slowT,
    vec2(0.22, 1.0),
    4.8, 0.30,
    0.52, 0.24,
    0.20, 0.12,
    deepBlue, blue, purple,
    0.09
    );

    // finer higher-frequency details
    col += auroraLayer(
    skyDir, slowT,
    vec2(-1.0, 0.18),
    6.0, 0.36,
    0.90, 0.26,
    0.14, 0.10,
    blue, cyan, lavender,
    0.06
    );

    // soft background mist so the sky feels filled, not patchy
    vec2 pA = vec2(skyDir.x * 1.6 + skyDir.z * 0.5, skyDir.y * 1.8);
    vec2 pB = vec2(skyDir.z * 1.9 - skyDir.x * 0.8, skyDir.y * 2.3);
    float m1 = fbm(pA + vec2(slowT * 0.12, -slowT * 0.08));
    float m2 = fbm(pB + vec2(-slowT * 0.10, slowT * 0.07));
    float mist = smoothstep(0.54, 0.80, m1) * 0.18 + smoothstep(0.60, 0.86, m2) * 0.12;

    vec3 mistCol = mix(deepBlue, cyan, smoothstep(0.45, 0.95, m2));
    mistCol = mix(mistCol, purple, smoothstep(0.62, 1.0, m1) * 0.35);
    col += mistCol * mist * domeMask * 0.10;

    return col * domeMask;
}

vec3 daySky(vec3 worldDir) {
    float y = clamp(worldDir.y, -1.0, 1.0);
    float zen = smoothstep(-0.08, 0.88, y);

    vec3 horizonCol = vec3(0.62, 0.77, 1.00);
    vec3 zenithCol = vec3(0.20, 0.44, 0.94);
    return mix(horizonCol, zenithCol, zen);
}

vec3 dreamNightSky(vec3 worldDir, vec3 skyDir) {
    float y = clamp(worldDir.y, -1.0, 1.0);
    float zen = smoothstep(-0.10, 0.95, y);

    vec3 horizonBlue   = vec3(0.05, 0.08, 0.24);
    vec3 horizonPurple = vec3(0.12, 0.07, 0.24);
    vec3 zenithBlue    = vec3(0.08, 0.12, 0.40);
    vec3 zenithPurple  = vec3(0.20, 0.09, 0.36);

    vec3 horizonCol = mix(horizonBlue, horizonPurple, 0.48);
    vec3 zenithCol  = mix(zenithBlue, zenithPurple, 0.58);

    vec3 col = mix(horizonCol, zenithCol, zen);

    vec2 uv = equalAreaUv(normalize(skyDir));
    vec2 p = uv * 4.0;

    float nebulaA = fbm(p * 1.05 + vec2(4.2, -1.7));
    float nebulaB = fbm(p * 1.95 + vec2(-2.8, 6.1));
    float nebula = mix(nebulaA, nebulaB, 0.5);

    float band = smoothstep(0.18, 0.95, y) * (1.0 - smoothstep(0.88, 1.0, y));
    float mist = smoothstep(0.52, 0.78, nebula) * band;

    vec3 blueGlow = vec3(0.14, 0.22, 0.58);
    vec3 purpleGlow = vec3(0.44, 0.17, 0.58);
    col += mix(blueGlow, purpleGlow, 0.5 + 0.5 * valueNoise2(uv * 6.0 + 3.7)) * mist * 0.20;

    float crown = smoothstep(0.35, 1.0, y);
    col += vec3(0.03, 0.05, 0.13) * crown * 0.34;

    float horizonLift = 1.0 - smoothstep(-0.15, 0.30, y);
    col += vec3(0.02, 0.02, 0.06) * horizonLift * 0.28;

    return col;
}

void main() {
    vec3 worldDir = viewDirFromUv(vUv);
    vec3 skyDir = normalize((SkyInvRotMat * vec4(worldDir, 0.0)).xyz);

    vec3 nightBase = dreamNightSky(worldDir, skyDir) * 0.70; // 30% darker
    vec3 col = mix(daySky(worldDir), nightBase, NightAmount);

    col += staticStars(skyDir) * NightAmount;
    col += dreamyAurora(skyDir, iTime) * NightAmount;

    float weather = clamp(1.0 - Rain * 0.45 - Thunder * 0.35, 0.30, 1.0);
    col *= weather;

    fragColor = vec4(col, 1.0);
}