#version 330 compatibility

uniform sampler2D DiffuseSampler;

uniform float Time;
uniform vec2 OutSize;
uniform float ChargeIntensity; // 0..1

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

    float a = sat(ChargeIntensity);
    vec4 src = texture(DiffuseSampler, uv);
    vec3 col = src.rgb;

    // slight zoom-in + breathing
    float breathe = 0.85 + 0.15 * sin(Time * 4.0);
    float zoom = (0.018 + 0.020 * breathe) * a;
    vec2 zUv = p * (1.0 - zoom) + 0.5;

    vec3 zCol = texture(DiffuseSampler, zUv).rgb;
    col = mix(col, zCol, 0.55 * a);

    // vignette mask (stronger at edges)
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 pp = vec2(p.x * aspect, p.y);
    float r = length(pp);
    float vign = smoothstep(0.28, 0.98, r);
    vign = vign * vign;

    // cosmic nebula noise in edges
    float n = fbm(pp * 3.2 + vec2(Time * 0.13, -Time * 0.10));
    float n2 = fbm(pp * 6.0 + vec2(-Time * 0.20, Time * 0.18));
    float neb = sat(0.55*n + 0.45*n2);

    vec3 purp = vec3(0.78, 0.28, 1.00);
    vec3 blue = vec3(0.12, 0.18, 0.95);
    vec3 tint = mix(purp, blue, neb);

    // edge darken + colored glow
    col *= (1.0 - 0.32 * vign * a);
    col += tint * (0.55 * vign * a);

    // tiny star speckles (biased to edges)
    float h = hash(uv * OutSize * 0.35 + vec2(Time * 40.0, Time * 23.0));
    float stars = smoothstep(0.992, 1.0, h) * vign;
    col += (purp * 0.8 + blue * 0.6) * (1.25 * stars * a);

    // subtle electric streaks around edge
    float ang = atan(pp.y, pp.x);
    float lines = abs(sin(ang * 10.0 + Time * 6.5));
    lines = pow(lines, 6.0) * vign;
    col += (purp * 0.6 + vec3(1.0) * 0.2) * (0.32 * lines * a);

    fragColor = vec4(col, src.a);
}