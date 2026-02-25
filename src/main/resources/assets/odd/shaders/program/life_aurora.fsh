#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3  Center;     // base center (x,z) + base y
uniform float Radius;     // 5.0
uniform float Height;     // 7.0  (original “upwards” height from Center.y)

uniform float Age01;      // 0..1
uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

#define PI 3.141592653589793
#define TAU 6.283185307179586

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

float smooth01(float a, float b, float x){
    float t = sat((x - a) / (b - a));
    return t*t*(3.0-2.0*t);
}

// Ray vs infinite cylinder (axis Y) in XZ, returns [t0,t1] if hit
bool rayCylXZ(vec3 ro, vec3 rd, vec2 c, float R, out float t0, out float t1){
    vec2 oc = ro.xz - c;
    vec2 dv = rd.xz;

    float A = dot(dv,dv);
    float B = 2.0 * dot(oc,dv);
    float C = dot(oc,oc) - R*R;

    if (A < 1e-7) return false;

    float D = B*B - 4.0*A*C;
    if (D < 0.0) return false;

    float s = sqrt(D);
    float inv = 0.5 / A;

    t0 = (-B - s) * inv;
    t1 = (-B + s) * inv;
    if (t0 > t1) { float tmp = t0; t0 = t1; t1 = tmp; }
    return true;
}

// Ray vs Y slab [y0,y1]
bool raySlabY(vec3 ro, vec3 rd, float y0, float y1, out float t0, out float t1){
    if (abs(rd.y) < 1e-7){
        if (ro.y < y0 || ro.y > y1) return false;
        t0 = -1e20; t1 = 1e20;
        return true;
    }
    float a = (y0 - ro.y) / rd.y;
    float b = (y1 - ro.y) / rd.y;
    t0 = min(a,b);
    t1 = max(a,b);
    return true;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;
    depth = min(depth, 0.9995);

    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);

    float maxDist = min(distance(ro, rp), 110.0);

    float erupt = smooth01(0.00, 0.18, Age01);
    float diss  = smooth01(0.00, 0.22, 1.0 - Age01);
    float master = A * diss;

    // =========================
    // HEIGHT BEHAVIOR
    // =========================
    const float DOWN_EXT   = 1.5;
    const float TOPVAR_MAX = 6.0;

    // NEW: make the top fade much more gradual (blocks of feathering)
    const float TOP_FADE   = 2.35;
    const float TOP_FADE_POW = 0.65; // <1 => softer, less “cut off”

    float y0 = Center.y - DOWN_EXT;
    float baseHeight = Height + DOWN_EXT;
    float y1Max = y0 + baseHeight + TOPVAR_MAX;

    float rNow = mix(Radius * 0.70, Radius, erupt);

    float thick = mix(1.35, 0.85, erupt);
    float Rout  = rNow + thick;
    float Rin   = max(rNow - thick, 0.05);

    float tc0, tc1, ty0, ty1;
    if (!rayCylXZ(ro, rd, Center.xz, Rout, tc0, tc1)) { fragColor = vec4(base,1.0); return; }
    if (!raySlabY(ro, rd, y0, y1Max, ty0, ty1))       { fragColor = vec4(base,1.0); return; }

    float tEnter = max(max(tc0, ty0), 0.0);
    float tExit  = min(min(tc1, ty1), maxDist);
    if (tExit <= tEnter) { fragColor = vec4(base,1.0); return; }

    float segLen = (tExit - tEnter);

    const int STEPS = 96;
    float dt = segLen / float(STEPS);

    float j = hash12(texCoord * 917.0 + vec2(iTime * 0.17, -iTime * 0.11));
    float t = tEnter + j * dt;

    float sumCurtain = 0.0;
    float sumVeil    = 0.0;

    float sigma = mix(0.65, 0.45, erupt);
    float beamCount = 22.0;
    float curtainFreq = 9.6;

    float veilSigma = mix(1.60, 1.20, erupt);

    for (int i = 0; i < STEPS; i++) {
        vec3 wp = ro + rd * t;
        vec3 lp = wp - Center;

        float rad = length(lp.xz);
        float ang = atan(lp.z, lp.x);

        float tv1 = 0.5 + 0.5 * sin(ang * 2.6 + iTime * 0.45);
        float tv2 = 0.5 + 0.5 * sin(ang * 7.3 - iTime * 0.28);
        float topVar = (tv1*tv1*0.65 + tv2*tv2*0.35) * TOPVAR_MAX;

        float yLocal = wp.y - y0;
        float localTop = baseHeight + topVar;

        // bottom entry (same)
        float vIn  = smooth01(0.00, 0.55, yLocal);

        // NEW: much softer top feather (and eased even more with pow)
        float topDist = (localTop - yLocal);
        float vOut = smooth01(0.00, TOP_FADE, topDist);
        vOut = pow(vOut, TOP_FADE_POW);

        float vMask = vIn * vOut;

        if (vMask > 0.001) {
            float shellD = abs(rad - rNow);
            float hollow = smooth01(Rin, Rin + 0.10, rad);

            float bottomBright = exp(-yLocal * 0.14);

            // 1) curtains
            float shell = exp(-(shellD*shellD) / (sigma*sigma));

            float n = hash12(vec2(ang * 3.1, floor(yLocal * 0.6)) + vec2(iTime * 0.12, 1.7));

            float curtains = 0.5 + 0.5 * sin(iTime * 3.1 + yLocal * curtainFreq + n * 6.0);
            curtains = pow(sat(curtains), 1.80);

            float beams = abs(sin(ang * beamCount + iTime * 2.4));
            beams = pow(beams, 6.0);

            float shimmer = 0.82 + 0.18 * sin(iTime * 3.2 + n * 6.0 + yLocal * 0.55);

            float densCurtain = shell * hollow * (0.32 + 0.85 * beams) * curtains * shimmer * vMask;
            densCurtain *= bottomBright;
            densCurtain *= (0.95 + 0.65 * (1.0 - smooth01(0.0, 0.35, Age01)));

            sumCurtain += densCurtain * dt;

            // 2) veil
            float veil = exp(-(shellD*shellD) / (veilSigma*veilSigma));

            float s1 = 0.5 + 0.5 * sin(ang * 2.1 - iTime * 0.65 + yLocal * 0.22);
            float s2 = 0.5 + 0.5 * sin(ang * 5.4 + iTime * 0.40 + yLocal * 0.09);
            float flow = (0.55 + 0.45 * s1) * (0.55 + 0.45 * s2);

            float densVeil = veil * hollow * vMask * flow;
            densVeil *= bottomBright;
            densVeil *= 0.26;

            sumVeil += densVeil * dt;

            if ((sumCurtain + sumVeil) > 20.0) break;
        }

        t += dt;
    }

    float sumAll = sumCurtain * 0.90 + sumVeil * 0.85;
    float aur = 1.0 - exp(-sumAll * 0.85);
    aur = sat(aur);

    vec3 pMid = ro + rd * (tEnter + 0.55 * segLen);
    vec2 d = pMid.xz - Center.xz;
    float dist = length(d);

    float inside = sat(1.0 - dist / max(rNow, 0.001));
    inside = inside * inside;

    float floorBias = exp(-abs(pMid.y - Center.y) / 0.85);
    float innerPulse = 0.72 + 0.28 * sin(iTime * 2.0 - dist * 1.15);
    float inner = inside * floorBias * innerPulse;

    float y01 = sat((pMid.y - y0) / (baseHeight + TOPVAR_MAX));
    float bottomGlow = 1.0 - smooth01(0.15, 0.95, y01);

    vec3 aurA = vec3(1.00, 0.60, 0.97);
    vec3 aurB = vec3(0.82, 0.55, 1.00);
    vec3 aurC = vec3(1.00, 0.90, 0.99);
    vec3 aurD = vec3(0.62, 0.40, 0.95);

    float cc = 0.5 + 0.5 * sin(iTime * 0.75 + atan(d.y, d.x) * 2.2);
    vec3 ringCol = mix(aurA, aurB, cc);
    ringCol = mix(ringCol, aurC, sat(aur * 0.65));
    ringCol = mix(ringCol, aurD, y01 * 0.55);
    ringCol += aurC * (bottomGlow * 0.30);

    vec3 innerCol = vec3(1.00, 0.74, 0.94);

    vec3 glow = vec3(0.0);
    glow += ringCol  * (aur   * 2.25 * master);
    glow += innerCol * (inner * 0.32 * master);

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.10);
    vec3 col = mix(addCol, screenCol, 0.55);

    float dn = hash12(gl_FragCoord.xy + vec2(iTime * 60.0, -iTime * 37.0));
    col += (dn - 0.5) * (1.0 / 255.0);

    fragColor = vec4(col, 1.0);
}
