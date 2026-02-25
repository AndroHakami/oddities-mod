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
    float r = length(uv);

    // procedural cloudiness
    float n  = hash(texCoord * OutSize * 0.75 + vec2(Time * 11.0, Time * 7.0));
    float n2 = hash(texCoord * OutSize * 1.35 + vec2(Time * 17.0, Time * 13.0));

    float swirl = 0.5 + 0.5 * sin((uv.x * 3.2 - uv.y * 2.7) + Time * 1.6 + n * 6.0);
    float cloud = sat((0.92 - r) * 1.25 + (n - 0.5) * 0.45 + (swirl - 0.5) * 0.25);

    // auras
    float edge = smoothstep(0.20, 1.05, r);
    float snowAura = sat(edge * (0.75 + 0.25 * sin(Time * 2.2 + n2 * 6.5)));
    float fireAura = sat((1.0 - edge) * (0.70 + 0.30 * sin(Time * 3.4 + n * 8.0)));

    vec3 darkPurple = vec3(0.08, 0.00, 0.12);
    vec3 snowCol    = vec3(0.85, 0.93, 1.00);
    vec3 fireCol    = vec3(1.00, 0.45, 0.10);

    vec3 tint = darkPurple * (0.95 * cloud)
              + snowCol    * (0.55 * snowAura)
              + fireCol    * (0.45 * fireAura);

    // apply gently
    vec3 outCol = src.rgb;
    outCol += tint * (0.85 * a);
    outCol *= (1.0 - 0.10 * a * cloud);

    fragColor = vec4(outCol, src.a);
}
