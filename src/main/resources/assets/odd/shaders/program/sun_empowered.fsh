#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform vec2 OutSize;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x,0.0,1.0); }

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1,0));
    float c = hash(i + vec2(0,1));
    float d = hash(i + vec2(1,1));
    vec2 u = f*f*(3.0-2.0*f);
    return mix(a,b,u.x) + (c-a)*u.y*(1.0-u.x) + (d-b)*u.x*u.y;
}

float fbm(vec2 p){
    float v = 0.0;
    float a = 0.55;
    for(int i=0;i<4;i++){
        v += a * noise(p);
        p *= 2.0;
        a *= 0.55;
    }
    return v;
}

void main() {
    vec2 uv = texCoord;
    vec2 p = uv - 0.5;
    vec4 src = texture(DiffuseSampler, uv);
    vec3 col = src.rgb;

    float a = sat(Intensity);
    float pulse = 0.5 + 0.5 * sin(Time * 5.5);

    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 pp = vec2(p.x * aspect, p.y);
    float r = length(pp);
    float vign = smoothstep(0.24, 0.96, r);
    float ring = smoothstep(0.34, 0.58, r) * (1.0 - smoothstep(0.58, 0.84, r));

    float n = fbm(pp * 4.0 + vec2(Time * 0.18, -Time * 0.11));
    float n2 = fbm(pp * 8.0 + vec2(-Time * 0.24, Time * 0.16));
    float shimmer = sat(0.58 * n + 0.42 * n2);

    vec3 gold = vec3(1.00, 0.84, 0.30);
    vec3 orange = vec3(1.00, 0.52, 0.15);
    vec3 white = vec3(1.0);
    vec3 tint = mix(gold, orange, shimmer);

    col *= (1.0 - 0.18 * vign * a);
    col += tint * (0.35 * vign * a);
    col += mix(gold, white, pulse) * (0.24 * ring * a);

    float speck = smoothstep(0.992, 1.0, hash(uv * OutSize * 0.42 + vec2(Time * 29.0, Time * 17.0)));
    col += tint * (0.9 * speck * vign * a);

    fragColor = vec4(col, src.a);
}
