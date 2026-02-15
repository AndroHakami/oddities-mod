// FILE: src/main/resources/assets/odd/shaders/program/gate_darken.fsh
#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;

uniform vec3 CameraPosition;

uniform vec3 GateCenter;
uniform vec3 GateRight;
uniform vec3 GateUp;
uniform vec3 GateNormal;

uniform float GateHalfWidth;
uniform float GateHalfHeight;
uniform float PlaneScale;
uniform float GlowStrength;
uniform float GlowHalfWidth;
uniform float GlowHalfHeight;

// ✅ NEW: per-style glow color
uniform vec3 GlowColor;

uniform float iTime;
uniform float Intensity;

uniform float Radius;    // expanding wave (blocks)
uniform float Darkness;  // 0..1

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

// low-frequency noise (NOT grainy)
float hash12(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }
float noise2(vec2 p){
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
        v += a * noise2(p);
        p *= 2.02;
        a *= 0.52;
    }
    return v;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;

    // Don’t darken sky
    if (depth > 0.9995) {
        fragColor = vec4(base, 1.0);
        return;
    }

    // Reconstruct ray + depth distance
    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);
    float distDepth = distance(ro, rp);

    // Portal region detection (so portal pixels aren't darkened + add glow)
    float planeW = GateHalfWidth  * PlaneScale;
    float planeH = GateHalfHeight * PlaneScale;

    float glowW = GlowHalfWidth;
    float glowH = GlowHalfHeight;

    float doorMask = 0.0;
    float glowMask = 0.0;

    float denom = dot(rd, GateNormal);
    if (abs(denom) > 1.0e-4) {
        float tPlane = dot(GateCenter - ro, GateNormal) / denom;
        if (tPlane > 0.0 && (distDepth + 0.02) >= tPlane) { // not occluded
            vec3 ip = ro + rd * tPlane;
            vec3 dP = ip - GateCenter;

            float u = dot(dP, GateRight);
            float v = dot(dP, GateUp);

            // glow zone (slightly larger)
            if (abs(u) <= glowW && abs(v) <= glowH) {
                float px = u / glowW;
                float py = v / glowH;
                float m = max(abs(px), abs(py)); // 1.0 at glow rectangle border

                float d = abs(m - 1.0);
                glowMask = exp(- (d*d) / (0.06*0.06));  // outline thickness feel
                glowMask *= (1.0 - smoothstep(1.20, 1.45, m));
            }

            // strict doorway interior for immunity (portal pixels)
            if (abs(u) <= planeW && abs(v) <= planeH) {
                float px = u / planeW;
                float py = v / planeH;
                float m = max(abs(px), abs(py));
                doorMask = 1.0 - smoothstep(0.995, 1.005, m);
            }
        }
    }

    // World position at depth surface (for darkening)
    vec3 wp = rp;

    // distance from gate (slightly reduce vertical influence)
    vec3 d = wp - GateCenter;
    d.y *= 0.70;
    float dist = length(d);

    // expanding influence
    float feather = 4.0;   // edge softness in blocks
    float inside = 1.0 - smoothstep(Radius, Radius + feather, dist); // 1 inside, 0 outside

    if (inside <= 0.001 && glowMask <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    // moving wave-front ring (adds motion, NOT spiral)
    float ringW = 1.35;
    float ring = exp(-pow((dist - Radius) / ringW, 2.0));

    // animated shadow texture (low frequency)
    float n = fbm((wp.xz - GateCenter.xz) * 0.065 + vec2(iTime * 0.08, -iTime * 0.06));
    n = 0.78 + 0.22 * n;

    // subtle “pull” without angle spirals: direction-warped noise
    vec2 dir = normalize((GateCenter.xz - wp.xz) + vec2(1.0e-4));
    float n2 = fbm((wp.xz - GateCenter.xz) * 0.040 + dir * (iTime * 0.35));
    n2 = 0.80 + 0.20 * n2;

    float dark = Darkness * A * (inside * n * n2 + ring * 0.30);

    // portal pixels immune
    dark *= (1.0 - doorMask);
    dark = sat(dark);

    vec3 col = base * (1.0 - dark);

    // ✅ Style-driven emissive halo
    float glow = GlowStrength * A * glowMask;
    glow *= (0.45 + 0.55 * sat(Radius / 10.0));

    col += GlowColor * glow;

    fragColor = vec4(col, 1.0);
}
