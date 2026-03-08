#version 330 compatibility

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;
uniform float Time;
uniform float Intensity;
uniform float Progress;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x, 0.0, 1.0); }

float hash12(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float n2(vec2 p){
    float n = 0.0;
    n += hash12(p);
    n += 0.5 * hash12(p * 2.07 + 13.1);
    n += 0.25 * hash12(p * 4.13 - 7.7);
    return n / 1.75;
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    float a = sat(Intensity);
    float p = sat(Progress);

    if (a <= 0.001) {
        fragColor = vec4(src.rgb, 1.0);
        return;
    }

    vec2 center = vec2(0.5, 0.5);
    float grain = n2(texCoord * OutSize * 0.004 + vec2(Time * 2.1, -Time * 1.7));

    // ✅ strong zoom + shake (back)
    float zoom = 1.0 + (0.020 + 0.060 * p) * a;

    float shakeAmt = (0.0007 + 0.0030 * p) * a;
    vec2 shake = vec2(
        sin(Time * 40.0 + grain * 7.0),
        cos(Time * 43.0 + grain * 9.0)
    ) * shakeAmt;

    vec2 uvWarp = center + (texCoord - center) / zoom + shake;

    // safe sample
    vec2 uvClamp = clamp(uvWarp, vec2(0.0), vec2(1.0));

    // soften warp near borders
    vec2 d0 = max(vec2(0.0) - uvWarp, vec2(0.0));
    vec2 d1 = max(uvWarp - vec2(1.0), vec2(0.0));
    float outDist = length(d0 + d1);
    float keepWarp = 1.0 - smoothstep(0.0, 0.03, outDist);

    vec3 warped = texture(DiffuseSampler, uvClamp).rgb;
    vec3 base = mix(src.rgb, warped, keepWarp);

    // orange empower overlay
    vec2 uv = texCoord * 2.0 - 1.0;
    float r = length(uv);
    float ang = atan(uv.y, uv.x);

    float pulse = 0.55 + 0.45 * sin(Time * (3.2 + 2.0 * p) + r * 8.0 + ang * 0.7);
    float vig = smoothstep(0.10, 1.10, r);
    vig = pow(vig, 1.15);

    float amt = sat(a * (0.35 + 0.75 * p) * (0.35 + 0.65 * pulse));
    amt *= (0.25 + 0.75 * vig);

    vec3 orange = vec3(1.00, 0.42, 0.08);
    vec3 yellow = vec3(1.00, 0.78, 0.12);
    float heat = sat(0.5 + 0.5 * sin(Time * 1.7 + r * 4.5 + grain * 2.0));
    vec3 tint = mix(orange, yellow, heat);

    vec3 darkened = base * (1.0 - 0.14 * amt);
    vec3 glow = tint * (0.30 * amt);

    vec3 outCol = vec3(1.0) - (vec3(1.0) - darkened) * exp(-glow * 1.35);

    fragColor = vec4(outCol, 1.0);
}