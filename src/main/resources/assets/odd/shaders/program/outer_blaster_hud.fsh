
#version 330 compatibility
#define PI 3.141592653589793
#define TAU 6.283185307179586

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float iTime;
uniform float Intensity;
uniform float Heat;
uniform float Overheated;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float ring(vec2 p, float r, float w) {
    float d = abs(length(p) - r);
    return 1.0 - smoothstep(w, w * 1.8, d);
}

void main() {
    vec2 uv = texCoord;
    vec3 base = texture(DiffuseSampler, uv).rgb;

    float alpha = sat(Intensity);
    if (alpha <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 p = uv - vec2(0.5, 0.5);
    p.x *= aspect;

    // lower half-arc hugging the crosshair area
    float R = 0.060;
    float W = 0.0048;
    float a = atan(p.y, p.x);
    float inHalf = step(0.0, -p.y); // only lower half around cursor

    float t = clamp((a + PI) / PI, 0.0, 1.0);

    float rr = ring(p, R, W) * inHalf;

    float segs = 18.0;
    float sLoc = fract(t * segs);
    float sGate = smoothstep(0.08, 0.20, sLoc) * (1.0 - smoothstep(0.80, 0.92, sLoc));

    float bg = rr * sGate * 0.14;
    float fg = rr * sGate * step(t, Heat);

    float head = (1.0 - smoothstep(0.0, 0.055, abs(t - Heat))) * rr * sGate;

    float flash = 0.70 + 0.30 * sin(iTime * 18.0);
    float over = sat(Overheated);

    vec3 cold = vec3(0.30, 1.10, 0.25);
    vec3 hot = vec3(0.75, 1.20, 0.45);
    vec3 maxed = vec3(1.10, 1.25, 0.60);

    vec3 add = vec3(0.0);
    add += cold * bg;
    add += mix(cold, hot, Heat) * fg * (1.45 + 0.25 * Heat);
    add += maxed * head * (1.15 + 0.55 * Heat);
    add += maxed * fg * over * 0.38 * flash;
    add += maxed * rr * over * 0.18 * flash;

    fragColor = vec4(base + add * alpha, 1.0);
}
