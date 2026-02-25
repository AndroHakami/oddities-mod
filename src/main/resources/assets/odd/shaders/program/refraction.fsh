#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Strength;
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

    if (a <= 0.001) {
        fragColor = src;
        return;
    }

    // Centered coords for edge weighting
    vec2 uv = texCoord * 2.0 - 1.0;
    float d = length(uv);

    // Iridescent “film” shimmer (stronger near edges)
    float edge = smoothstep(0.25, 1.20, d);
    edge = sat(edge);

    float n = hash(texCoord * OutSize + vec2(Time * 37.0, Time * 19.0)) - 0.5;
    float wob = sin(Time * 2.2 + d * 6.0) * 0.5 + 0.5;

    // Chromatic offset (tiny) for prismatic look
    float off = (0.0015 + 0.0035 * edge) * a;
    vec2 dir = normalize(uv + vec2(0.0001, 0.0002));

    vec3 cR = texture(DiffuseSampler, texCoord + dir * off).rgb;
    vec3 cG = texture(DiffuseSampler, texCoord).rgb;
    vec3 cB = texture(DiffuseSampler, texCoord - dir * off).rgb;

    // Rainbow tint based on wobble + noise
    vec3 rainbow = vec3(
        0.55 + 0.45 * sin(Time * 1.8 + wob * 6.0),
        0.55 + 0.45 * sin(Time * 1.8 + wob * 6.0 + 2.1),
        0.55 + 0.45 * sin(Time * 1.8 + wob * 6.0 + 4.2)
    );

    float film = sat((0.25 + 0.75 * edge) * (0.65 + 0.35 * wob) + n * 0.10) * a;

    vec3 base = vec3(cR.r, cG.g, cB.b);
    vec3 outCol = mix(src.rgb, base, 0.35 * a);
    outCol += rainbow * (0.35 * film);

    fragColor = vec4(outCol, src.a);
}
