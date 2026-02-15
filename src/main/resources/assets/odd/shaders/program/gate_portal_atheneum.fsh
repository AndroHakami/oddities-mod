#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;

uniform vec3 CameraPosition;

uniform vec3 GateCenter;
uniform vec3 GateRight;
uniform vec3 GateUp;
uniform vec3 GateNormal;

uniform float GateHalfWidth;
uniform float GateHalfHeight;

uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

#define PI 3.141592653589793
#define TAU 6.283185307179586

float sat(float x) { return clamp(x, 0.0, 1.0); }

mat2 rot2(float a){
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

// low-frequency stable noise (no grain)
float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}
float valueNoise(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    vec2 u = f*f*(3.0-2.0*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}
float fbm(vec2 p){
    float v = 0.0;
    float a = 0.55;
    for(int i=0;i<4;i++){
        v += a * valueNoise(p);
        p *= 2.02;
        a *= 0.52;
    }
    return v;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;

    // Ray from camera through pixel
    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);

    // Intersect with portal plane
    float denom = dot(rd, GateNormal);
    if (abs(denom) < 1.0e-4) { fragColor = vec4(base, 1.0); return; }

    float tFront = dot(GateCenter - ro, GateNormal) / denom;
    if (tFront <= 0.0) { fragColor = vec4(base, 1.0); return; }

    // Depth occlusion (prevents seeing through walls/closed door)
    float distDepth = distance(ro, rp);
    if (depth < 0.9995 && distDepth + 0.02 < tFront) {
        fragColor = vec4(base, 1.0);
        return;
    }

    // ==========================================================
    // Portal plane size (bigger than doorway = "whole world" feel)
    // ==========================================================
    float planeScale = 1.5;
    float planeHalfW = GateHalfWidth  * planeScale;
    float planeHalfH = GateHalfHeight * planeScale;

    // bounds check on front hit
    vec3 ipFront = ro + rd * tFront;
    vec3 dP0 = ipFront - GateCenter;
    float u0 = dot(dP0, GateRight);
    float v0 = dot(dP0, GateUp);

    if (abs(u0) > planeHalfW || abs(v0) > planeHalfH) {
        fragColor = vec4(base, 1.0);
        return;
    }

    // soft edge fade at plane border
    vec2 p0 = vec2(u0 / planeHalfW, v0 / planeHalfH); // -1..1
    float edge = 1.0 - smoothstep(0.985, 1.03, max(abs(p0.x), abs(p0.y)));

    // ==========================================================
    // DEEP TUNNEL + SPIRAL + SUCTION BEAMS
    // ==========================================================
    const float PORTAL_DEPTH_BLOCKS = 14.0;
    const int   STEPS = 44;

    float t = iTime;

    vec3 backCenter = GateCenter - GateNormal * PORTAL_DEPTH_BLOCKS;
    float tBack = dot(backCenter - ro, GateNormal) / denom;

    // ========= STYLE KNOBS =========
    float swirlSpeed   = 2.25;  // dreamy a touch faster than roots
    float zoomBack     = 40.0;
    float twistDepth   = 8.5;
    float tiles        = 23.0;

    float coreRadius   = 0.20;
    float coreSoft     = 0.085;

    float spiralArms   = 6.0;
    float spiralTight  = 6.0;
    float spiralBright = 1.08;

    float beamCount    = 12.0;
    float beamSharp    = 9.0;
    float beamSpeed    = 2.8;
    float beamStrength = 0.95;
    // ===============================

    float accum = 0.0;
    float beamAcc = 0.0;
    float fogAcc  = 0.0;
    float coreAcc = 0.0;

    // ✅ NEW: track swirl energy so we can tint JUST the swirl yellow
    float swirlAcc = 0.0;

    for (int i = 0; i < STEPS; i++) {
        float z01 = (float(i) + 0.5) / float(STEPS);
        float zC = pow(z01, 1.70);

        float tt = mix(tFront, tBack, zC);
        if (tt <= 0.0) continue;

        vec3 ip = ro + rd * tt;
        vec3 dP = ip - GateCenter;

        float u = dot(dP, GateRight);
        float v = dot(dP, GateUp);

        if (abs(u) > planeHalfW || abs(v) > planeHalfH) continue;

        vec2 p = vec2(u / planeHalfW, v / planeHalfH);

        float zoom = exp2(zC * log2(max(zoomBack, 1.001)));
        vec2 q = p * zoom;

        float baseAng = t * (0.60 * swirlSpeed) + zC * twistDepth;

        float r0 = length(q) + 1.0e-4;
        float curvature = r0 * (0.22 + 0.10 * spiralTight);
        q = rot2(baseAng + curvature) * q;

        // "blocky" sampling: evaluate at tile centers
        vec2 g  = q * tiles;
        vec2 id = floor(g);
        vec2 qT = (id + 0.5) / tiles;

        float r = length(qT) + 1.0e-4;
        float ang = atan(qT.y, qT.x);

        float core = 1.0 - smoothstep(coreRadius, coreRadius + coreSoft, r);

        float spiralPhase = ang * spiralArms + log(r) * spiralTight - t * (2.4 * swirlSpeed) - zC * 3.5;
        float spiral = 0.5 + 0.5 * sin(spiralPhase);
        spiral = smoothstep(0.35, 0.95, spiral) * spiralBright;

        float spokes = abs(sin(ang * beamCount + t * 0.35 + zC * 2.0));
        spokes = pow(spokes, beamSharp);

        float pull = 0.5 + 0.5 * sin(r * 18.0 - t * beamSpeed - zC * 6.0);
        pull = smoothstep(0.15, 1.0, pull);

        float beams = spokes * pull;

        float beamRad = smoothstep(coreRadius + 0.03, 0.55, r) * (1.0 - smoothstep(0.95, 1.35, r));
        beams *= beamRad * beamStrength;

        float n = fbm(qT * 2.1 + vec2(t * 0.08, -t * 0.06));
        n = mix(n, spiral, 0.55);

        float fall = 1.0 / (1.0 + r * (1.25 + 1.20 * zC));

        float e = (spiral * 0.92 + n * 0.62 + beams * 0.95) * fall;

        float wNear = exp(-zC * 1.15);
        float wFar  = exp(-zC * 0.40);

        accum   += e * wNear;
        beamAcc += beams * fall * wNear;
        fogAcc  += e * wFar;
        coreAcc += core * wNear;

        // ✅ NEW: swirl-only accumulation (no beams/no noise dominance)
        swirlAcc += spiral * fall * wNear;
    }

    float intensity = sat(accum * (2.25 / float(STEPS)));
    float haze      = sat(fogAcc * (0.70 / float(STEPS)));
    float beamI     = sat(beamAcc * (1.35 / float(STEPS)));
    float coreI     = sat(coreAcc * (1.35 / float(STEPS)));

    // ✅ NEW: normalized swirl energy
    float swirlI    = sat(swirlAcc * (1.55 / float(STEPS)));

    // ==========================================================
    // ATHENEUM palette: dreamy midnight/azure + pastel yellow swirl
    // ==========================================================
    vec3 deepBlue   = vec3(0.02, 0.05, 0.12);
    vec3 midBlue    = vec3(0.10, 0.18, 0.40);
    vec3 brightBlue = vec3(0.28, 0.56, 0.95);

    // ✅ Pastel yellow for swirl
    vec3 pastelYellow = vec3(1.00, 0.96, 0.72);

    // Keep beams cool-blue
    vec3 beamBlue   = vec3(0.55, 0.85, 1.00);

    float m = smoothstep(0.06, 0.92, intensity);
    vec3 portal = mix(deepBlue, midBlue, m);
    portal = mix(portal, brightBlue, sat(intensity * intensity * 0.92));

    portal = mix(portal, deepBlue, sat(haze * 0.62));

    // ✅ Yellow tint ONLY where swirl is strong, and NOT in the core
    float swirlMask = swirlI;
    swirlMask *= smoothstep(0.10, 0.75, intensity);      // requires “some” energy
    swirlMask *= (1.0 - sat(coreI * 0.95));              // avoid center core
    swirlMask = sat(swirlMask);

    // add a gentle pastel contribution (keeps overall blue)
    portal = mix(portal, portal + pastelYellow * 0.35, swirlMask);

    // beams stay cool
    portal += beamBlue * (beamI * 0.65);

    // black core
    portal = mix(portal, vec3(0.0), sat(coreI * 0.90));

    // tiny bloom-ish lift
    portal += brightBlue * (intensity * 0.08);

    vec3 col = mix(base, portal, A * edge);
    fragColor = vec4(col, 1.0);
}
