#version 120
// Shinobi Mist: свечение ярких мест, цветокоррекция (холодные тени, тёплый свет), виньетка и зерно.
uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
uniform float frameTimeCounter;
varying vec2 texcoord;

float rnd(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    vec2 px = vec2(1.0 / viewWidth, 1.0 / viewHeight);
    vec3 c = texture2D(colortex0, texcoord).rgb;

    // дешёвое свечение: усреднённые яркие соседи
    vec3 glow = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;
        vec2 o = vec2(cos(a), sin(a)) * px * 6.0;
        vec3 s = texture2D(colortex0, texcoord + o).rgb;
        glow += max(s - vec3(0.72), vec3(0.0));
    }
    c += glow * 0.18;

    // цветокоррекция
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, 0.82);                      // чуть приглушённые цвета
    c = mix(c * vec3(0.90, 0.97, 1.08), c * vec3(1.10, 1.02, 0.90), smoothstep(0.25, 0.85, l)); // холод в тенях, тепло в свете
    c = (c - 0.5) * 1.12 + 0.5;                      // контраст

    // виньетка
    vec2 q = texcoord - 0.5;
    c *= 1.0 - dot(q, q) * 0.9;

    // зерно
    c += (rnd(texcoord * vec2(viewWidth, viewHeight) + frameTimeCounter) - 0.5) * 0.025;

    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
