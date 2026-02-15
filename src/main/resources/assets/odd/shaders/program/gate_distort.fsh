#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;

uniform vec3 CameraPosition;
uniform vec3 GatePosition;

uniform float iTime;
uniform float Intensity; // overall cap
uniform float Proximity; // 0..1 (distance-based)

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x, 0.0, 1.0); }

float hash12(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453123); }
float noise2(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash12(i);
    float b = hash12(i+vec2(1,0));
    float c = hash12(i+vec2(0,1));
    float d = hash12(i+vec2(1,1));
    vec2 u = f*f*(3.0-2.0*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}

void main(){
    vec3 base = texture(DiffuseSampler, texCoord).rgb;

    float a = sat(Intensity);
    float p = sat(Proximity);

    // nothing to do
    if(a <= 0.001 || p <= 0.001){
        fragColor = vec4(base, 1.0);
        return;
    }

    // Strength curve: gentle far, strong near
    float s = a * (0.15 + 0.85 * p);
    s *= s; // nicer ramp

    vec2 uv = texCoord;
    vec2 c = uv - 0.5;

    float r2 = dot(c, c);

    // Mild barrel distortion (increases near the gate)
    float k = 0.18 * s;             // distortion amount
    vec2  uvD = c * (1.0 + k * r2) + 0.5;

    // Subtle animated wobble (keeps it alive)
    float wob = (noise2(uv * 6.0 + vec2(iTime * 0.6, -iTime * 0.45)) - 0.5);
    uvD += c * (wob * 0.008 * s);

    // Chroma offsets along radial direction
    vec2 dir = normalize(c + vec2(1e-5));
    float chroma = 0.010 * s;       // chroma amount
    vec2 off = dir * chroma;

    vec3 col;
    col.r = texture(DiffuseSampler, uvD + off).r;
    col.g = texture(DiffuseSampler, uvD).g;
    col.b = texture(DiffuseSampler, uvD - off).b;

    // optional: slight contrast dip
    col = mix(base, col, sat(0.65 + 0.35 * p));

    fragColor = vec4(col, 1.0);
}
