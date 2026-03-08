#version 330 compatibility
#define PI 3.141592653589793
#define TAU 6.283185307179586

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;

uniform float iTime;
uniform float Intensity;

// accept either (use max)
uniform float Fuel;   // 0..1
uniform float Charge; // 0..1

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x, 0.0, 1.0); }

float ring(vec2 p, float r, float w){
    float d = abs(length(p) - r);
    return 1.0 - smoothstep(w, w * 1.7, d);
}

float boxMask(vec2 p, vec2 b){
    vec2 d = abs(p) - b;
    return 1.0 - sat(max(d.x, d.y) * 55.0);
}

// 0 at up, 0.25 right, 0.5 down, 0.75 left
float angle01(vec2 p){
    float a = atan(p.y, p.x);
    return fract(a / TAU + 0.25);
}

void main(){
    vec2 uv = texCoord;
    vec3 base = texture(DiffuseSampler, uv).rgb;

    float a = sat(Intensity);
    if (a <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float pct = sat(max(Fuel, Charge));

    // orange HUD colors
    vec3 hud = vec3(1.00, 0.55, 0.12);
    vec3 hot = vec3(1.00, 0.88, 0.65);

    float aspect = OutSize.x / max(OutSize.y, 1.0);

    // =========================================================
    // JARVIS-LIKE TECH OVERLAY (subtle, centric, no heavy grids)
    // =========================================================
    vec2 pc = uv - vec2(0.5);
    pc.x *= aspect;

    float boot = a; // assume your code ramps Intensity in/out

    // concentric HUD rings (very subtle)
    float halo1 = ring(pc, 0.42, 0.0018) * (0.10 * boot);
    float halo2 = ring(pc, 0.30 + 0.010 * sin(iTime * 1.1), 0.0016) * (0.08 * boot);
    float halo3 = ring(pc, 0.18, 0.0014) * (0.06 * boot);

    // “tech rays” ONLY near the big ring (not across the whole screen)
    float ang = atan(pc.y, pc.x);
    float rays = pow(abs(sin(ang * 6.0 + iTime * 0.35)), 24.0);
    rays *= ring(pc, 0.42, 0.010) * (0.06 * boot);

    // center reticle (small)
    float cross = 0.0;
    cross += (1.0 - smoothstep(0.0, 0.0014, abs(pc.x))) * (1.0 - smoothstep(0.0, 0.080, abs(pc.y)));
    cross += (1.0 - smoothstep(0.0, 0.0014, abs(pc.y))) * (1.0 - smoothstep(0.0, 0.080, abs(pc.x)));
    cross *= 0.10 * boot;

    float dotC = (1.0 - smoothstep(0.0, 0.006, length(pc))) * (0.10 * boot);

    // subtle corner brackets (HUD-on feeling)
    vec2 vv = uv - 0.5;
    float br = 0.0;
    br += boxMask(vv - vec2(-0.46, -0.26), vec2(0.040, 0.010));
    br += boxMask(vv - vec2(-0.46, -0.26), vec2(0.010, 0.040));
    br += boxMask(vv - vec2( 0.46, -0.26), vec2(0.040, 0.010));
    br += boxMask(vv - vec2( 0.46, -0.26), vec2(0.010, 0.040));
    br += boxMask(vv - vec2(-0.46,  0.26), vec2(0.040, 0.010));
    br += boxMask(vv - vec2(-0.46,  0.26), vec2(0.010, 0.040));
    br += boxMask(vv - vec2( 0.46,  0.26), vec2(0.040, 0.010));
    br += boxMask(vv - vec2( 0.46,  0.26), vec2(0.010, 0.040));
    br = sat(br) * (0.06 + 0.10 * boot);

    float overlay = 0.0;
    overlay += halo1 + halo2 + halo3;
    overlay += rays;
    overlay += cross + dotC;
    overlay += br;

    // =========================================================
    // ARC METER (correct spot + smaller + not stretched + flipped)
    // =========================================================
    // HUD-style placement (left / mid)
    vec2 center = vec2(0.55, 0.52);

    vec2 p = uv - center;
    p.x *= aspect;

    // flip horizontally (requested) around the arc’s own center
    p.x = -p.x;

    float ang01 = angle01(p);

    // left-side arc span (down-left -> up-left)
    float a0 = 0.60;
    float a1 = 0.90;

    float inArc = step(a0, ang01) * step(ang01, a1);
    float arcT = (ang01 - a0) / max(a1 - a0, 1e-6);
    arcT = sat(arcT);

    float R = 0.120;   // smaller
    float W = 0.0058;  // thinner

    float rr = ring(p, R, W) * inArc;

    // segmented look (clean)
    float segs = 18.0;
    float sLoc = fract(arcT * segs);
    float sGate = smoothstep(0.08, 0.18, sLoc) * (1.0 - smoothstep(0.82, 0.92, sLoc));

    float bg = rr * sGate * 0.15;

    float fill = step(arcT, pct);
    float fg = rr * sGate * fill;

    // highlight head
    float head = 1.0 - smoothstep(0.0, 0.05, abs(arcT - pct));
    head *= rr * sGate * (0.35 + 0.65 * pct);

    // local glow around arc only
    float glow = exp(-dot(p, p) * 65.0) * (0.10 + 0.12 * pct);

    vec3 add = vec3(0.0);
    add += hud * (overlay * 1.0);
    add += hud * (bg);
    add += hud * (fg * (1.55 + 0.55 * pct));
    add += hot * (head * 1.55);
    add += hot * (glow);

    // IMPORTANT: no screen-wide scanlines/grids => no “thick down lines”
    vec3 col = base + add * a;
    fragColor = vec4(col, 1.0);
}