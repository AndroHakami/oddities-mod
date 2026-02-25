#version 330 compatibility

uniform mat4 InvProjMat;
uniform mat4 InvViewMat;

uniform float iTime;
uniform float Progress;
uniform float CubeIntensity;

in vec2 vUv;
out vec4 fragColor;

#define PI 3.14159265359

float sat(float x){ return clamp(x, 0.0, 1.0); }

vec3 viewDirFromUv(vec2 uv){
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 clip = vec4(ndc, 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    view /= max(view.w, 1e-6);
    vec3 dirView  = normalize(view.xyz);
    vec3 dirWorld = normalize((InvViewMat * vec4(dirView, 0.0)).xyz);
    return dirWorld;
}

float hash21(vec2 p){
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
vec2 hash22(vec2 p){
    float n = hash21(p);
    return vec2(n, hash21(p + n + 19.19));
}

vec2 octEncode(vec3 n){
    n /= (abs(n.x) + abs(n.y) + abs(n.z) + 1e-6);
    vec2 uv = n.xy;
    if (n.z < 0.0) uv = (1.0 - abs(uv.yx)) * sign(uv);
    return uv * 0.5 + 0.5;
}

/* ---------- noise / fbm (aurora) ---------- */
float n2(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    vec2 u = f*f*(3.0-2.0*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}
float fbm(vec2 p){
    float v = 0.0;
    float a = 0.55;
    for(int i=0;i<5;i++){
        v += a * n2(p);
        p *= 2.02;
        a *= 0.52;
    }
    return v;
}

/* ---------- outline square “panels” ONLY ---------- */
float panelLines_only(vec2 uv, float scale, float density, float t){
    vec2 p  = uv * scale;
    vec2 id = floor(p);
    vec2 f  = fract(p);

    float rnd = hash21(id);
    float on  = step(1.0 - density, rnd);

    float edge = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));
    float line = 1.0 - smoothstep(0.012, 0.028, edge);

    float flick = 0.86 + 0.14 * sin(t * 4.2 + rnd * 40.0);
    return on * line * flick;
}

/* ---------- stars: NOT circles (tiny spark + cross glint) ---------- */
float starSpark(vec2 p, float r){
    // core
    float d = length(p);
    float core = 1.0 - smoothstep(r, r * 1.8, d);

    // cross glint (prevents “big circle” look)
    float gx = 1.0 - smoothstep(r * 0.20, r * 1.8, abs(p.x));
    float gy = 1.0 - smoothstep(r * 0.20, r * 1.8, abs(p.y));
    float cross = max(gx * 0.65, gy * 0.65);

    // tiny diagonal glint
    float g1 = 1.0 - smoothstep(r * 0.20, r * 1.9, abs(p.x + p.y) * 0.707);
    float g2 = 1.0 - smoothstep(r * 0.20, r * 1.9, abs(p.x - p.y) * 0.707);
    float diag = max(g1, g2) * 0.35;

    return sat(core * 0.75 + cross * 0.55 + diag * 0.35);
}

float starLayer(vec2 uv, float scale, float density, float radius, float t){
    vec2 g  = uv * scale;
    vec2 id = floor(g);
    vec2 f  = fract(g) - 0.5;

    float rnd = hash21(id);
    float on  = step(1.0 - density, rnd);

    vec2 ofs = hash22(id + 19.3) - 0.5;
    vec2 p = f - ofs * 0.38;

    float s = starSpark(p, radius);

    float tw = 0.70 + 0.30 * sin(t * 2.0 + rnd * 30.0);
    return on * s * tw;
}

/* ---------- aurora curtains (green, controlled) ---------- */
vec3 aurora(vec3 dir, vec2 uv, float t){
    float v = sat(dir.y * 0.5 + 0.5);

    // only high overhead
    float band = smoothstep(0.70, 0.86, v) * (1.0 - smoothstep(0.94, 0.995, v));

    vec2 p = vec2(uv.x * 8.0, v * 2.7);
    p += vec2(t * 0.010, -t * 0.014);

    float n = fbm(p);
    float body = pow(sat(n), 2.0);

    float strands = pow(sat(1.0 - abs(fract(uv.x * 22.0 + n * 1.3) - 0.5) * 2.0), 3.4);

    float a = band * body * (0.30 + 0.70 * strands);

    vec3 deepG  = vec3(0.01, 0.08, 0.03);
    vec3 bright = vec3(0.10, 0.90, 0.32);
    vec3 teal   = vec3(0.06, 0.55, 0.45);

    float glow = pow(a, 1.4);
    return (deepG * a + bright * glow * 0.55 + teal * glow * 0.20) * 0.32;
}

void main(){
    vec3 dir = viewDirFromUv(vUv);
    vec2 uv  = octEncode(dir);

    float prog = sat(Progress);
    float geoK = sat(CubeIntensity);

    float y = sat(dir.y * 0.5 + 0.5);
    float overhead = smoothstep(0.10, 0.55, dir.y);

    // SPACE BLACK base (kills the vanilla blue)
    vec3 skyBase = mix(vec3(0.002, 0.002, 0.004), vec3(0.0, 0.0, 0.001), smoothstep(0.15, 0.95, y));

    // Stronger stars (but tiny)
    float starFade = smoothstep(0.15, 0.85, y);
    float s =
        starLayer(uv, 280.0, 0.022, 0.10, iTime) +
        starLayer(uv, 560.0, 0.012, 0.07, iTime * 0.85) * 0.70 +
        starLayer(uv, 140.0, 0.012, 0.13, iTime * 1.15) * 0.55;

    vec3 starColA = vec3(0.18, 1.00, 0.34);
    vec3 starColB = vec3(0.03, 0.20, 0.08);
    vec3 stars = (starColA * s * 0.55 + starColB * pow(sat(s), 2.2) * 0.25) * starFade;

    // Aurora overhead
    vec3 aur = aurora(dir, uv, iTime) * overhead;

    // Outline squares only
    float lines = 0.0;
    lines += panelLines_only(uv, 36.0, 0.12, iTime) * 1.00;
    lines += panelLines_only(uv, 72.0, 0.08, iTime * 1.10) * 0.65;

    float glimmer = pow(sat(lines), 2.2);
    vec3 neonGreen = vec3(0.18, 1.00, 0.34);
    vec3 paleGold  = vec3(0.95, 0.92, 0.70);

    vec3 lineCol = neonGreen * (0.22 + 0.78 * glimmer) + paleGold * (glimmer * 0.06);
    lineCol *= (0.20 * geoK) * overhead;

    vec3 col = skyBase;
    col += (stars + aur) * (0.95 * prog);
    col += lineCol;

    // IMPORTANT: fully replace sky very early => no blue horizon
    float aOut = smoothstep(0.01, 0.08, prog);

    fragColor = vec4(col, aOut);
}