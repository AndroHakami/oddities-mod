#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3  Center;
uniform float Radius;
uniform float Height;

uniform float Age01;
uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

#define PI 3.141592653589793
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
float smooth01(float a, float b, float x){
    float t = sat((x - a) / (b - a));
    return t*t*(3.0-2.0*t);
}

bool rayCylXZ(vec3 ro, vec3 rd, vec2 c, float R, out float t0, out float t1){
    vec2 oc = ro.xz - c;
    vec2 dv = rd.xz;
    float A = dot(dv,dv);
    float B = 2.0 * dot(oc,dv);
    float C = dot(oc,oc) - R*R;
    if (A < 1e-7) return false;
    float D = B*B - 4.0*A*C;
    if (D < 0.0) return false;
    float s = sqrt(D);
    float inv = 0.5 / A;
    t0 = (-B - s) * inv;
    t1 = (-B + s) * inv;
    if (t0 > t1) { float tmp=t0; t0=t1; t1=tmp; }
    return true;
}

bool raySlabY(vec3 ro, vec3 rd, float y0, float y1, out float t0, out float t1){
    if (abs(rd.y) < 1e-7){
        if (ro.y < y0 || ro.y > y1) return false;
        t0 = -1e20; t1 = 1e20;
        return true;
    }
    float a = (y0 - ro.y) / rd.y;
    float b = (y1 - ro.y) / rd.y;
    t0 = min(a,b);
    t1 = max(a,b);
    return true;
}

void main(){
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base,1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;
    depth = min(depth, 0.9995);

    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);

    float maxDist = min(distance(ro, rp), 170.0);

    // “drop” effect: beam extends downward fast at start
    float grow = smoothstep(0.0, 0.35, Age01);
    float y0 = Center.y;
    float y1 = Center.y + Height * grow;

    // then fade out later
    float fadeOut = 1.0 - smoothstep(0.60, 1.0, Age01);
    float strength = A * fadeOut;

    float rNow = max(Radius, 0.25);
    float Rout = rNow + 0.55;

    float tc0, tc1, ty0, ty1;
    if (!rayCylXZ(ro, rd, Center.xz, Rout, tc0, tc1)) { fragColor = vec4(base,1.0); return; }
    if (!raySlabY(ro, rd, y0, y1, ty0, ty1))          { fragColor = vec4(base,1.0); return; }

    float tEnter = max(max(tc0, ty0), 0.0);
    float tExit  = min(min(tc1, ty1), maxDist);
    if (tExit <= tEnter) { fragColor = vec4(base,1.0); return; }

    float segLen = tExit - tEnter;

    const int STEPS = 64;
    float dt = segLen / float(STEPS);

    float j = hash12(texCoord * 901.0 + vec2(iTime*0.13, -iTime*0.09));
    float t = tEnter + j * dt;

    float sum = 0.0;
    float sumEdge = 0.0;

    for(int i=0;i<STEPS;i++){
        vec3 wp = ro + rd * t;
        vec3 lp = wp - Center;

        float y = lp.y;
        float vIn  = smooth01(0.0, 2.0, y);
        float vOut = smooth01(0.0, 8.0, (y1 - wp.y));
        float v = vIn * vOut;

        if (v > 0.001){
            float rad = length(lp.xz);

            float core = exp(-pow(rad / (rNow*0.70 + 0.10), 2.0));
            float edge = exp(-pow((rad - rNow) / (0.22), 2.0));

            float ang = atan(lp.z, lp.x);
            float spokes = pow(abs(sin(ang*9.0 + iTime*2.8)), 18.0);

            float bands = 0.90 + 0.10 * sin(wp.y * 0.24 - iTime * 12.0 + floor(wp.y));
            float flick = 0.88 + 0.12 * sin(iTime * 20.0);

            float dens = (core*0.90 + edge*1.15) * (0.80 + 0.45*spokes) * bands * flick * v;

            sum += dens * dt;
            sumEdge += edge * (0.75 + 0.55*spokes) * v * dt;

            if (sum > 14.0) break;
        }

        t += dt;
    }

    float m = sat(1.0 - exp(-sum * 2.3)) * strength;
    float e = sat(1.0 - exp(-sumEdge * 2.9)) * strength;

    vec3 neonCore = vec3(0.78, 1.00, 0.86);
    vec3 neon     = vec3(0.18, 1.00, 0.34);
    vec3 acid     = vec3(0.06, 0.80, 0.20);

    vec3 col = base;

    // slight darken behind beam
    col = mix(col, col * 0.76, sat(m*0.35));

    vec3 add = neonCore * (m * 1.45) + neon * (e * 2.2) + acid * (m * 0.55);

    // screen-ish blend for punch
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - col) * exp(-add * 1.10);
    col = mix(col + add, screenCol, 0.55);

    fragColor = vec4(col, sat(m + e));
}