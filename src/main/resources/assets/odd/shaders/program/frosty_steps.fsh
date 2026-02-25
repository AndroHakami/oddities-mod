#version 330 compatibility
uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Strength;
uniform vec2 OutSize;
in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x,0.0,1.0); }
float hash(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float a = sat(Strength);
    if (a <= 0.001) { fragColor = vec4(base, 1.0); return; }

    vec2 uv = texCoord - 0.5;
    float d = length(uv);

    // chill vignette + frost bloom
    float vig = sat(1.0 - d * 1.45);
    float frost = pow(1.0 - vig, 1.35);

    // tiny drifting snow grain (very soft)
    float n = hash(texCoord * OutSize * 0.85 + vec2(Time * 18.0, -Time * 11.0));
    float speck = smoothstep(0.84, 1.0, n) * 0.35;

    vec3 coldA = vec3(0.58, 0.82, 1.00);
    vec3 coldB = vec3(0.75, 0.95, 1.00);

    vec3 tint = mix(coldA, coldB, sat(0.5 + 0.5*sin(Time*0.6 + texCoord.y*6.0)));

    vec3 col = base;
    col = mix(col, col * 0.86 + tint * 0.14, a * 0.75);
    col += tint * (frost * 0.28 * a);
    col += vec3(1.0) * (speck * 0.10 * a);

    fragColor = vec4(col, 1.0);
}
