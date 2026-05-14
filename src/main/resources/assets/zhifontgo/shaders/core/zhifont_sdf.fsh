#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float distanceValue = texture(Sampler0, texCoord0).r;
    float edgeWidth = max(fwidth(distanceValue) * 0.75, 0.02);
    float alpha = smoothstep(0.5 - edgeWidth, 0.5 + edgeWidth, distanceValue);

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;
    if (color.a < 0.01) {
        discard;
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
