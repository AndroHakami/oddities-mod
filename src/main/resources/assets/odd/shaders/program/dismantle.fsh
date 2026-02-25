#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3  Center;     // world center
uniform float HalfSize;   // 5.0 for a 10x10x10 cube

uniform float Age01;      // 0..1
uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x,0.0,1.0); }

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

float hash12(vec2 p){
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// slab ray-box intersection on AABB [-hs,hs] in local space
bool rayBox(vec3 roL, vec3 rdL, float hs, out float t0, out float t1) {
    vec3 inv = 1.0 / max(abs(rdL), vec3(1e-6)) * sign(rdL);
    vec3 tA = (-vec3(hs) - roL) * inv;
    vec3 tB = ( vec3(hs) - roL) * inv;
    vec3 tminV = min(tA, tB);
    vec3 tmaxV = max(tA, tB);

    t0 = max(tminV.x, max(tminV.y, tminV.z));
    t1 = min(tmaxV.x, min(tmaxV.y, tmaxV.z));
    return t1 > t0;
}

// SDF to box centered at origin with half extents hs
float sdBox(vec3 p, float hs){
    vec3 q = abs(p) - vec3(hs);
    float outside = length(max(q, 0.0));
    float inside  = min(max(q.x, max(q.y, q.z)), 0.0);
    return outside + inside;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;
    depth = min(depth, 0.9995); // allow sky rays too

    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);

    float maxDist = min(distance(ro, rp), 140.0);

    // timing: quick appear + smooth fade
    float appear = smoothstep(0.00, 0.08, Age01);
    float fade   = smoothstep(0.00, 0.18, 1.0 - Age01);
    float master = A * appear * fade;

    // local space (centered box)
    vec3 roL = ro - Center;
    vec3 rpL = rp - Center;

    float tEnter, tExit;
    if (!rayBox(roL, rd, HalfSize, tEnter, tExit)) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float t0 = max(tEnter, 0.0);
    float t1 = min(tExit,  maxDist);
    if (t1 <= t0) { fragColor = vec4(base, 1.0); return; }

    float segLen = (t1 - t0);

    // Camera inside boost (makes it super obvious when you’re inside)
    vec3 camL = CameraPosition - Center;
    float maxAbs = max(max(abs(camL.x), abs(camL.y)), abs(camL.z));
    float camInside = 1.0 - step(HalfSize, maxAbs); // 1 when inside

    float insideBoost = mix(1.0, 1.45, camInside);

    // Use midpoint of ray segment through the cube to build a stable boundary highlight
    float tMid = (t0 + t1) * 0.5;
    vec3 pMid = roL + rd * tMid;

    // distance to nearest face for midpoint (only meaningful inside)
    float faceDist = min(min(HalfSize - abs(pMid.x), HalfSize - abs(pMid.y)), HalfSize - abs(pMid.z));
    faceDist = max(faceDist, 0.0);

    // cube shell glow (strong near boundary)
    float border = exp(-faceDist * 4.2);

    // general volumetric “presence” so it looks full
    float fill = sat(1.0 - exp(-segLen * 0.22));
    fill *= (0.55 + 0.45 * border);

    // if the surface pixel is inside the cube, add a little more
    float dSurf = sdBox(rpL, HalfSize);
    float onOrInside = 1.0 - step(0.0, dSurf); // 1 if rp inside
    float surfBoost = 0.55 + 0.45 * onOrInside;

    // cheap “block edge” highlight using depth derivatives (no extra texture taps)
    float dd = length(vec2(dFdx(depth), dFdy(depth)));
    float geomEdge = sat(dd * 110.0) * onOrInside;

    // subtle dither (reduces banding, makes it feel “alive”)
    float n = hash12(gl_FragCoord.xy + vec2(iTime * 90.0, -iTime * 70.0));
    float dither = (n - 0.5) * 0.06;

    // RED palette (not orange)
    vec3 redHot  = vec3(1.00, 0.10, 0.05);
    vec3 redDeep = vec3(0.55, 0.03, 0.02);
    vec3 redFill = vec3(0.18, 0.01, 0.01);

    vec3 col = base;

    float borderAmt = sat(border * (1.15 + dither)) * insideBoost;
    float fillAmt   = sat(fill   * (0.55 + dither * 0.6)) * insideBoost * surfBoost;

    // keep it readable in daylight (don’t nuke the scene)
    float kBorder = 1.35;
    float kFill   = 0.42;
    float kEdge   = 0.85;

    col += mix(redDeep, redHot, 0.65 + dither) * (borderAmt * kBorder * master);
    col += redFill * (fillAmt * kFill * master);

    // emphasize block silhouettes slightly inside the cube
    col += redHot * (geomEdge * kEdge * master);

    fragColor = vec4(col, 1.0);
}
