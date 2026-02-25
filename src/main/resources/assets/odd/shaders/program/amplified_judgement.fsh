#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3  Center;
uniform float Radius;
uniform float Height;

uniform float Mode;     // 0 charge, 1 beam
uniform float Age01;
uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

#define PI 3.141592653589793
#define TAU 6.283185307179586

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

    float maxDist = min(distance(ro, rp), 140.0);

    vec3 col = base;

    // holy colors
    vec3 hotCore = vec3(1.00, 0.99, 0.92);
    vec3 gold    = vec3(1.00, 0.82, 0.28);
    vec3 pale    = vec3(1.00, 0.95, 0.80);

    // ===========================
    // MODE 0: charge disk + haze
    // ===========================
    if (Mode < 0.5){
        vec2 p = rp.xz - Center.xz;
        float r = length(p);

        float disk = 1.0 - smoothstep(Radius * 1.35, Radius * 1.50, r);
        float ring = exp(-pow((r - Radius*1.05) / (Radius*0.20 + 0.12), 2.0));

        float pulse = 0.70 + 0.30 * sin(iTime * 5.2);
        float spin  = atan(p.y, p.x);
        float spokes = pow(abs(sin(spin * 7.0 + iTime * 2.2)), 10.0);

        float haze = disk * (0.25 + 0.75 * ring) * pulse;
        haze += spokes * ring * 0.35 * pulse;

        // very faint vertical “prebeam”
        float y = rp.y - Center.y;
        float up = smooth01(0.0, 10.0, y);
        float pre = ring * up * 0.22;

        vec3 add = (gold * 1.7 + pale * 1.1) * (haze + pre) * A;

        col += add;
        fragColor = vec4(col, 1.0);
        return;
    }

    // ===========================
    // MODE 1: beam cylinder
    // ===========================
    float y0 = Center.y;
    float y1 = Center.y + Height;

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
        float vOut = smooth01(0.0, 8.0, (Height - y));     // soft top
        float v = vIn * vOut;

        if (v > 0.001){
            float rad = length(lp.xz);

            float core = exp(-pow(rad / (rNow*0.70 + 0.10), 2.0));
            float edge = exp(-pow((rad - rNow) / (0.22), 2.0));

            float ang = atan(lp.z, lp.x);
            float spokes = pow(abs(sin(ang*8.0 + iTime*2.0)), 16.0);

            float bands = 0.90 + 0.10 * sin(wp.y * 0.22 - iTime * 10.0 + floor(wp.y));
            float flick = 0.92 + 0.08 * sin(iTime * 18.0);

            float dens = (core*0.85 + edge*1.10) * (0.85 + 0.35*spokes) * bands * flick * v;

            sum += dens * dt;
            sumEdge += edge * (0.75 + 0.45*spokes) * v * dt;

            if (sum > 14.0) break;
        }

        t += dt;
    }

    float m = sat(1.0 - exp(-sum * 2.2)) * A;
    float e = sat(1.0 - exp(-sumEdge * 2.8)) * A;

    // slight darken behind beam so it reads in daylight
    col = mix(col, col * 0.78, sat(m*0.35));

    vec3 add = hotCore * (m * 1.65) + gold * (e * 2.15) + pale * (m * 0.65);

    // tiny screen-grade (keeps it “holy”)
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - col) * exp(-add * 1.05);
    col = mix(col + add, screenCol, 0.55);

    float dn = hash12(gl_FragCoord.xy + vec2(iTime*60.0, -iTime*37.0));
    col += (dn - 0.5) * (1.0/255.0);

    fragColor = vec4(col, 1.0);
}
