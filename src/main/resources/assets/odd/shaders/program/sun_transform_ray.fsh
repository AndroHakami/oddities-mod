#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Start;
uniform vec3 End;
uniform float Radius;
uniform float Age01;
uniform float iTime;
uniform float Intensity;
uniform float Reverse;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x,0.0,1.0); }

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

float distToSeg(vec3 p, vec3 a, vec3 b, out float s01) {
    vec3 ab = b - a;
    float len2 = max(dot(ab, ab), 1e-6);
    float t = dot(p - a, ab) / len2;
    t = clamp(t, 0.0, 1.0);
    s01 = t;
    vec3 c = a + ab * t;
    return length(p - c);
}

float hash12(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;
    depth = min(depth, 0.9995);

    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);
    float maxDist = min(distance(ro, rp), 120.0);

    float age = sat(Age01);
    float pop = Reverse > 0.5 ? (1.0 - age) : age;
    float master = smoothstep(0.0, 0.10, pop) * smoothstep(1.0, 0.35, pop) * A;

    vec3 gold = vec3(1.00, 0.86, 0.34);
    vec3 orange = vec3(1.00, 0.56, 0.14);
    vec3 white = vec3(1.0);

    const int STEPS = 44;
    float dt = maxDist / float(STEPS);
    float t = hash12(texCoord * 751.0 + vec2(iTime * 0.14, -iTime * 0.09)) * dt;

    vec3 accum = vec3(0.0);
    float accD = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;
        float s01;
        float dSeg = distToSeg(p, Start, End, s01);
        float thickness = mix(Radius * 0.28, Radius * 1.35, 1.0 - age);
        float halo = smoothstep(thickness * 2.4, 0.0, dSeg);
        float core = smoothstep(thickness, 0.0, dSeg);
        float pulse = 0.75 + 0.25 * sin(iTime * 18.0 + s01 * 20.0);
        vec3 tint = mix(gold, orange, s01 + 0.08 * sin(iTime * 3.0));
        vec3 col = tint * halo * (1.25 + 0.4 * pulse) + white * core * 0.65;
        float dens = (halo * 0.65 + core * 1.25) * pulse;
        accum += col * dens * dt;
        accD += dens * dt;
        if (accD > 1.3) break;
        t += dt;
    }

    float alpha = sat(1.0 - exp(-accD * 3.2)) * master;
    vec3 glow = accum / max(accD, 1e-5);
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.05);
    vec3 col = mix(base, screenCol, alpha);

    fragColor = vec4(col, 1.0);
}
