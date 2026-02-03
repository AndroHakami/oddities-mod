#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Time;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

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

    vec3 col = src.rgb;
    float y = luma(col);

    float pulse = 0.70 + 0.30 * sin(Time * 2.7);

    // a bit more desat
    col = mix(col, vec3(y), 0.33 * a);

    // STRONGER red push
    vec3 rage = vec3(y * 1.35, y * 0.05, y * 0.04);
    col = mix(col, col + rage, (0.92 * a) * pulse);

    // stronger vignette
    vec2 uv = texCoord * 2.0 - 1.0;
    float d = dot(uv, uv);
    float vig = smoothstep(1.25, 0.20, d);
    col *= mix(0.70, 1.10, vig);

    // grain
    float n = hash(texCoord * OutSize + vec2(Time * 30.0, Time * 17.0));
    col += (n - 0.5) * (0.06 * a);

    fragColor = vec4(col, src.a);
}
