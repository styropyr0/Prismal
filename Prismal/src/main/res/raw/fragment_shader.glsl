// ═══════════════════════════════════════════════════════════════════════════
// Prismal - Liquid Glass Fragment Shader
// lens + droplet height + dual-specular + Fresnel transmission/reflection
//
// Author: Saurav Sajeev
// ═══════════════════════════════════════════════════════════════════════════

precision highp float;

uniform sampler2D u_backgroundTexture;
uniform vec2      u_bgUvOffset;
uniform vec2      u_bgUvScale;
uniform sampler2D u_blurredTexture;
uniform int       u_useBlurredTexture;

uniform vec2  u_resolution;
uniform vec2  u_glassSize;
uniform vec4  u_cornerRadii;
uniform float u_refractionInset;
uniform float u_sminSmoothing;
uniform float u_edgeRefractionFalloff;

uniform float u_ior;
uniform float u_glassThickness;
uniform float u_normalStrength;
uniform float u_displacementScale;
uniform float u_heightTransitionWidth;

uniform float u_lensRefractionPx;
uniform float u_lensDepthEffect;

uniform float u_chromaticAberration;
uniform float u_dispersionR;
uniform float u_dispersionB;

uniform float u_vibrancy;
uniform float u_plainHighlight;

uniform float u_liquidDome;
uniform float u_fresnelReflect;

uniform float u_brightness;
uniform vec4  u_glassColor;
uniform float u_highlightWidth;

uniform vec2  u_lightDir;
uniform float u_specular;
uniform float u_shininess;
uniform float u_rimStrength;

uniform vec4  u_shadowColor;
uniform float u_shadowSoftness;

uniform float u_causticIntensity;
uniform float u_transmittance;

uniform vec2  u_backdropSampleScale;
uniform float u_parallaxScale;

uniform float u_pressProgress;
uniform float u_backdropPinch;
uniform vec2  u_glowCenter;
uniform float u_glowStrength;

uniform int   u_showNormals;

// 0 = final colour, 1..4 = write one of the glass cache layers (see glass_compose.glsl)
uniform int   u_bakeMode;
uniform float u_chromaMax;

varying vec2 v_screenTexCoord;
varying vec2 v_shapeCoord;

float radiusAtCentered(vec2 c, vec4 radii) {
    if (c.x >= 0.0) {
        if (c.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (c.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRectRealistic(vec2 coord, vec2 halfSize, float radius) {
    vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

vec2 gradSdRoundedRectRealistic(vec2 coord, vec2 halfSize, float radius) {
    vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        vec2 m = max(cornerCoord, 0.0);
        float len = length(m);
        if (len < 1e-5) return vec2(0.0);
        return sign(coord) * (m / len);
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * vec2(gradX, 1.0 - gradX);
    }
}

float circleMapRealistic(float x) {
    x = clamp(x, 0.0, 1.0);
    return 1.0 - sqrt(max(0.0, 1.0 - x * x));
}

float smin_poly(float a, float b, float k) {
    if (k <= 0.0) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float smax_poly(float a, float b, float k) {
    if (k <= 0.0) return max(a, b);
    float h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);
    return mix(b, a, h) + k * h * (1.0 - h);
}

float sdRoundBox(vec2 p, vec2 b, float r, float k) {
    if (k <= 0.0) {
        vec2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }
    vec2 q = abs(p) - b + r;
    float a = smax_poly(q.x, q.y, k);
    float c = smin_poly(a, 0.0, k * 0.5);
    vec2  ql = vec2(smax_poly(q.x, 0.0, k), smax_poly(q.y, 0.0, k));
    return c + length(ql) - r;
}

float getHeightFromDist(float dist, float tw) {
    float t = clamp(-dist / tw, 0.0, 1.0);
    return sqrt(max(0.0, 2.0 * t - t * t));
}

vec2 computeGradientHeight(vec2 pPx, vec2 halfSz, float cr, float k, float tw) {
    vec2 s = vec2(1.0, 1.0);
    float hpx = getHeightFromDist(sdRoundBox(pPx + vec2(s.x, 0.0), halfSz, cr, k), tw);
    float hnx = getHeightFromDist(sdRoundBox(pPx - vec2(s.x, 0.0), halfSz, cr, k), tw);
    float hpy = getHeightFromDist(sdRoundBox(pPx + vec2(0.0, s.y), halfSz, cr, k), tw);
    float hny = getHeightFromDist(sdRoundBox(pPx - vec2(0.0, s.y), halfSz, cr, k), tw);
    return vec2((hpx - hnx) * 0.5, (hpy - hny) * 0.5);
}

vec3 applyVibrancy(vec3 rgb, float sat) {
    if (sat <= 1.001) return rgb;
    float L = dot(rgb, vec3(0.213, 0.715, 0.072));
    return clamp(mix(vec3(L), rgb, sat), 0.0, 1.0);
}

vec4 sampleBg(vec2 uv) {
    return texture2D(u_backgroundTexture, u_bgUvOffset + uv * u_bgUvScale);
}

vec2 pack16(float v) {
    float x = floor(clamp(v, 0.0, 1.0) * 65535.0 + 0.5);
    float hi = floor(x / 256.0);
    return vec2(hi, x - hi * 256.0) / 255.0;
}

vec2 backdropUv(vec2 screenUv, vec2 offset, float pinchMix) {
    float press = clamp(u_pressProgress, 0.0, 1.0);
    float pinch = mix(1.0, max(u_backdropPinch, 0.01), press * pinchMix);
    vec2 s = max(u_backdropSampleScale, vec2(0.01)) / vec2(pinch);
    vec2 scaled = (screenUv - 0.5) / s + 0.5;
    return clamp(scaled + offset, vec2(0.0), vec2(1.0));
}

void main() {
    vec2 halfSz = u_glassSize * 0.5;
    float minDim = min(halfSz.x, halfSz.y);
    float pxNorm = clamp(minDim / 108.0, 0.36, 1.0) + smoothstep(88.0, 220.0, minDim) * 0.45;
    float edgePunch = mix(1.0, 1.12, smoothstep(74.0, 200.0, minDim));
    float smallGlass = smoothstep(128.0, 46.0, minDim * 2.0);
    edgePunch = mix(edgePunch, 1.0, smallGlass * 0.85);
    float rimScale = 1.0;
    float crMask = min(min(u_cornerRadii.x, u_cornerRadii.y), min(u_cornerRadii.z, u_cornerRadii.w));
    crMask = min(crMask, min(u_glassSize.x, u_glassSize.y) * 0.5);

    vec2 pPx = v_shapeCoord * u_glassSize;
    vec2 cKy = vec2(pPx.x, -pPx.y);

    float crMax = min(halfSz.x, halfSz.y);
    float radCorner = min(radiusAtCentered(cKy, u_cornerRadii), crMax);
    float sdKy = sdRoundedRectRealistic(cKy, halfSz, radCorner);

    float distMask = sdRoundBox(pPx, halfSz, crMask, u_sminSmoothing);
    float edgeDist = -distMask;
    float reflShell = smoothstep(clamp(minDim * 0.09, 1.8, 18.0), 0.0, edgeDist) * smoothstep(-3.0, 0.0, distMask);
    reflShell *= mix(0.78, 0.42, smallGlass);
    float opacity = smoothstep(0.75, -0.75, distMask);
    if (opacity < 0.001) discard;

    float dome = clamp(u_liquidDome, 0.0, 2.0);
    float refractionHeight = max(u_heightTransitionWidth * (1.0 + 0.55 * dome), 1.0);
    refractionHeight = min(refractionHeight, minDim * 0.98);

    float tw = max(u_heightTransitionWidth * (1.0 + 0.38 * dome), 1.0);
    tw = min(tw, minDim * 0.98);
    float hSig = getHeightFromDist(distMask, tw);
    vec2 gradHSig = vec2(0.0);
    if (distMask > -(tw + 2.5)) {
        gradHSig = computeGradientHeight(pPx, halfSz, crMask, u_sminSmoothing, tw);
    }

    float gradRadius = min(radCorner * 1.5, min(halfSz.x, halfSz.y));
    vec2 gradLens = gradSdRoundedRectRealistic(cKy, halfSz, gradRadius);

    float innerReach = max(min(halfSz.x, halfSz.y) - crMask * 0.42, minDim * 0.22);
    innerReach += refractionHeight * (1.0 + 0.25 * dome);
    innerReach = min(innerReach, max(halfSz.x, halfSz.y) * 0.95);
    float tDeep = clamp(edgeDist / max(innerReach, 2.0), 0.0, 1.0);
    float tShell = 1.0 - tDeep;

    float meniscusBand = smoothstep(0.0, 0.12, tShell);
    float hCap = pow(tShell, 0.38);
    float edgeBulge = 0.10 * pow(tShell, 2.8);
    float hDome = (hCap + edgeBulge) * meniscusBand;

    float coreBlend = smoothstep(0.0, 0.38, tDeep);
    float hSlab = mix(hSig * (0.58 + 0.42 * coreBlend), hSig, 0.4 + 0.6 * (1.0 - dome));

    float domeW = dome * (0.74 + 0.26 * smoothstep(0.12, 0.94, tShell));
    float height = mix(hSlab, hDome, domeW);
    float edgeRound = 1.0 - smoothstep(0.72, 1.0, tShell);
    height = clamp(height * (0.84 + 0.16 * meniscusBand + 0.08 * edgeRound), 0.0, 1.0);

    vec2 outward = (length(gradLens) > 1e-4) ? normalize(gradLens) : vec2(0.0, 1.0);
    float shellCurv = smoothstep(0.0, 1.0, tShell);
    vec2 gCap = outward * (-shellCurv * (0.38 / max(minDim, 8.0)));
    gCap *= meniscusBand * edgeRound;
    vec2 gradH = mix(gradHSig, gCap, domeW);

    vec3 N = normalize(vec3(-gradH.x * u_normalStrength, -gradH.y * u_normalStrength, 1.0));

    float menW = clamp(edgeDist / tw, 0.0, 1.0);
    float menCirc = sqrt(max(0.0, 1.0 - menW * menW));
    vec3 N_meniscus = normalize(vec3(-outward * menCirc * 0.95, 0.26 + 0.74 * menW));
    float menBlend = smoothstep(tw * 0.42, 0.0, edgeDist) * smoothstep(-4.0, 0.0, distMask) * 0.62;
    menBlend *= mix(1.0, 0.15, smallGlass);
    N = normalize(mix(N, N_meniscus, menBlend));

    float dropLens = pow(smoothstep(refractionHeight, 0.0, edgeDist), 0.82);

    if (u_showNormals == 1) {
        gl_FragColor = vec4(N * 0.5 + 0.5, opacity);
        return;
    }

    vec3 V = vec3(0.0, 0.0, 1.0);
    float cosVN = clamp(dot(N, V), 0.0, 1.0);
    float r0 = pow((1.0 - u_ior) / (1.0 + u_ior), 2.0);
    float silW = clamp(minDim * 0.12, 2.5, 34.0);
    float edgeSil = smoothstep(silW, 0.0, edgeDist) * smoothstep(-4.5, 0.0, distMask);
    float tiltW = clamp(length(N.xy) * 2.4, 0.0, 1.0);
    float grazingW = clamp(edgeSil * 0.94 + tiltW * 0.55, 0.0, 1.0);
    float cosVNeff = mix(cosVN, max(0.04, cosVN * 0.22 + 0.07 * tiltW), grazingW);
    float F = r0 + (1.0 - r0) * pow(1.0 - cosVNeff, 5.0);
    float fresCtl = clamp(u_fresnelReflect, 0.0, 5.0);
    float Fr = fresCtl;
    float cosVNrim = cosVNeff;

    vec2 cenSafe = cKy + vec2(1e-4, 1e-4);
    vec2 lensDir = gradLens + u_lensDepthEffect * normalize(cenSafe);
    float ldLen = length(lensDir);
    lensDir = ldLen > 1e-5 ? lensDir / ldLen : vec2(0.0);

    float lensRh = refractionHeight;
    float sdIn = min(sdKy, 0.0);
    float dLens = 0.0;
    if ((-sdKy) < lensRh) {
        dLens = circleMapRealistic(1.0 - (-sdIn / lensRh)) * (-u_lensRefractionPx);
        dLens *= (1.0 + clamp(u_pressProgress, 0.0, 1.0) * 0.45);
    }

    vec2 lensDeltaUv = (dLens * lensDir) / u_resolution;
    float parallaxK = 0.052 * u_displacementScale;
    vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK * u_parallaxScale;
    lensDeltaUv += parallax;
    lensDeltaUv *= mix(0.78, 1.12, (1.0 - F) * (0.42 + 0.58 * height));

    float refrStr = height * (0.5 + F * 0.35);
    vec3 refIn = refract(-V, N, 1.0 / u_ior);
    vec3 refOut = (dot(refIn, refIn) < 0.001) ? vec3(0.0) : refract(refIn, -N, u_ior);
    vec2 snellOff = (refOut.xy * u_glassThickness * refrStr / u_resolution) * u_displacementScale;
    snellOff *= mix(0.72, 1.18, (1.0 - F) * (0.5 + 0.5 * height));

    vec2 bDir = length(pPx) > 1e-3 ? -normalize(pPx) : vec2(0.0, -1.0);
    float bulge = smoothstep(0.05, 0.38, tDeep) * (1.0 - smoothstep(0.52, 0.94, tDeep));
    bulge = pow(max(bulge, 0.0), 0.62) * height * (0.014 + 0.01 * dome);
    bulge *= smoothstep(0.02, 0.36, tDeep) * dropLens;
    vec2 bulgeUv = bDir * bulge * u_glassSize / u_resolution;
    snellOff *= pxNorm * dropLens;
    bulgeUv *= pxNorm;

    vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;
    float pinchMix = 1.0 - smoothstep(0.0, 0.72, tDeep);
    vec2 uvCenter = backdropUv(v_screenTexCoord, baseOffset, pinchMix);
    float avgDim = (u_glassSize.x + u_glassSize.y) * 0.5;

    float caAmt = max(u_chromaticAberration, 0.0);
    float chromaMag = 0.0;
    vec3 color;

    if (caAmt < 0.02) {
        if (u_useBlurredTexture == 1) {
            color = texture2D(u_blurredTexture, uvCenter).rgb;
        } else {
            color = sampleBg(uvCenter).rgb;
        }
    } else {
        float chromaFar = avgDim * 0.5;
        float edgeFac = pow(smoothstep(chromaFar, 0.0, edgeDist), 1.8);
        float chromaBase = caAmt * 0.0018 * edgeFac;
        chromaMag = chromaBase * pxNorm;

        vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx) : vec2(0.0, 1.0);
        vec2 chromaPush = dispDir * chromaBase * pxNorm;
        vec2 uvR = backdropUv(v_screenTexCoord, baseOffset + chromaPush * u_dispersionR, pinchMix);
        vec2 uvG = uvCenter;
        vec2 uvB = backdropUv(v_screenTexCoord, baseOffset - chromaPush * u_dispersionB, pinchMix);

        if (u_useBlurredTexture == 1) {
            float r = texture2D(u_blurredTexture, uvR).r;
            float g = texture2D(u_blurredTexture, uvG).g;
            float b = texture2D(u_blurredTexture, uvB).b;
            color = vec3(r, g, b);
        } else {
            float r = sampleBg(uvR).r;
            float g = sampleBg(uvG).g;
            float b = sampleBg(uvB).b;
            color = vec3(r, g, b);
        }
    }

    color = applyVibrancy(color, u_vibrancy);

    vec2 gDir = normalize(gradLens + vec2(1e-4));
    float edgeG = reflShell * pow(1.0 - cosVNrim, 1.35) * mix(0.07, 0.62, F);
    edgeG *= mix(0.72, 0.38, smallGlass);
    float reflW = min(0.9, edgeG * (0.1 + fresCtl * 0.46) * (0.28 + 0.72 * height));
    vec2 reflDelta = gDir * (4.0 + 38.0 * pow(1.0 - cosVNrim, 1.25) + length(N.xy) * 14.0) / u_resolution * pxNorm;
    vec2 reflUv = clamp(v_screenTexCoord + baseOffset + reflDelta, vec2(0.0), vec2(1.0));
    vec3 reflSample;
    if (u_useBlurredTexture == 1) {
        reflSample = texture2D(u_blurredTexture, reflUv).rgb;
    } else {
        reflSample = sampleBg(reflUv).rgb;
    }
    color = mix(color, reflSample, reflW);

    vec3 skyHaze = vec3(0.88, 0.93, 1.02);
    float skyW = min(0.62, edgeG * pow(1.0 - cosVNrim, 1.2) * (0.04 + fresCtl * 0.28) * (0.28 + 0.72 * height));
    skyW *= mix(0.68, 0.32, smallGlass);
    color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);

    color *= u_brightness;
    color = mix(color, color * u_glassColor.rgb, u_glassColor.a);

    vec3 Lp = normalize(vec3(u_lightDir, 1.45));
    vec3 Ls = normalize(vec3(-u_lightDir.x * 0.62 + 0.41, -u_lightDir.y * 0.62 + 0.33, 0.74));
    vec3 Hp = normalize(Lp + V);
    vec3 Hs = normalize(Ls + V);

    float shadowExt = mix(0.15, 0.60, u_shadowSoftness > 1.0
        ? clamp(u_shadowSoftness / 20.0, 0.0, 1.0)
        : clamp(u_shadowSoftness, 0.0, 1.0));
    float shadowFalloff = avgDim * shadowExt;
    float innerShadow = 1.0 - smoothstep(0.0, shadowFalloff, edgeDist);
    innerShadow = pow(innerShadow, 2.35) * 0.62 * (0.22 + height * 0.68);
    color = mix(color, u_shadowColor.rgb * 0.25, innerShadow * u_shadowColor.a);
    vec3 shaded = color;
    color = vec3(0.0);

    float sh = max(u_shininess, 1.0);
    float sp = u_specular * 1.05;
    float specP = pow(max(dot(N, Hp), 0.0), sh) * sp;
    specP *= (0.32 + 0.68 * height);
    float specS = pow(max(dot(N, Hs), 0.0), sh * 0.68) * sp * 0.48;
    specS *= (0.24 + 0.76 * height) * (0.42 + 0.58 * F);
    float specEdge = smoothstep(0.0, max(5.0, tw * 0.35), edgeDist);
    color += (specP + specS) * specEdge * vec3(0.99, 0.993, 1.0);

    vec3 Vn = normalize(V);
    float dotNV = clamp(dot(N, Vn), 0.0, 1.0);
    float Fnv = pow(1.0 - dotNV, 2.9);
    float FedgeRim = pow(1.0 - cosVNrim, 3.25);

    float rimBandTight = mix(0.82, 0.52, smallGlass);
    float bandFracR = mix(0.022, 0.042, smoothstep(62.0, 218.0, minDim));
    float bandR = clamp(minDim * bandFracR * rimBandTight, mix(0.28, 0.65, 1.0 - smallGlass), min(12.0, minDim * 0.1));
    float rimW = max(bandR * 1.8, 2.2);
    float rimT = 1.0 - clamp(edgeDist / rimW, 0.0, 1.0);
    float shellRim = rimT * rimT * smoothstep(-2.2, 0.0, distMask);
    float centerQuiet = smoothstep(minDim * 0.18, minDim * 0.62, edgeDist);
    float depthFade = mix(1.0, 0.62, centerQuiet);

    vec2 cn = cKy / max(halfSz, vec2(1.0));
    vec2 Lxy = normalize(u_lightDir + vec2(1e-5));
    vec2 gN = normalize(gradLens + vec2(1e-4));
    float edgeLight = dot(gN, Lxy);

    float tl = max(0.0, min(-cn.x, -cn.y));
    float trc = max(0.0, min(cn.x, -cn.y));
    float br = max(0.0, min(cn.x, cn.y));
    float bl = max(0.0, min(-cn.x, cn.y));
    float lightDiag = smoothstep(-0.3, 0.3, Lxy.x + Lxy.y * 0.46);
    float pairOpp = pow(clamp(mix(tl + br, trc + bl, lightDiag), 0.0, 1.0), 1.06);
    float runAlong = smoothstep(0.14, 0.98, max(abs(cn.x), abs(cn.y)));
    float sx = exp(-abs(cn.y) * (2.25 + 1.85 * pairOpp));
    float sy = exp(-abs(cn.x) * (2.25 + 1.85 * pairOpp));
    float streakOpp = pairOpp * runAlong * max(sx, sy);

    vec3 hiSoft = vec3(0.98, 0.992, 1.008);
    vec3 hiVeil = vec3(0.958, 0.978, 1.012);
    vec3 oppTint = vec3(0.952, 0.968, 1.018);

    float schlickW = F;
    float litHairline = pow(max(edgeLight, 0.0), 3.6) * shellRim;
    float oppGlow = pow(max(-edgeLight, 0.0), 1.05) * shellRim * (0.28 + 0.72 * FedgeRim * schlickW);
    float rimLitSide = litHairline * u_rimStrength * mix(0.92, 1.18, smallGlass) * (0.58 + 0.42 * height) * depthFade;
    float rimOpposite = oppGlow * u_rimStrength * mix(0.34, 0.48, smallGlass) * (0.4 + 0.6 * height) * depthFade;
    color += hiSoft * rimLitSide * rimScale;
    color += mix(hiVeil, oppTint, 0.42) * rimOpposite * rimScale;

    float rimCorner = streakOpp * shellRim * u_rimStrength * 0.035 * FedgeRim * (0.35 + 0.65 * height);
    color += hiSoft * rimCorner * rimScale;

    float faceSheenSoft = smoothstep(rimW * 1.8, 0.0, edgeDist) * smoothstep(-2.0, 0.0, distMask)
        * smoothstep(0.08, 0.82, edgeLight) * Fnv * schlickW * u_rimStrength * 0.022;
    color += hiSoft * faceSheenSoft * (0.48 + 0.52 * height) * rimScale;

    float plusHL = rimT * u_plainHighlight * u_rimStrength
        * pow(max(edgeLight, 0.0), 2.2) * (1.0 - 0.55 * centerQuiet);
    plusHL *= mix(0.42, 0.06, smallGlass);
    color += plusHL * vec3(0.99, 0.995, 1.0);

    if (u_causticIntensity > 0.001) {
        float causticDot = dot(normalize(vec3(gradH * u_normalStrength, 0.45)), Lp);
        float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height;
        color += caust * vec3(1.0, 0.96, 0.90);
    }

    float pressGlow = clamp(u_pressProgress, 0.0, 1.0) * clamp(u_glowStrength, 0.0, 1.0);
    if (pressGlow > 0.001) {
        vec2 glowPx = u_glowCenter * u_glassSize - halfSz;
        float glowR = minDim * 1.5;
        float spot = smoothstep(glowR, glowR * 0.5, length(pPx - glowPx));
        color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);
    }

    if (u_bakeMode == 0) {
        gl_FragColor = vec4(shaded + color, opacity * u_transmittance);
    } else if (u_bakeMode == 1) {
        gl_FragColor = vec4(pack16(baseOffset.x * 0.125 + 0.5), pack16(baseOffset.y * 0.125 + 0.5));
    } else if (u_bakeMode == 2) {
        gl_FragColor = vec4(reflW, skyW, innerShadow * u_shadowColor.a, opacity);
    } else if (u_bakeMode == 3) {
        gl_FragColor = vec4(clamp(color, 0.0, 1.0), clamp(chromaMag / u_chromaMax, 0.0, 1.0));
    } else {
        vec2 reflPx = reflDelta * u_resolution / 1024.0 + 0.5;
        gl_FragColor = vec4(pack16(reflPx.x), pack16(reflPx.y));
    }
}
