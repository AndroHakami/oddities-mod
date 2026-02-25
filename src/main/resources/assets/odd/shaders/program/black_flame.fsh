#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3  Center;
uniform float Radius;   // 2.0
uniform float Height;   // 7.0

uniform float Age01;    // 0..1
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

float n2(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    vec2 u = f*f*(3.0-2.0*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}

float smooth01(float a, float b, float x){
    float t = sat((x - a) / (b - a));
    return t*t*(3.0-2.0*t);
}

/** robust cyl hit: if looking almost vertical, treat as hit if inside */
bool rayCylXZ(vec3 ro, vec3 rd, vec2 c, float R, out float t0, out float t1){
    vec2 oc = ro.xz - c;
    vec2 dv = rd.xz;

    float A = dot(dv,dv);
    float B = 2.0 * dot(oc,dv);
    float C = dot(oc,oc) - R*R;

    if (A < 1e-7) {
        if (C <= 0.0) { t0 = -1e20; t1 = 1e20; return true; }
        return false;
    }

    float D = B*B - 4.0*A*C;
    if (D < 0.0) return false;

    float s = sqrt(D);
    float inv = 0.5 / A;

    t0 = (-B - s) * inv;
    t1 = (-B + s) * inv;
    if (t0 > t1) { float tmp = t0; t0 = t1; t1 = tmp; }
    return true;
}

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

    float maxDist = min(distance(ro, rp), 140.0);

    // ===== timeline: fast erupt, steady burn, gentle fade =====
    float erupt = smooth01(0.00, 0.045, Age01);
    float diss  = smooth01(0.00, 0.30, 1.0 - Age01);
    float master = A * erupt * diss;

    float pop = 1.0 + 0.55 * (1.0 - smooth01(0.00, 0.12, Age01));
    master *= pop;

    // ===== geometry (down 1.5 blocks) =====
    float down = 1.5;
    float yBase = Center.y - down;

    float hNow = mix(1.1, Height + down, erupt);
    float y0 = yBase;
    float y1 = yBase + hNow;

    float rNow = mix(Radius * 0.55, Radius, erupt);
    float Rout = rNow + 0.85;

    float tc0, tc1, ty0, ty1;
    if (!rayCylXZ(ro, rd, Center.xz, Rout, tc0, tc1)) { fragColor = vec4(base,1.0); return; }
    if (!raySlabY(ro, rd, y0, y1, ty0, ty1))          { fragColor = vec4(base,1.0); return; }

    float tEnter = max(max(tc0, ty0), 0.0);
    float tExit  = min(min(tc1, ty1), maxDist);
    if (tExit <= tEnter) { fragColor = vec4(base,1.0); return; }

    // ===== march (cheaper) =====
    const int STEPS = 48;
    float segLen = (tExit - tEnter);
    float dt = segLen / float(STEPS);

    float j = hash12(texCoord * 917.0 + vec2(iTime * 0.19, -iTime * 0.13));
    float t = tEnter + j * dt;

    float sum = 0.0;
    float sumRim = 0.0;

    // fast flame feel (but cheap)
    float speed = 12.0;
    float bandF = 12.5;

    for (int i = 0; i < STEPS; i++) {
        vec3 wp = ro + rd * t;
        vec3 lp = wp - vec3(Center.x, yBase, Center.z);

        float h = lp.y / max(hNow, 0.001); // 0..1

        // softer top fade so no hard cut
        float vIn  = smooth01(0.00, 0.06, h);
        float vOut = smooth01(0.00, 0.92, 1.0 - h);
        float v = vIn * vOut;

        if (v > 0.001) {
            float ang = atan(lp.z, lp.x);

            // cheap flow noise (NO fbm)
            float flow = n2(vec2(ang * 2.4, h * 4.2) + vec2(iTime * 0.20, -iTime * 0.55));

            // slight radius warp so it isn't a perfect tube
            float rWarp = rNow * (1.0
                + 0.10 * sin(iTime * 3.4 + h * 6.2 + ang * 2.0)
                + 0.07 * (flow - 0.5));

            float rad = length(lp.xz);
            float r01 = rad / max(rWarp, 0.001);

            // core + rim masks
            float core = exp(-r01*r01 * 2.4);
            float rim  = exp(-pow((r01 - 0.98) / 0.22, 2.0));

            // tongues (fast rising)
            float tongues = 0.5 + 0.5 * sin((h * bandF - iTime * speed) + ang * 3.2 + (flow - 0.5) * 5.0);
            tongues = pow(sat(tongues), 2.05);

            // brighter near base
            float baseBoost = 0.65 + 0.65 * exp(-h * 3.0);

            // keep it hollow-ish / not filling whole cylinder
            float edgeSoft = smooth01(1.30, 0.78, r01);

            float dens = (core * 0.60 + rim * 1.05) * tongues * v * edgeSoft * baseBoost;

            sum += dens * dt;
            sumRim += (rim * tongues * v * baseBoost) * dt;

            if (sum > 14.0) break;
        }

        t += dt;
    }

    float m    = sat(1.0 - exp(-sum    * 1.95)) * master;
    float rimM = sat(1.0 - exp(-sumRim * 3.10)) * master;

    vec3 col = base;

    // ---- BLACK FLAME BODY ----
    // Strong darkening (looks black even in daylight)
    float darkAmt = sat(m * 0.95);
    col = mix(col, col * 0.045, darkAmt);

    // ---- DARK OUTLINE (only slightly purple-tinted) ----
    // Keep it subtle: “black outline with faint purple tint”
    vec3 tintDeep = vec3(0.03, 0.00, 0.05);   // VERY dark purple
    vec3 tintEdge = vec3(0.08, 0.02, 0.12);   // still dark, not neon

    float glow = sat(rimM * 0.55 + m * 0.10);

    // small day visibility assist (not huge)
    float luma = dot(base, vec3(0.299, 0.587, 0.114));
    float dayBoost = 1.0 + 0.35 * smooth01(0.25, 0.90, luma);

    float shimmer = 0.86 + 0.14 * sin(iTime * 6.5 + glow * 3.0);

    // OUTLINE is mostly “ink”, with faint purple edge
    col += tintDeep * (glow * 0.80) * shimmer * dayBoost;
    col += tintEdge * (glow * 0.90) * (0.75 + 0.25 * sin(iTime * 10.0)) * dayBoost;

    fragColor = vec4(col, 1.0);
}
