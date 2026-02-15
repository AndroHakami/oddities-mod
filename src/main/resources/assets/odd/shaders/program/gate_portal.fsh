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

uniform float GlowStrength;
uniform vec3  GlowColor;
uniform float GlowHalfWidth;
uniform float GlowHalfHeight;

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
float hash12(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }
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

float glowRect(vec2 uv, vec2 halfSize){
    // uv is in blocks (u0,v0); halfSize is in blocks
    float mx = abs(uv.x) / max(halfSize.x, 1e-4);
    float my = abs(uv.y) / max(halfSize.y, 1e-4);
    float m = max(mx, my);          // 1.0 at rectangle border
    float d = abs(m - 1.0);
    // smooth outline band; tweak sigma for thickness feel
    float sigma = 0.055;
    float g = exp(-(d*d)/(sigma*sigma));
    // kill it outside a little
    g *= (1.0 - smoothstep(1.12, 1.45, m));
    return g;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;

    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);

    float denom = dot(rd, GateNormal);
    if (abs(denom) < 1.0e-4) { fragColor = vec4(base, 1.0); return; }

    float tFront = dot(GateCenter - ro, GateNormal) / denom;
    if (tFront <= 0.0) { fragColor = vec4(base, 1.0); return; }

    // Depth occlusion
    float distDepth = distance(ro, rp);
    bool occluded = (depth < 0.9995 && distDepth + 0.02 < tFront);

    // hit on plane
    vec3 ipFront = ro + rd * tFront;
    vec3 dP0 = ipFront - GateCenter;
    float u0 = dot(dP0, GateRight);
    float v0 = dot(dP0, GateUp);

    float planeHalfW = GateHalfWidth;
    float planeHalfH = GateHalfHeight;

    // glow can extend beyond portal interior
    float glowMask = 0.0;
    if (!occluded && abs(u0) <= GlowHalfWidth && abs(v0) <= GlowHalfHeight) {
        glowMask = glowRect(vec2(u0, v0), vec2(GlowHalfWidth, GlowHalfHeight));
        // tiny shimmer
        glowMask *= (0.85 + 0.15 * sin(iTime * 3.1 + (u0 + v0) * 0.25));
    }

    bool inPlane = (!occluded && abs(u0) <= planeHalfW && abs(v0) <= planeHalfH);

    if (!inPlane && glowMask <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float edge = 0.0;
    if (inPlane) {
        vec2 p0 = vec2(u0 / planeHalfW, v0 / planeHalfH); // -1..1
        edge = 1.0 - smoothstep(0.985, 1.03, max(abs(p0.x), abs(p0.y)));
    }

    // ==========================
    // DEEP TUNNEL + SPIRAL + BEAMS
    // ==========================
    const float PORTAL_DEPTH_BLOCKS = 14.0;
    const int   STEPS = 44;

    float t = iTime;

    vec3 backCenter = GateCenter - GateNormal * PORTAL_DEPTH_BLOCKS;
    float tBack = dot(backCenter - ro, GateNormal) / denom;

    float swirlSpeed   = 2.0;
    float zoomBack     = 38.0;
    float twistDepth   = 9.0;
    float tiles        = 24.0;

    float coreRadius   = 0.22; // slightly bigger core
    float coreSoft     = 0.08;

    float spiralArms   = 6.0;
    float spiralTight  = 6.2;
    float spiralBright = 1.15;

    float beamCount    = 14.0;
    float beamSharp    = 10.0;
    float beamSpeed    = 3.2;
    float beamStrength = 1.10;

    float accum = 0.0;
    float beamAcc = 0.0;
    float fogAcc  = 0.0;
    float coreAcc = 0.0;

    if (inPlane) {
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
            float e = (spiral * 0.95 + n * 0.55 + beams * 1.05) * fall;

            float wNear = exp(-zC * 1.15);
            float wFar  = exp(-zC * 0.40);

            accum   += e * wNear;
            beamAcc += beams * fall * wNear;
            fogAcc  += e * wFar;
            coreAcc += core * wNear;
        }
    }

    float intensity = sat(accum * (2.35 / float(STEPS)));
    float haze      = sat(fogAcc * (0.75 / float(STEPS)));
    float beamI     = sat(beamAcc * (1.55 / float(STEPS)));
    float coreI     = sat(coreAcc * (1.35 / float(STEPS)));

    // Green palette
    vec3 deepGreen   = vec3(0.00, 0.06, 0.03);
    vec3 midGreen    = vec3(0.01, 0.18, 0.09);
    vec3 brightGreen = vec3(0.03, 0.42, 0.18);
    vec3 beamGreen   = vec3(0.08, 0.65, 0.28);

    float m = smoothstep(0.06, 0.92, intensity);
    vec3 portal = mix(deepGreen, midGreen, m);
    portal = mix(portal, brightGreen, sat(intensity * intensity * 0.95));
    portal = mix(portal, deepGreen, sat(haze * 0.65));
    portal += beamGreen * (beamI * 0.75);
    portal = mix(portal, vec3(0.0), sat(coreI * 0.92));
    portal += brightGreen * (intensity * 0.12);

    vec3 col = mix(base, portal, A * edge);

    // NEW: glow comes from portal shader now
    col += GlowColor * (GlowStrength * A * glowMask);

    fragColor = vec4(col, 1.0);
}
