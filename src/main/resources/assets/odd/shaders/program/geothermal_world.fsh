#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3  PlayerPos;
uniform float Radius;
uniform vec3  Color;

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

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;
    if (depth >= 0.9999) { fragColor = vec4(base, 1.0); return; }

    vec3 wp = worldPos(vec3(texCoord, depth));

    // distance from torso center
    float d = distance(wp, PlayerPos);

    // main body mask: affects pixels on the player model (tight radius)
    float body = 1.0 - smoothstep(Radius, Radius + 0.10, d);

    // edge/rim boost so it feels like "energy on the skin"
    float rimSigma = 0.08;
    float rim = exp(-((d - Radius) * (d - Radius)) / (rimSigma * rimSigma));

    // pulsation + subtle "heat crawl" across surface
    float pulse = 0.55 + 0.45 * sin(iTime * 6.0);
    float crawl = 0.55 + 0.45 * sin(iTime * 4.6 + wp.y * 6.0 + wp.x * 2.2 - wp.z * 1.9);

    float energy = (body * (0.55 + 0.65 * pulse) + rim * 0.35) * crawl;
    energy = sat(energy);

    // apply
    vec3 col = base + Color * (energy * 0.85 * A);

    fragColor = vec4(col, 1.0);
}
