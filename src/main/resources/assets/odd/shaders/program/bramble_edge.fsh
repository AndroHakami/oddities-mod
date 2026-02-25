#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Strength;
uniform float Pulse;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);

    float a = sat(Strength);
    float p = sat(Pulse);

    if (a <= 0.001 && p <= 0.001) {
        fragColor = src;
        return;
    }

    // edge mask (stronger near edges)
    vec2 uv = texCoord * 2.0 - 1.0;
    float d = length(uv);

    float edge = smoothstep(0.72, 1.28, d);
    edge = edge * edge;

    // shimmer noise
    float n = hash(texCoord * OutSize + vec2(Time * 21.0, Time * 13.0));
    float shimmer = (n - 0.5) * 0.12;

    // “overwhelming” light energy
    float pulse = 0.85 + 0.15 * sin(Time * 2.2);
    float k = sat(edge * (0.95 + shimmer) * pulse);

    // intensity: base + hit pulse
    float I = sat(a * 0.85 + p * 1.25);

    // warm-white / radiant gold tint
    vec3 glow = vec3(1.0, 0.98, 0.75);

    vec3 outCol = src.rgb;
    outCol += glow * (0.85 * k * I);
    outCol *= (1.0 - 0.05 * k * I);

    fragColor = vec4(outCol, src.a);
}
