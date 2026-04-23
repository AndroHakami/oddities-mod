#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float hash(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 34.123);
    return fract(p.x * p.y);
}

float lineField(vec2 uv, float seed, float scale, float drift) {
    uv *= scale;
    float y = uv.y + drift;
    float path = sin(y * 3.7 + seed * 2.4) * 0.20
               + sin(y * 8.1 + seed * 5.2 + Time * 0.9) * 0.08;
    float d = abs(uv.x - path);
    float core = smoothstep(0.04, 0.0, d);
    float glow = smoothstep(0.11, 0.0, d) * 0.55;
    return core + glow;
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    vec3 col = src.rgb;

    float a = sat(Intensity);
    if (a <= 0.001) {
        fragColor = src;
        return;
    }

    vec2 uv = texCoord * 2.0 - 1.0;
    float vignette = smoothstep(1.15, 0.15, length(uv));

    vec2 shaky = uv + vec2(
        sin(Time * 2.7 + uv.y * 4.0) * 0.02,
        cos(Time * 1.9 + uv.x * 5.0) * 0.015
    ) * a;

    float scribble = 0.0;
    scribble += lineField(shaky + vec2(-0.28, 0.00), 1.3, 1.2, Time * 0.12);
    scribble += lineField(shaky + vec2( 0.13, 0.10), 2.7, 1.5, Time * 0.10);
    scribble += lineField(shaky + vec2( 0.31,-0.18), 4.1, 1.8, Time * 0.08);
    scribble += lineField(shaky + vec2(-0.10, 0.26), 5.5, 2.0, Time * 0.14);

    float grain = hash(texCoord * OutSize + vec2(Time * 31.0, Time * 19.0)) - 0.5;
    float edge = pow(1.0 - vignette, 1.5);

    vec3 ink = vec3(0.06, 0.05, 0.05);
    vec3 chalk = vec3(0.82, 0.80, 0.78);

    col = mix(col, col * 0.78, 0.15 * a);
    col += chalk * (scribble * 0.10 * a) * vignette;
    col -= ink * (scribble * 0.20 * a) * edge;
    col += grain * (0.04 * a);

    fragColor = vec4(col, src.a);
}