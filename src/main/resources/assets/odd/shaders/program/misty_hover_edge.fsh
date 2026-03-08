#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Time;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float hash12(vec2 p){
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    vec2 u = f*f*(3.0-2.0*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}

float fbm(vec2 p){
    float v = 0.0;
    float a = 0.55;
    for(int i=0;i<4;i++){
        v += a * noise(p);
        p *= 2.02;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = src; return; }

    // aspect-correct radial edge
    vec2 p = texCoord * 2.0 - 1.0;
    p.x *= (OutSize.x / max(1.0, OutSize.y));

    float d = length(p);

    // edge mask: only edges, soft
    float edge = smoothstep(0.70, 1.18, d);
    edge = edge * edge;

    float t = Time;

    // smooth wave field that flows around edges
    vec2 q = p * 1.35;
    float n1 = fbm(q * 3.2 + vec2( t * 0.10, -t * 0.07));
    float n2 = fbm(q * 6.0 + vec2(-t * 0.05,  t * 0.09));
    float waves = 0.55 * n1 + 0.45 * n2;

    float band = sin((q.x + q.y) * 6.5 + t * 1.10) * 0.5 + 0.5;
    float foam = smoothstep(0.72, 0.95, band) * 0.35;

    float k = edge * A;
    float amp = (0.35 + 0.65 * waves);
    float alpha = k * (0.35 + 0.65 * amp);

    vec3 deep = vec3(0.02, 0.06, 0.16);
    vec3 mid  = vec3(0.03, 0.10, 0.28);
    vec3 tint = mix(deep, mid, sat(waves + foam));

    vec3 col = src.rgb;
    col += tint * (0.85 * alpha);
    col *= (1.0 - 0.08 * alpha);

    fragColor = vec4(col, src.a);
}