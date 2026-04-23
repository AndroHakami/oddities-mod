#version 330 compatibility

in vec3 Position;
out vec2 vUv;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    vUv = Position.xy * 0.5 + 0.5;
}
