package com.autumn.douyin.liquidglass.ui

/**
 * AGSL runtime shaders that produce the liquid-glass look.
 *
 * These are distilled from Kyant0's Backdrop library (Apache-2.0):
 * a signed-distance-field rounded rect drives edge refraction + optional
 * chromatic dispersion, plus a directional rim highlight.
 *
 * RuntimeShader / AGSL requires API 33 (Android 13), which is why the module
 * sets minSdk = 33.
 */
object LiquidGlassShaders {

    private const val SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y; else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x; else return radii.w;
    }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 c = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(c, 0.0)) - radius;
    float inside = min(max(c.x, c.y), 0.0);
    return outside + inside;
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 c = abs(coord) - (halfSize - float2(radius));
    if (c.x >= 0.0 || c.y >= 0.0) {
        return sign(coord) * normalize(max(c, 0.0));
    } else {
        float gx = step(c.y, c.x);
        return sign(coord) * float2(gx, 1.0 - gx);
    }
}
"""

    /**
     * Refraction shader. `content` is the (already blurred) backdrop.
     * Uniforms:
     *   size              — glass size in px
     *   cornerRadii       — TL,TR,BR,BL
     *   refractionHeight  — thickness of the refracting rim (px)
     *   refractionAmount  — how far coordinates bend (px, sign flips direction)
     *   depthEffect       — 0/1, adds a center-ward bulge
     *   chromaticAmount   — 0 disables dispersion, else split strength
     */
    val REFRACTION = """
uniform shader content;
uniform float2 size;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAmount;

$SDF

float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 cc = coord - halfSize;
    float radius = radiusAt(cc, cornerRadii);

    float sd = sdRoundedRect(cc, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - (-sd / refractionHeight)) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(cc, halfSize, gradRadius) + depthEffect * normalize(cc));

    float2 refracted = coord + d * grad;

    if (chromaticAmount <= 0.0) {
        return content.eval(refracted);
    }

    // Cheap chromatic aberration: shift RGB along the refraction vector.
    float2 shift = d * grad * chromaticAmount * ((cc.x * cc.y) / (halfSize.x * halfSize.y));
    half4 c = half4(0.0);
    half4 r = content.eval(refracted + shift);
    half4 g = content.eval(refracted);
    half4 b = content.eval(refracted - shift);
    c.r = r.r;
    c.g = g.g;
    c.b = b.b;
    c.a = (r.a + g.a + b.a) / 3.0;
    return c;
}
"""

    /**
     * Directional rim highlight. Draws a bright arc along one edge so the pill
     * reads as a raised piece of glass. Composited additively on top.
     */
    val HIGHLIGHT = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float angle;
uniform float falloff;
layout(color) uniform half4 color;

$SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 cc = coord - halfSize;
    float radius = radiusAt(cc, cornerRadii);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(cc, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    return color * intensity;
}
"""
}
