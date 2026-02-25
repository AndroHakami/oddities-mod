#version 330 compatibility

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;
uniform float Time;
uniform float Intensity;

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
    if (a <= 0.001) { fragColor = src; return; }

    vec3 base = src.rgb;

    // center coords
    vec2 uv = texCoord * 2.0 - 1.0;
    float r = length(uv);
    float ang = atan(uv.y, uv.x);

    // edge mask (clean center)
    float vig = smoothstep(0.22, 1.10, r);
    vig = pow(vig, 1.25);

    // flame noise & pulsing
    float grain = n2(texCoord * OutSize * 0.003 + vec2(Time * 1.9, -Time * 1.3));
    float pulse = 0.55 + 0.45 * sin(Time * 3.4 + r * 7.0 + ang * 1.0);

    float edgeAmt = a * vig;
    edgeAmt *= (0.75 + 0.25 * grain);
    edgeAmt *= (0.75 + 0.25 * pulse);
    edgeAmt = sat(edgeAmt);

    // orange -> yellow heat shift
    vec3 orange = vec3(1.00, 0.30, 0.05);
    vec3 yellow = vec3(1.00, 0.75, 0.10);

    float g = sat(0.5 + 0.5 * sin(Time * 1.6 + r * 4.0 + grain * 2.0));
    vec3 tint = mix(orange, yellow, g);

    // slight darkening + hot glow veil
    vec3 darkened = base * (1.0 - 0.18 * edgeAmt);
    float glowAmt = edgeAmt * (0.75 + 0.55 * pulse) * (0.65 + 0.35 * grain);
    vec3 glow = tint * (0.60 * glowAmt);

    // screen-ish blend
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - darkened) * exp(-glow * 1.25);

    float mixAmt = sat(0.72 * a);
    vec3 outCol = mix(base, screenCol, mixAmt);

    fragColor = vec4(outCol, src.a);
}