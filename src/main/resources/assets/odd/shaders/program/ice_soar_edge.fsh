#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Time;
uniform vec2 OutSize;

in vec2 texCoord; // unused (kept for compatibility)
out vec4 fragColor;

float sat(float x){ return clamp(x, 0.0, 1.0); }

float hash(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

// thin repeating line pattern without sine wobble
float lineBands(float x, float width){
    float f = fract(x);
    float d = min(f, 1.0 - f);                 // distance to nearest band center
    return 1.0 - smoothstep(0.0, width, d);    // thin bright line
}

void main() {
    vec2 os = max(OutSize, vec2(1.0));
    vec2 uv = gl_FragCoord.xy / os;
    uv = clamp(uv, 0.0, 1.0);

    vec4 src = texture(DiffuseSampler, uv);
    float a = sat(Intensity);
    if (a <= 0.001) { fragColor = src; return; }

    // --- Blockade-style edge mask (radial) ---
    vec2 ndc = uv * 2.0 - 1.0;
    float d = length(ndc);

    float edge = smoothstep(0.76, 1.18, d);
    edge *= edge; // soften falloff

    // --- “cold wind” streaks (diagonal flow, NO wobble) ---
    vec2 dir  = normalize(vec2(0.92, 0.38));
    vec2 perp = vec2(-dir.y, dir.x);

    vec2 px = gl_FragCoord.xy;

    float u = dot(px, dir);
    float v = dot(px, perp);

    float t = Time;

    // gust modulation along the flow direction
    float gust = 0.65 + 0.35 * sin(t * 1.1 + u * 0.003);

    // 3 layers of straight-ish streaks
    float L1 = lineBands(v * 0.030 + u * 0.002 + t * 1.60, 0.060);
    float L2 = lineBands(v * 0.052 + u * 0.003 - t * 2.10, 0.045);
    float L3 = lineBands(v * 0.085 + u * 0.004 + t * 2.85, 0.032);

    // break up the lines into “puffs” so it feels like wind, not wallpaper
    float seg = hash(vec2(floor(u * 0.020), floor(v * 0.020) + floor(t * 2.0)));
    float puff = smoothstep(0.25, 1.0, seg);

    float wind = (0.52 * L1 + 0.34 * L2 + 0.22 * L3) * gust * puff;

    // occasional tiny snow sparkles (rare + subtle)
    float sp = hash(floor(px * 0.22) + vec2(floor(t * 5.0), floor(t * 3.0)));
    float spark = smoothstep(0.992, 1.0, sp);

    // glacier palette
    vec3 iceA = vec3(0.14, 0.70, 1.00);
    vec3 iceB = vec3(0.70, 0.97, 1.00);
    float grad = 0.5 + 0.5 * sin(t * 0.55 + uv.y * 5.0);
    vec3 ice = mix(iceA, iceB, grad);

    float k = sat(edge * (0.75 + wind)) * a;

    vec3 outCol = src.rgb;

    // icy glow + wind brightness
    outCol += ice * (0.44 * k);

    // frosty depth
    outCol *= (1.0 - 0.06 * k);

    // sparkles near edge only
    outCol += iceB * (0.65 * spark * edge * a);

    fragColor = vec4(outCol, src.a);
}