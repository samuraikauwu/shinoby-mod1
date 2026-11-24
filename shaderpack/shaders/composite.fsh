#version 120
// Shinobi Mist: туманная дымка по глубине, холоднее вдали, плотнее в дождь.
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform float rainStrength;
varying vec2 texcoord;

void main() {
    vec3 c = texture2D(colortex0, texcoord).rgb;
    float d = texture2D(depthtex0, texcoord).r;
    vec4 p = gbufferProjectionInverse * vec4(texcoord * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    float dist = length(p.xyz / p.w);
    float density = 0.011 + 0.012 * rainStrength;
    float fog = 1.0 - exp(-dist * density);
    if (d >= 1.0) fog = 0.35; // небо чуть затянуто дымкой
    vec3 mist = mix(vec3(0.66, 0.63, 0.58), vec3(0.50, 0.55, 0.60), clamp(dist / 160.0, 0.0, 1.0));
    c = mix(c, mist, clamp(fog, 0.0, 0.85));
    gl_FragData[0] = vec4(c, 1.0);
}
