// src/main/resources/assets/odd/shaders/program/blockade_edge.fsh
#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Time;
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
    float a = sat(Intensity);

    if (a <= 0.001) {
        fragColor = src;
        return;
    }

    vec2 uv = texCoord * 2.0 - 1.0;
    float d = length(uv);

    // Thinner edge: only near the very edges
    float edge = smoothstep(0.85, 1.25, d);
    edge *= edge; // soften further (more subtle falloff)

    // Calmer pulse + lighter shimmer
    float pulse = 0.92 + 0.08 * sin(Time * 2.0);
    float n = hash(texCoord * OutSize + vec2(Time * 17.0, Time * 11.0));
    float shimmer = (n - 0.5) * 0.05;

    // Slight lime (yellow-green)
    vec3 lime = vec3(0.55, 1.00, 0.18);

    float k = sat(edge * (0.90 + shimmer) * pulse) * a;

    vec3 outCol = src.rgb;

    // Reduced glow strength (was 0.65)
    outCol += lime * (0.48 * k);

    // Reduced under-darken (was 0.08)
    outCol *= (1.0 - 0.03 * k);

    fragColor = vec4(outCol, src.a);
}
