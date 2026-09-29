#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// Space-suit helmet sonar: surfaces coloured by distance (red = close, blue = far),
// deliberately low fidelity: pixelated, blurred and grainy, with expanding scan rings.
// No return (sky) stays black.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SonarConfig {
    float MaxRange;  // blocks mapped onto the red → blue ramp
    float Near;      // camera near plane (vanilla: 0.05)
    float Far;       // far plane = max(render distance * 4, cloud range * 16); 2048 is the
                     // usual value, and within MaxRange the error stays under ~10%
};

layout(location = 0) out vec4 fragColor;

// 26.x renders with reversed Z (1 = near, 0 = far/sky); same maths as vanilla's
// deviceToLinearDepth, with the projection terms rebuilt from Near/Far.
float linearDepth(float deviceDepth) {
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    float m22 = Near / (Far - Near);
    float m32 = Near * Far / (Far - Near);
    float d = deviceDepth;
#else
    float m22 = (Near + Far) / (Far - Near);
    float m32 = 2.0 * Near * Far / (Far - Near);
    float d = (deviceDepth - 0.5) * 2.0;
#endif
    return m32 / (d + m22);
}

vec3 heat(float t) {
    vec3 c = mix(vec3(1.0, 0.0, 0.0), vec3(1.0, 1.0, 0.0), smoothstep(0.0, 0.25, t));
    c = mix(c, vec3(0.0, 1.0, 0.0), smoothstep(0.25, 0.5, t));
    c = mix(c, vec3(0.0, 1.0, 1.0), smoothstep(0.5, 0.75, t));
    return mix(c, vec3(0.0, 0.2, 1.0), smoothstep(0.75, 1.0, t));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// One sonar return: heat colour by range with the scan ring; sky / no return is near black.
vec3 sonar(vec2 uv, float seconds) {
    float deviceDepth = texture(DepthSampler, uv).r;
    if (deviceDepth <= 0.0) return vec3(0.0, 0.0, 0.03);
    float z = linearDepth(deviceDepth);
    float t = clamp(z / MaxRange, 0.0, 1.0);
    vec3 color = heat(t) * mix(1.0, 0.3, smoothstep(0.85, 1.0, t));
    float ring = fract(z / 6.0 - seconds * 1.5);
    color += vec3(0.3) * smoothstep(0.94, 1.0, ring);
    float lum = dot(texture(InSampler, uv).rgb, vec3(0.299, 0.587, 0.114));
    return color * (0.85 + 0.2 * lum);
}

void main() {
    // Low-fidelity transducer: coarse pixels (~180 rows), each one a blurred 3x3 average of
    // its neighbourhood, plus light fine-grain noise.
    vec2 grid = vec2(floor(180.0 * OutSize.x / OutSize.y), 180.0);
    vec2 cell = floor(texCoord * grid);
    vec2 uv = (cell + 0.5) / grid;
    vec2 step2 = 1.0 / grid;
    float seconds = GameTime * 24000.0 / 20.0;

    vec3 color = vec3(0.0);
    float total = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            float w = (i == 0 && j == 0) ? 4.0 : ((i == 0 || j == 0) ? 2.0 : 1.0);
            color += sonar(uv + vec2(i, j) * step2 * 0.9, seconds) * w;
            total += w;
        }
    }
    color /= total;

    float grain = hash(floor(texCoord * OutSize) + floor(seconds * 24.0) * 7.13) - 0.5;
    color += grain * 0.10;
    float scanline = 0.93 + 0.07 * sin(texCoord.y * OutSize.y * 3.14159);
    fragColor = vec4(max(color, 0.0) * scanline, 1.0);
}
