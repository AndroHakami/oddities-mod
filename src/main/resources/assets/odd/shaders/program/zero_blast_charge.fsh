#version 330 compatibility
#define PI 3.141592653589793
#define TAU 6.283185307179586

uniform sampler2D DiffuseSampler;

uniform float iTime;
uniform float Intensity;
uniform float Charge; // 0..1
uniform float Zoom;   // ~1..1.04

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x, 0.0, 1.0); }
float hash(float x){ return fract(sin(x) * 43758.5453123); }

float boxMask(vec2 p, vec2 b){
    vec2 d = abs(p) - b;
    return 1.0 - sat(max(d.x, d.y) * 40.0);
}

float ring(vec2 p, float r, float w){
    float d = abs(length(p) - r);
    return 1.0 - smoothstep(w, w * 1.7, d);
}

void main(){
    vec2 uv = texCoord;
    vec2 c  = vec2(0.5);

    // pixelate overlay computations (minecraft-y)
    vec2 pix = vec2(320.0, 180.0);
    vec2 puv = floor(uv * pix) / pix;

    // zoom-in as we charge
    vec2 p = uv - c;
    vec2 uz = p / max(Zoom, 0.001) + c;

    vec3 base = texture(DiffuseSampler, uz).rgb;

    float a = sat(Intensity);
    if (a <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float pct = sat(Charge);

    // Warm grade while charging (subtle)
    base = mix(base, vec3(base.r * 1.05, base.g * 0.90, base.b * 0.75), a * (0.10 + 0.15 * pct));

    // ORANGE HUD color
    vec3 hud = vec3(1.00, 0.55, 0.12);
    vec3 hot = vec3(1.00, 0.88, 0.65);

    // scanlines + “digital” noise bars
    float scan = 0.6 + 0.4 * sin((puv.y + iTime * 0.06) * 900.0);
    scan = pow(scan, 2.2);

    // ✅ REMOVE the obvious “fast falling” bars
    float barAmt = 0.0;

    // blocky grid (DISABLED per request)
    float grid = 0.0;

    // radial ring (segmented)
    vec2 rp = puv - c;
    float r = length(rp);
    float ang = atan(rp.y, rp.x);
    float ang01 = fract((ang / TAU) + 0.25); // top = 0

    float R = 0.22;
    float W = 0.012;
    float rr = ring(rp, R, W);

    float segs = 48.0;
    float segLocal = fract(ang01 * segs);
    float segGap = smoothstep(0.0, 0.12, segLocal) * (1.0 - smoothstep(0.88, 1.0, segLocal));
    float fill = step(ang01, pct);
    float ringFill = rr * fill * segGap;

    // highlight the “charge edge” so it feels like a laser gauge
    float da = abs(ang01 - pct);
    da = min(da, 1.0 - da);
    float edge = (1.0 - smoothstep(0.0, 0.020, da)) * rr * segGap;
    edge *= (0.35 + 0.95 * pct);

    // little tick marks
    float ticks = step(0.985, abs(sin(ang * segs)));
    ticks *= ring(rp, R + 0.03, 0.006) * (0.45 + 0.55 * pct);

    // center crosshair / brackets (make it more “target”)
    float cross = (1.0 - smoothstep(0.0, 0.003, abs(rp.x))) * (1.0 - smoothstep(0.0, 0.18, abs(rp.y)));
    cross += (1.0 - smoothstep(0.0, 0.003, abs(rp.y))) * (1.0 - smoothstep(0.0, 0.18, abs(rp.x)));
    cross *= (0.06 + 0.10 * pct);

    // target rings around center
    float tInner = ring(rp, 0.055 + 0.006 * pct, 0.006);
    float tOuter = ring(rp, 0.115, 0.005);
    float tPulse = ring(rp, 0.155, 0.006) * (0.5 + 0.5 * sin(iTime * (1.1 + 1.2 * pct)));

    // a small rotating “lock blip” on the outer ring
    float blipA = fract(pct * 0.85 + iTime * 0.08);
    float dba = abs(ang01 - blipA);
    dba = min(dba, 1.0 - dba);
    float blip = (1.0 - smoothstep(0.0, 0.022, dba)) * ring(rp, 0.115, 0.010);
    blip *= (0.25 + 0.75 * pct);

    float target = 0.0;
    target += tInner * (0.55 + 0.85 * pct);
    target += tOuter * (0.35 + 0.55 * pct);
    target += tPulse * (0.05 + 0.10 * pct);
    target += blip  * (0.35 + 0.75 * pct);

    // centered brackets (replaces corner grids/brackets; keeps it “centric”)
    float br = 0.0;
    br += boxMask(rp - vec2(-0.13, -0.09), vec2(0.045, 0.014));
    br += boxMask(rp - vec2(-0.13, -0.09), vec2(0.014, 0.045));
    br += boxMask(rp - vec2( 0.13, -0.09), vec2(0.045, 0.014));
    br += boxMask(rp - vec2( 0.13, -0.09), vec2(0.014, 0.045));
    br += boxMask(rp - vec2(-0.13,  0.09), vec2(0.045, 0.014));
    br += boxMask(rp - vec2(-0.13,  0.09), vec2(0.014, 0.045));
    br += boxMask(rp - vec2( 0.13,  0.09), vec2(0.045, 0.014));
    br += boxMask(rp - vec2( 0.13,  0.09), vec2(0.014, 0.045));
    br = sat(br) * (0.08 + 0.22 * pct);

    float glow = exp(-dot(rp, rp) * 8.5);

    float intensity = a * (0.55 + 0.95 * pct);

    vec3 add = vec3(0.0);
    add += hud * (ringFill * (2.2 + 1.2 * pct));
    add += hot * (edge * 1.35);
    add += hot * (ticks * 1.1);
    add += hud * (grid * 1.0);         // stays 0.0
    add += hud * (scan * 0.05);
    add += hud * (barAmt * 1.0);       // now 0.0
    add += hot * (glow * (0.10 + 0.22 * pct));
    add += hud * (cross);
    add += hot * (br);
    add += hot * (target * (0.85 + 0.65 * pct));

    // vignette (minecraft-y)
    vec2 v = puv - 0.5;
    float vig = sat(1.0 - dot(v, v) * 1.7);
    add *= (0.55 + 0.45 * vig);

    vec3 col = base + add * intensity;
    fragColor = vec4(col, 1.0);
}