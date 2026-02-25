#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Center;     // set to player chest
uniform float Radius;    // ~2.55
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

bool raySphere(vec3 ro, vec3 rd, vec3 c, float r, out float t0, out float t1){
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float c2 = dot(oc, oc) - r*r;
    float h = b*b - c2;
    if (h < 0.0) return false;
    h = sqrt(h);
    t0 = -b - h;
    t1 = -b + h;
    return true;
}

void main(){
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float A = sat(Intensity);
    if (A <= 0.001){ fragColor = vec4(base,1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;
    depth = min(depth, 0.9995);

    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);

    float maxDist = min(distance(ro, rp), 64.0);

    float t0, t1;
    if(!raySphere(ro, rd, Center, Radius, t0, t1)){ fragColor = vec4(base,1.0); return; }

    float tEnter = max(t0, 0.0);
    float tExit  = min(t1, maxDist);
    if(tExit <= tEnter){ fragColor = vec4(base,1.0); return; }

    float tMid = 0.5*(tEnter+tExit);
    vec3 p = ro + rd*tMid;

    float d = length(p - Center) / max(Radius, 0.001); // 0..1
    float rim = exp(-pow((d - 0.92)/0.10, 2.0));
    float fill = exp(-d*d*2.3);

    float shimmer = 0.88 + 0.12*sin(iTime*5.2 + p.y*0.7);

    vec3 coldA = vec3(0.60, 0.85, 1.00);
    vec3 coldB = vec3(0.30, 0.55, 0.95);

    vec3 glow = (coldA * (fill*0.18) + coldB * (rim*0.78)) * shimmer;
    glow *= A;

    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.35);
    fragColor = vec4(mix(base + glow, screenCol, 0.55), 1.0);
}
