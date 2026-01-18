#version 330 compatibility
#define STEPS 280
#define MIN_DIST 0.001
#define MAX_DIST 1400.0

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;
uniform vec3 BlockPosition;

uniform float iTime;
uniform float Intensity;

uniform float Radius;  // 0..45 during radiation (20s), and kept at 45 during pillar
uniform float Mode;    // 0 = radiation, 1 = pillar

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }
float hash(float x) { return fract(sin(x) * 43758.5453123); }

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

float sdCylinder(vec3 p, float r) { return length(p.xz) - r; }
float sdSphere(vec3 p, float r) { return length(p) - r; }

mat2 rot2(float a){
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

#define PI 3.141592653589793
#define TAU 6.283185307179586

const float RADIATION_MAX_R = 45.0;

// Big area grade tuning
const float SKY_RANGE_MULT = 2.0;
const float FALLOFF_POWER  = 0.55;

/* ===================== Noise / fbm for sky clouds ===================== */
float n2(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(dot(i, vec2(127.1, 311.7)));
    float b = hash(dot(i + vec2(1.0, 0.0), vec2(127.1, 311.7)));
    float c = hash(dot(i + vec2(0.0, 1.0), vec2(127.1, 311.7)));
    float d = hash(dot(i + vec2(1.0, 1.0), vec2(127.1, 311.7)));
    vec2 u = f*f*(3.0-2.0*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}
float fbm(vec2 p){
    float v = 0.0;
    float a = 0.55;
    for(int i=0;i<4;i++){
        v += a * n2(p);
        p *= 2.02;
        a *= 0.52;
    }
    return v;
}
/* ======================================================================== */

/* ===================== Radiation symbol helpers ===================== */
float angDiff(float a, float b) {
    float d = a - b;
    d = mod(d + PI, TAU) - PI;
    return abs(d);
}
float ringMask(vec2 p, float r, float t) {
    float d = abs(length(p) - r);
    return 1.0 - smoothstep(t, t * 1.6, d);
}
float diskMask(vec2 p, float r) {
    float d = length(p);
    return 1.0 - smoothstep(r, r * 1.05, d);
}
float wedgeMask(vec2 p, float ang0, float halfAng, float r0, float r1) {
    float a = atan(p.y, p.x);
    float da = angDiff(a, ang0);
    float angM = 1.0 - smoothstep(halfAng, halfAng * 1.15, da);

    float rr = length(p);
    float radIn  = smoothstep(r0, r0 * 1.02, rr);
    float radOut = 1.0 - smoothstep(r1, r1 * 1.02, rr);

    return angM * radIn * radOut;
}
float radiationSymbol(vec2 p, float R) {
    if (R <= 0.05) return 0.0;

    float rr = length(p);
    float bound = 1.0 - smoothstep(R, R * 1.04 + 0.12, rr);

    float tOuter = max(0.14, 0.055 * R);
    float tInner = max(0.12, 0.040 * R);

    float outerR = 0.95 * R;
    float innerR = 0.22 * R;

    float centerFill = diskMask(p, 0.13 * R);
    float centerRing = ringMask(p, 0.18 * R, tInner * 0.55);

    float outerRing = ringMask(p, outerR, tOuter);
    float innerRing = ringMask(p, innerR, tInner);

    float bladeInner = 0.30 * R;
    float bladeOuter = 0.78 * R;
    float halfAng = radians(28.0);

    float b0 = wedgeMask(p, 0.0,               halfAng, bladeInner, bladeOuter);
    float b1 = wedgeMask(p, TAU / 3.0,         halfAng, bladeInner, bladeOuter);
    float b2 = wedgeMask(p, 2.0 * TAU / 3.0,   halfAng, bladeInner, bladeOuter);

    float blades = max(b0, max(b1, b2));
    blades *= smoothstep(bladeInner, bladeInner + tInner * 0.8, rr);
    blades *= (1.0 - smoothstep(bladeOuter, bladeOuter + tOuter * 0.9, rr));

    float symbol = 0.0;
    symbol = max(symbol, outerRing);
    symbol = max(symbol, innerRing);
    symbol = max(symbol, centerRing);
    symbol = max(symbol, centerFill * 0.85);
    symbol = max(symbol, blades);

    float glow = symbol;
    glow += symbol * symbol * 0.85;
    glow += pow(symbol, 3.0) * 0.65;

    return sat(glow) * bound;
}
/* ==================================================================== */

float ringWave(vec3 p, float t, float speed, float width) {
    float r = length(p.xz);
    float R = t * speed;
    float x = (r - R) / max(width, 0.001);
    return exp(-x * x);
}

/* ===================== Pillar radius (less rippley) ===================== */
float beamRadius(float t, float y) {
    float target = max(Radius, 4.70);
    float scale = target / 4.70;

    float settle = smoothstep(0.0, 0.08, t);
    float base = mix(4.70 * 0.92, 4.70, settle);

    float wob  = 0.030 * sin(y * 0.18 + t * 6.0)
               + 0.020 * sin(y * 0.33 - t * 4.0);

    return base * scale + wob;
}

float sDistBeam(vec3 p, float t) {
    return sdCylinder(p, beamRadius(t, p.y));
}

// Dome head while dropping: union cylinder + sphere at yCut
float sDistDrop(vec3 p, float t, float yCut) {
    float rHead = beamRadius(t, yCut);
    float cyl = sdCylinder(p, beamRadius(t, p.y));
    float sph = sdSphere(p - vec3(0.0, yCut, 0.0), rHead);
    return min(cyl, sph);
}

vec2 raymarchAny(vec3 ro, vec3 rd, float maxDist, float t, float useDrop, float yCut) {
    float traveled = 0.0;
    float closeSteps = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * traveled;

        float d = (useDrop > 0.5) ? sDistDrop(p, t, yCut) : sDistBeam(p, t);

        if (d <= MIN_DIST || traveled >= maxDist || traveled >= MAX_DIST) break;

        traveled += max(d * 0.7, 0.001);

        if (d <= 0.01) closeSteps += 1.0;
    }
    return vec2(traveled, closeSteps);
}

/* ===================== Apocalypse grade ===================== */
vec3 applyApocalypseGrade(
    vec3 col,
    float depth,
    vec3 ro,
    vec3 rd,
    float maxDist,
    float range,
    float a,
    float power,
    float impactFlash,
    float impactFrame
){
    vec3 camLocal = CameraPosition - BlockPosition;
    float camD = length(camLocal.xz);

    float g = exp(-(camD*camD) / (range*range));
    g = pow(g, FALLOFF_POWER);
    g = sat(g) * a;

    float sky = step(0.9995, depth);

    float tC = clamp(-dot(ro, rd), 0.0, maxDist);
    vec3  cp = ro + rd * tC;
    float rayD = length(cp.xz);

    float coreR = max(range * 0.55, 10.0);
    float core = exp(-(rayD*rayD) / (coreR*coreR));
    core = pow(core, 0.85) * g;

    vec3 warmPunch = vec3(1.00, 0.55, 0.12);
    vec3 emberDeep = vec3(0.045, 0.010, 0.010);
    vec3 hotCore   = vec3(1.00, 0.98, 0.90);
    vec3 gold      = vec3(1.00, 0.78, 0.24);

    float pDark = mix(1.0, 1.75, sat((power - 1.0) / 2.0));
    float pWarm = mix(1.0, 1.90, sat((power - 1.0) / 2.0));

    float darkAmt = sat(g * (0.68 + 0.55 * sky) * pDark);
    float warmAmt = sat(g * (0.42 + 0.75 * sky) * pWarm);

    float flash = sat(impactFlash) * g;
    float hit   = sat(impactFrame) * g;

    col = mix(col, col * 0.14 + emberDeep * 0.86, darkAmt);
    col += warmPunch * warmAmt;

    col = mix(col, vec3(col.r, col.g * 0.78, col.b * 0.20), sat(g * (0.95 + 0.35 * (power - 1.0))));

    if (sky > 0.5) {
        vec2 uv = texCoord;
        vec2 p = vec2(uv.x * 3.3, uv.y * 2.35);
        p += vec2(iTime * 0.03, -iTime * 0.018);

        float c1 = fbm(p);
        float c2 = fbm(p * 1.9 + 7.13);
        float clouds = sat(mix(c1, c2, 0.45));
        clouds = smoothstep(0.36, 0.86, clouds);

        col *= (1.0 - clouds * g * (0.78 + 0.18 * (power - 1.0)));

        float glow = core * (0.30 + 0.85 * clouds) * (1.0 + 0.35 * (power - 1.0));
        col += (gold * 3.4 + warmPunch * 2.4 + hotCore * 1.5) * glow;

        float breach = pow(core, 0.70) * (0.25 + 0.85 * clouds);
        col = mix(col, mix(gold, hotCore, 0.58), breach * (0.72 + 0.20 * (power - 1.0)));
    }

    vec2 v = texCoord - 0.5;
    float vig = sat(1.0 - dot(v, v) * 1.75);
    col *= mix(1.0, 0.66, g * (1.0 - vig) * (1.0 + 0.30 * (power - 1.0)));

    // flash
    vec3 flashCol = mix(gold, hotCore, 0.55);
    col = mix(col, col + flashCol * (3.2 * flash), flash);

    // SNAPPY hitframe (short)
    if (hit > 0.001) {
        float l = dot(col, vec3(0.299, 0.587, 0.114));
        float bw = step(0.55, l);
        vec3 mono = mix(vec3(0.0), vec3(1.0), bw);

        float ang = atan(v.y, v.x);
        float streaks = pow(abs(sin(ang * 70.0)), 16.0);
        float edge = sat(length(v) * 1.6);
        float lines = streaks * pow(edge, 0.35);
        mono = mix(mono, vec3(1.0), sat(lines * 2.2));

        col = mix(col, mono, hit);
    }

    return col;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float a = sat(Intensity);

    if (a <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float depth = texture(DepthSampler, texCoord).r;

    vec3 ro = worldPos(vec3(texCoord, 0.0)) - BlockPosition;
    vec3 rp = worldPos(vec3(texCoord, depth)) - BlockPosition;

    vec3 rd = normalize(rp - ro);
    float maxDist = distance(ro, rp);

    float stageRange = max(Radius, (Mode < 0.5 ? 18.0 : 30.0)) * SKY_RANGE_MULT;

    vec3 col = base;

    // ===================== MODE 0: Radiation =====================
    if (Mode < 0.5) {
        float pctGrow = sat(Radius / RADIATION_MAX_R);
        float power = mix(1.0, 2.0, pctGrow);
        float aStage = sat(a * (0.60 + 0.70 * pctGrow));

        col = applyApocalypseGrade(col, depth, ro, rd, maxDist, stageRange, aStage, power, 0.0, 0.0);

        vec2 p = rp.xz;
        float R = max(Radius, 0.0);

        float angle = pctGrow * TAU;
        p = rot2(angle) * p;

        float sym = radiationSymbol(p, R);

        float pulse = 0.65 + 0.35 * (0.5 + 0.5 * sin(iTime * 5.2));
        float shimmer = 0.75 + 0.25 * sin(iTime * 12.5 + p.x * 0.18 + p.y * 0.16);

        vec3 hotCore = vec3(1.00, 0.98, 0.90);
        vec3 gold    = vec3(1.00, 0.78, 0.24);
        vec3 orange  = vec3(1.00, 0.52, 0.10);

        vec3 glowCol = (orange * 2.7 + gold * 2.1 + hotCore * 1.3) * pulse * shimmer;

        float rr = length(p);
        float edge = 1.0 - smoothstep(R - 0.45, R + 0.45, rr);
        edge *= smoothstep(R - 1.15, R - 0.15, rr);
        edge = sat(edge) * (0.70 + 0.30 * sin(iTime * 7.0 + pctGrow * 6.0));

        col += glowCol * sym * (a * 2.55);
        col += (gold * 4.2 + hotCore * 2.2) * edge * a;

        float hard = sat(sym * (0.62 + 0.30 * pulse) * a);
        col = mix(col, mix(gold, hotCore, 0.55), hard);

        // ===== Pre-drop in the last 0.10s (19.90 -> 20.00) =====
        // 0.10 / 20.0 = 0.005, so start at pctGrow >= 0.995
        float pre = sat((pctGrow - 0.995) / 0.005);
        if (pre > 0.001) {
            float yTop = 400.0;
            float yCut = mix(yTop, 0.0, pre);

            // Raymarch with dome head
            vec2 hit = raymarchAny(ro, rd, maxDist, iTime, 1.0, yCut);
            vec3 hp = ro + rd * hit.x;

            float hitMask = step(hit.x, maxDist) * smoothstep(0.0, 10.0, hit.y);

            // reveal from top down
            float reveal = smoothstep(yCut - 6.0, yCut + 1.0, hp.y);
            hitMask *= reveal;

            float axisDist = length(hp.xz);
            float core2 = exp(-axisDist * axisDist * 10.0);
            float halo2 = exp(-axisDist * axisDist * 1.9);

            float hard2 = sat(hitMask * 1.35);
            float soft2 = sat(a * (hitMask * 1.0 + exp(-length(rp.xz) * length(rp.xz) * 0.045) * 0.70));

            float rBeam = beamRadius(iTime, hp.y);
            float edgeBand = exp(-pow((axisDist - rBeam) / 0.32, 2.0));

            float ang2 = atan(hp.z, hp.x);
            float spokes = pow(abs(sin(ang2 * 8.0 + iTime * 2.0)), 18.0) * halo2;

            float bands = 0.92 + 0.08 * sin(hp.y * 0.22 - iTime * 10.0 + hash(floor(hp.y)));
            float flicker = 0.95 + 0.05 * sin(iTime * 18.0);

            vec3 beamAdd =
                (hotCore * core2 * 14.0 +
                 gold    * halo2 * 10.0 +
                 orange  * halo2 * 6.0) * bands * flicker;

            beamAdd += (gold * 14.0 + hotCore * 8.0) * edgeBand;
            beamAdd += (gold * 10.0 + orange * 6.0) * spokes;

            // dome emphasis at the moving head
            float head = exp(-pow((hp.y - yCut) / 3.6, 2.0));
            beamAdd *= (1.0 + 3.0 * head);

            col += beamAdd * soft2;

            vec3 solidTarget = mix(gold, hotCore, core2);
            col = mix(col, solidTarget, hard2);
        }

        fragColor = vec4(col, 1.0);
        return;
    }

    // ===================== MODE 1: Pillar =====================
    float t = iTime;

    // super snappy timings (no drop here; drop was done in the last 0.10s of radiation)
    float impactFlash = exp(-pow((t - 0.000) / 0.030, 2.0));
    float impactFrame = exp(-pow((t - 0.020) / 0.018, 2.0));

    float post = sat(t / 0.22);
    float power = mix(2.0, 3.0, post);

    col = applyApocalypseGrade(col, depth, ro, rd, maxDist, stageRange, a, power, impactFlash, impactFrame);

    // Beam volume (solid pillar)
    vec2 hit = raymarchAny(ro, rd, maxDist, t, 0.0, 0.0);
    vec3 hp = ro + rd * hit.x;

    float hitMask = step(hit.x, maxDist) * smoothstep(0.0, 10.0, hit.y);

    vec3 hotCore = vec3(1.00, 0.98, 0.90);
    vec3 gold    = vec3(1.00, 0.78, 0.24);
    vec3 orange  = vec3(1.00, 0.52, 0.10);

    float axisDist = length(hp.xz);

    float core = exp(-axisDist * axisDist * 10.0);
    float halo = exp(-axisDist * axisDist * 1.9);

    float hard = sat(hitMask * 1.35);
    float soft = sat(a * (hitMask * 1.0 + exp(-length(rp.xz) * length(rp.xz) * 0.045) * 0.70));

    float rBeam = beamRadius(t, hp.y);
    float edgeBand = exp(-pow((axisDist - rBeam) / 0.32, 2.0));

    float ang = atan(hp.z, hp.x);
    float spokes = pow(abs(sin(ang * 8.0 + t * 2.0)), 18.0) * halo;

    float bands = 0.92 + 0.08 * sin(hp.y * 0.22 - t * 10.0 + hash(floor(hp.y)));
    float flicker = 0.95 + 0.05 * sin(t * 18.0);

    vec3 beamAdd =
        (hotCore * core * 14.0 +
         gold    * halo * 10.0 +
         orange  * halo * 6.0) * bands * flicker;

    beamAdd += (gold * 14.0 + hotCore * 8.0) * edgeBand;
    beamAdd += (gold * 10.0 + orange * 6.0) * spokes;

    float r1 = ringWave(rp, t, 18.0, 1.2);
    float r2 = ringWave(rp, t, 12.0, 1.7) * 0.70;
    float rings = sat(r1 + r2);

    vec3 ringCol = gold * (rings * 3.2) + hotCore * (rings * 1.2);

    float impactArea = exp(-length(rp.xz) * length(rp.xz) * 0.03);
    vec3 impactCol = (hotCore * 2.0 + gold * 2.6 + orange * 1.6) * impactFlash * impactArea;

    col += beamAdd * soft;
    col += ringCol * (a * 0.85);
    col += impactCol * a;

    vec3 solidTarget = mix(gold, hotCore, core);
    col = mix(col, solidTarget, hard);

    fragColor = vec4(col, 1.0);
}
