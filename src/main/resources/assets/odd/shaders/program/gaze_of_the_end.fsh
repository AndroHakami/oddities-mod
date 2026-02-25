// src/main/resources/assets/odd/shaders/program/gaze_of_the_end.fsh
#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Time;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x, 0.0, 1.0); }

float hash(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float luma(vec3 c){ return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

// tiny “rune” cells: looks like shifting glyph blocks
float rune(vec2 uv, float t){
    vec2 g = floor(uv);
    vec2 f = fract(uv);
    float h = hash(g + floor(t));

    float a = step(0.85, h) * step(0.25, f.x) * step(f.x, 0.75) * step(0.15, f.y) * step(f.y, 0.22);
    float b = step(0.70, h) * step(0.15, f.x) * step(f.x, 0.22) * step(0.25, f.y) * step(f.y, 0.75);
    float c = step(0.78, h) * step(0.78, f.x) * step(f.x, 0.85) * step(0.25, f.y) * step(f.y, 0.75);
    float d = step(0.65, h) * step(0.30, f.x) * step(f.x, 0.70) * step(0.78, f.y) * step(f.y, 0.85);
    return max(max(a,b), max(c,d));
}

void main(){
    vec4 src = texture(DiffuseSampler, texCoord);
    float a = sat(Intensity);
    if (a <= 0.001) { fragColor = src; return; }

    vec2 uv = texCoord;
    vec2 p  = uv * 2.0 - 1.0;
    float d2 = dot(p, p);
    float d  = sqrt(d2);

    // ✅ mask out "cubic/rune" layer near center
    // 0 at center -> 1 toward edges
    float edgeMask = smoothstep(0.18, 0.72, d);

    vec3 col = src.rgb;
    float y = luma(col);

    // iron-ish desat + purple tint
    col = mix(col, vec3(y), 0.45 * a);
    vec3 purple = vec3(0.65, 0.20, 0.95);
    col = mix(col, col * (0.75 + 0.55 * purple), 0.60 * a);

    // suit vignette + border frame
    float vig = smoothstep(1.20, 0.20, d2);
    col *= mix(0.72, 1.05, vig);

    float frame = 0.0;
    frame += smoothstep(0.002, 0.0, uv.x);
    frame += smoothstep(0.002, 0.0, uv.y);
    frame += smoothstep(0.002, 0.0, 1.0-uv.x);
    frame += smoothstep(0.002, 0.0, 1.0-uv.y);
    col += frame * vec3(0.35, 0.10, 0.55) * (0.55 * a);

    // subtle scanlines
    float scan = 0.5 + 0.5 * sin((uv.y * OutSize.y) * 0.08 + Time * 2.0);
    col += (scan - 0.5) * vec3(0.08, 0.03, 0.10) * (0.35 * a);

    // grid lines (also faded near center)
    vec2 guv = uv * vec2(60.0, 34.0);
    vec2 gf = fract(guv);
    float grid = (step(gf.x, 0.02) + step(gf.y, 0.02)) * 0.5;
    col += grid * vec3(0.18, 0.05, 0.25) * (0.45 * a) * edgeMask;

    // runic layer (glyph blocks drifting) — ✅ heavily reduced at center
    float t = Time * 0.9;
    vec2 ruv = uv * vec2(38.0, 22.0) + vec2(t * 0.6, -t * 0.35);
    float r = rune(ruv, floor(Time * 1.2));

    // slightly less overall intensity + center fade
    col += r * vec3(0.22, 0.06, 0.32) * (0.65 * a) * edgeMask;

    // tiny grain
    float n = hash(uv * OutSize + vec2(Time * 30.0, Time * 17.0));
    col += (n - 0.5) * (0.04 * a);

    fragColor = vec4(col, src.a);
}