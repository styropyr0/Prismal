# Prismal - Technical Documentation

> **Author:** Saurav Sajeev  
> A reference document covering every principle, algorithm, and design decision that makes the liquid glass rendering work.

---

## Table of Contents

1. [What Prismal Is](#1-what-prismal-is)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Background Capture Pipeline](#3-background-capture-pipeline)
4. [Two-Pass Gaussian Blur](#4-two-pass-gaussian-blur)
5. [Signed Distance Fields](#5-signed-distance-fields)
6. [Height Field - The Glass Slab](#6-height-field--the-glass-slab)
7. [Spherical Meniscus - Water Droplet Curvature](#7-spherical-meniscus--water-droplet-curvature)
8. [Surface Normals](#8-surface-normals)
9. [Snell's Law Refraction](#9-snells-law-refraction)
10. [Lens Distortion and Parallax](#10-lens-distortion-and-parallax)
11. [Chromatic Aberration and Dispersion](#11-chromatic-aberration-and-dispersion)
12. [Fresnel Effect](#12-fresnel-effect)
13. [Lighting Model](#13-lighting-model)
14. [Rim and Border Highlights](#14-rim-and-border-highlights)
15. [Caustics](#15-caustics)
16. [Shadow](#16-shadow)
17. [Vibrancy](#17-vibrancy)
18. [Spring Physics Animator](#18-spring-physics-animator)
19. [Component Design - Slider, Switch, IconButton](#19-component-design--slider-switch-iconbutton)
20. [Key Parameter Reference](#20-key-parameter-reference)
21. [Critical Sizing Rule](#21-critical-sizing-rule)

---

## 1. What Prismal Is

Prismal renders an iOS-style liquid glass material on Android using **OpenGL ES 2.0** inside a `GLSurfaceView`. The key property of the material is that it refracts, blurs, tints, and lights the content *underneath* it - not a static overlay, but a physically-inspired live effect driven by a GLSL fragment shader.

The approach is fundamentally different from Android's native `RenderEffect` (AGSL) used by typical backdrop-blur libraries. Prismal runs in its own OpenGL context, captures the underlying view hierarchy into a bitmap, uploads it as a texture, and computes the glass appearance entirely on the GPU per fragment.

---

## 2. High-Level Architecture

```
Android View Hierarchy
        │
        │ (View.draw → Canvas → Bitmap)
        ▼
  Background Capture          ← PrismalFrameLayout.scheduleCaptureBackground()
        │
        │ (Bitmap → GL texture upload on GL thread)
        ▼
  backgroundTexture (GL_TEXTURE_2D)
        │
        ├──────────────────────────────────────────────────┐
        │                                                  │
        ▼                                                  ▼
  [blurHProgram]                                   [bgProgram]
  Horizontal Gaussian pass                    Draw raw bitmap as
  → blurTex1 / blurFbo1                       fullscreen background quad
        │
        ▼
  [blurVProgram]
  Vertical Gaussian pass
  → blurTex2 / blurFbo2  (frosted texture)
        │
        └──────────────┐
                       ▼
               [glassProgram]        ← fragment_shader.glsl
               SDF mask + height
               Normals + refraction
               Fresnel + specular
               Rim / caustic / shadow
               → composited glass quad
```

**Classes involved:**

| Class | Role |
|---|---|
| `PrismalFrameLayout` | Android `FrameLayout` host; owns the `GLSurfaceView`, triggers background capture, exposes the public API |
| `PrismalGlassRenderer` | `GLSurfaceView.Renderer`; manages all GL state, shader programs, FBOs, and uniform uploads |
| `fragment_shader.glsl` | The main glass fragment shader - all the optics math lives here |
| `PrismalLiquidGlass` | Singleton with the canonical default parameter set (the iOS-calibrated recipe) |
| `SpringAnimator` | Choreographer-driven spring for smooth physics animations |

---

## 3. Background Capture Pipeline

The glass must refract real content beneath it. The problem: `GLSurfaceView` renders into a separate EGL surface that composites at the OS level - it cannot read pixels from the regular Android `Canvas` pipeline directly.

**Solution:** `PrismalFrameLayout` draws the view hierarchy above it (its parent's background plus sibling views) into a `Bitmap` using `View.draw(canvas)`, then uploads that bitmap to a GL texture on the GL thread.

```kotlin
// PrismalFrameLayout - simplified capture flow
fun scheduleCaptureBackground() {
    if (captureScheduled) return   // deduplication flag
    captureScheduled = true
    post {
        captureScheduled = false
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // walk up to root, translate so this view is at (0,0)
        parent?.draw(canvas)
        glSurface.queueEvent {
            renderer.setBackgroundTexture(bitmap)  // uploads & recycles
            glSurface.requestRender()
        }
    }
}
```

The `captureScheduled` flag prevents redundant captures when multiple events fire in the same frame (scroll + layout change, for example).

**Y-axis flip:** Android's `Canvas` has origin top-left; OpenGL has origin bottom-left. The vertex shader flips the `v_shapeCoord` Y so textures sample correctly without an extra blit.

---

## 4. Two-Pass Gaussian Blur

The frosted-glass frost effect uses a separable Gaussian blur applied to the captured background. Separating an N×N Gaussian into a horizontal and a vertical 1D pass reduces sample count from O(N²) to O(2N).

**Pass 1 - Horizontal** (`prismal_blur_h`):  
Reads `backgroundTexture` → writes `blurTex1` (via `blurFbo1`).

**Pass 2 - Vertical** (`prismal_blur_v`):  
Reads `blurTex1` → writes `blurTex2` (via `blurFbo2`).

The glass shader then reads from:
- `u_backgroundTexture` - the raw (sharp) texture, used for refraction rays that exit through the front face
- `u_blurredTexture` - the frosted texture, selected when `u_useBlurredTexture == 1`

The blur sigma is driven by `blurRadius` (in pixels). Higher = more frosted.

---

## 5. Signed Distance Fields

A **Signed Distance Field (SDF)** is a scalar function `f(p)` that returns the signed distance from point `p` to the nearest surface of a shape - negative inside, zero on the boundary, positive outside.

Prismal uses two SDF variants for the rounded rectangle:

### `sdRoundedRectRealistic` - exact, per-quadrant radius

```glsl
float sdRoundedRectRealistic(vec2 coord, vec2 halfSize, float radius) {
    vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside  = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
```

`abs(coord)` folds all four quadrants into one (top-right), subtracts the inner rectangle extent `(halfSize - radius)`, then uses `length(max(..., 0))` for the rounded corner arc and `min(max(...), 0)` for the interior. It supports per-quadrant radii by calling `radiusAtCentered` before the call to pick the right radius for the fragment's corner.

### `sdRoundBox` - smooth-min version

```glsl
float sdRoundBox(vec2 p, vec2 b, float r, float k) { ... }
```

Uses `smax_poly` / `smin_poly` (polynomial smooth-min/max) for the corner join. The `k` parameter controls blending radius - higher = softer, rounder corners at the join. This is used for the height field and opacity mask because smoothed edges look better for the ramp profile.

### `gradSdRoundedRectRealistic` - analytic gradient

The analytic gradient of the SDF (the outward normal in 2D screen space) is used to:
- Drive the lens distortion direction
- Define the `outward` vector (pointing away from the shape centre, following the rounded-rect iso-curves)
- Compute the `gN` normal for rim highlight orientation

---

## 6. Height Field - The Glass Slab

The glass is modelled as a **height field** `h(p)` - a 2D function over the glass quad that represents the physical thickness of the glass at each pixel. Where `h = 1` the glass is at full depth (centre of a dome); where `h = 0` the glass is at the silhouette edge (zero thickness).

This height field is what produces the lens curvature, specular highlights, and normal map - everything that makes the glass look three-dimensional.

### Circular arc profile (`getHeightFromDist`)

```glsl
float getHeightFromDist(float dist, float tw) {
    float t = clamp(-dist / tw, 0.0, 1.0);
    return sqrt(max(0.0, 2.0 * t - t * t));
}
```

`dist` is the SDF value (negative inside the shape). `tw` is the transition width - the pixel-space ramp length over which height rises from 0 to 1.

The formula `sqrt(2t - t²)` is a **circular arc**: it maps the normalised depth `t ∈ [0,1]` through one quarter of a unit circle, giving:

- `t = 0` (silhouette edge) → `h = 0` - the glass has zero thickness here
- `t = 1` (fully inside) → `h = 1` - maximum depth
- The derivative `dh/dt = (1-t)/sqrt(2t-t²)` is steep near the edge and flattens toward the centre - exactly the profile of a meniscus or water droplet viewed from above

Previously a logistic sigmoid was used (`1 / (1 + exp(...))`), which gave `h ≈ 0.25` at the silhouette rather than 0. That residual height made the edge feel flat and linear - the sigmoid never truly reached zero. The circular arc guarantees `h = 0` at the exact silhouette, producing the sharp, clean water-droplet edge curvature.

### Dome mode (`u_liquidDome`)

When `u_liquidDome > 0`, the height field blends between two models:

**Slab mode (`dome = 0`):** `hSlab` - the SDF-ramp profile, producing a flat top with curved edges. Like a glass disc.

**Dome mode (`dome = 1`):** `hDome` - built from `tShell` (normalised distance from edge to centre):

```glsl
float hCap    = pow(tShell, 0.88);          // spherical cap profile
float edgeBulge = 0.24 * pow(tShell, 2.45); // surface-tension bulge at contact line
float hDome   = (hCap + edgeBulge) * meniscusBand;
```

The result looks like a sessile water droplet: a curved dome with a slight bulge at the contact line where the liquid meets the surface.

The `tw` (transition width) also widens proportionally with dome strength:

```glsl
float tw = max(u_heightTransitionWidth * (1.0 + 0.38 * dome) + minDim * 0.085, 1.0);
```

A stronger dome needs a wider edge ramp.

---

## 7. Spherical Meniscus - Water Droplet Curvature

The surface normal at the silhouette of a water droplet does not point straight up - it curves outward and downward at the contact angle. This is the **meniscus** effect that makes water droplets and real glass look spherical rather than like cut cylinders.

After computing the gradient-derived normal `N`, a meniscus normal `N_meniscus` is blended in at the rim zone:

```glsl
float menW     = clamp(edgeDist / tw, 0.0, 1.0);          // 0 at edge, 1 deep inside
float menCirc  = sqrt(max(0.0, 1.0 - menW * menW));       // circular arc: steep at edge
vec3 N_meniscus = normalize(vec3(-outward * menCirc * 0.95, 0.26 + 0.74 * menW));
float menBlend  = smoothstep(tw * 0.5, 0.0, edgeDist)     // active only near silhouette
                * smoothstep(-6.0, 0.0, distMask) * 0.82;
N = normalize(mix(N, N_meniscus, menBlend));
```

`N_meniscus` points radially outward (in the `outward` direction) at the silhouette with the Z component starting low (nearly horizontal, like the side of a sphere) and rising toward vertical as you move inward. `menCirc` traces a quarter-circle cross section.

This is what makes the sides of the glass appear **spherically curved** rather than flat vertical walls.

---

## 8. Surface Normals

The surface normal `N` is computed from the height field gradient via finite differences (central differences, 1-pixel step):

```glsl
vec2 computeGradientHeight(vec2 pPx, vec2 halfSz, float cr, float k, float tw) {
    float hpx = getHeightFromDist(sdRoundBox(pPx + vec2(1,0), halfSz, cr, k), tw);
    float hnx = getHeightFromDist(sdRoundBox(pPx - vec2(1,0), halfSz, cr, k), tw);
    float hpy = getHeightFromDist(sdRoundBox(pPx + vec2(0,1), halfSz, cr, k), tw);
    float hny = getHeightFromDist(sdRoundBox(pPx - vec2(0,1), halfSz, cr, k), tw);
    return vec2((hpx - hnx) * 0.5, (hpy - hny) * 0.5);
}
```

The 3D normal is then:

```glsl
vec3 N = normalize(vec3(-gradH.x * normalStrength, -gradH.y * normalStrength, 1.0));
```

The negative sign makes the normal point away from the surface for a dome (the slope rises toward the centre, so the gradient points inward, and the negated gradient points the normal outward/upward). The Z component `1.0` keeps the normal generally pointing toward the viewer. `u_normalStrength` scales the XY tilt to control how "steep" the glass looks.

After computing N, the meniscus blend is applied (see §7), then the debug `u_showNormals` path can visualise N as `(N * 0.5 + 0.5)` - a standard normal-map colour encoding.

---

## 9. Snell's Law Refraction

Real glass bends light at its surfaces according to **Snell's law**:

```
n₁ sin(θ₁) = n₂ sin(θ₂)
```

where `n` is the index of refraction (IOR) and `θ` is the angle to the surface normal. Air has IOR ≈ 1.0; soda-lime glass ≈ 1.52; water ≈ 1.33.

In the shader, `refract()` is GLSL's built-in implementation of Snell's law. The glass is modelled as a slab with two refractive interfaces: air → glass (entry) and glass → air (exit):

```glsl
vec3 V    = vec3(0.0, 0.0, 1.0);          // view direction (straight down)
vec3 refIn  = refract(-V, N, 1.0 / u_ior);    // ray enters glass from above
vec3 refOut = refract(refIn, -N, u_ior);       // ray exits glass from below
vec2 snellOff = refOut.xy * u_glassThickness * refrStr / u_resolution * u_displacementScale;
```

`refOut.xy` is the lateral displacement of the ray after passing through the full slab. `u_glassThickness` is the physical slab thickness in pixels - a thicker slab produces more displacement for the same IOR.

`refrStr` modulates strength by height and Fresnel:

```glsl
float refrStr = height * (0.5 + F * 0.35);
```

Pixels near the silhouette (`height ≈ 0`) see almost no Snell displacement; the bright Fresnel rim pixels (`F` high) see slightly amplified displacement.

---

## 10. Lens Distortion and Parallax

Beyond Snell refraction there are two more UV offset contributions:

### Lens distortion (`dLens`)

Lens distortion pushes pixels near the silhouette inward (barrel) or outward (pincushion) based on the SDF depth. The `circleMapRealistic` function maps the ramp nonlinearly:

```glsl
float circleMapRealistic(float x) {
    return 1.0 - sqrt(max(0.0, 1.0 - x * x));  // quarter-circle ease-out
}
```

The distortion is applied along `lensDir` - the SDF gradient blended slightly toward the shape centre to give a compound "barrel through a fisheye" look.

### Parallax (`parallax`)

Parallax fakes the stereo depth of the glass by offsetting the UV based on the surface tilt (via `gradLens`) and height:

```glsl
vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK;
```

Where the Fresnel factor `F` is high (grazing angle - silhouette), parallax increases strongly, making the edge feel physically deep. Where `F` is low (face-on centre), parallax is subtle.

### Combined offset

All three contributions sum into `baseOffset`:

```glsl
vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;
```

`bulgeUv` adds a subtle inward push at the glass centre (the `bulge` term), mimicking how a convex lens magnifies slightly around the middle.

The whole `baseOffset` is then scaled by `pxNorm` - a normalisation factor clamped to `[0.36, 1.0]` that ensures small glass elements don't over-displace and large hero panels get proportionally more effect.

---

## 11. Chromatic Aberration and Dispersion

Real glass has a different IOR for each wavelength - red bends less than blue. This is **chromatic dispersion**, and it creates the coloured fringes visible at the edges of thick glass.

When `u_chromaticAberration > 0`, the shader samples three separate UV coordinates and assembles them into a single RGB:

```glsl
vec2 dispDir  = normalize(gradLens);     // radially outward from centre
vec2 chromaPush = dispDir * (chromaBase + realChroma) * pxNorm;
vec2 uvR = baseOffset + chromaPush * u_dispersionR;   // red shifts outward
vec2 uvG = baseOffset;                                // green stays centred
vec2 uvB = baseOffset - chromaPush * u_dispersionB;   // blue shifts inward
```

`u_dispersionR` and `u_dispersionB` are independent multipliers (defaults = 1.0), letting you model asymmetric glass dispersion (e.g. heavy flint glass has stronger blue shift).

The cross-pattern chroma term adds an extra diagonal push proportional to `(x·y) / (halfW · halfH)` — aberration that matches the corner distortion visible in real wide-angle lenses.

The effect is gated by `edgeFac = pow(smoothstep(chromaFar, 0, edgeDist), 1.8)` - aberration only appears near the silhouette where thickness (and therefore dispersion) is maximum.

---

## 12. Fresnel Effect

The **Fresnel equations** describe how much light is reflected vs transmitted at a dielectric surface as a function of the angle of incidence. At normal incidence (looking straight at the glass face) most light transmits; at grazing incidence (viewing from the edge) most light reflects.

Prismal uses the **Schlick approximation**:

```glsl
float r0 = pow((1.0 - u_ior) / (1.0 + u_ior), 2.0);   // reflectance at normal incidence
float F  = r0 + (1.0 - r0) * pow(1.0 - cosVNeff, 5.0);
```

`r0` for IOR 1.52 (glass) ≈ 0.043 - about 4% reflectance straight-on.
At the silhouette, `cosVNeff → 0`, so `F → 1` (nearly 100% reflection).

`cosVNeff` is artificially pushed toward a small value near the silhouette (`edgeSil`) and where the surface normal tilts strongly sideways (`tiltW`), to exaggerate the grazing-angle rim glow on a flat panel (which would otherwise never reach true grazing angle since the viewer is always looking straight down).

`F` feeds into:
- Parallax strength - more depth illusion where reflection is strong
- Refraction strength - less transmitted light where reflection takes over
- Rim diffuse / veil highlights - the Fresnel-gated bright edge ring
- Shadow and specular weights

---

## 13. Lighting Model

### Light directions

Two lights are used - the primary key light `Lp` and a secondary fill `Ls`:

```glsl
vec3 Lp = normalize(vec3(u_lightDir, 1.45));
vec3 Ls = normalize(vec3(-u_lightDir.x * 0.62 + 0.41, -u_lightDir.y * 0.62 + 0.33, 0.74));
```

`Ls` is derived from `Lp` with partial negation and an offset - it acts as a soft fill from the opposite-upper direction, avoiding a completely dark shadow side.

### Blinn-Phong dual specular

Half-vectors `Hp` and `Hs` are computed for each light:

```glsl
vec3 Hp = normalize(Lp + V);
vec3 Hs = normalize(Ls + V);
float specP = pow(max(dot(N, Hp), 0.0), shininess) * specular * 1.05;
float specS = pow(max(dot(N, Hs), 0.0), shininess * 0.68) * specular * 0.48;
```

The secondary specular uses a broader exponent (`shininess * 0.68`) and lower intensity (`0.48x`), producing a wider, softer secondary highlight. Both are height-gated (`0.32 + 0.68 * height`) to suppress highlights near the silhouette where the glass is thin. The specular tint `vec3(0.99, 0.993, 1.0)` is a very slight cool white - real glass specular is almost colourless with a faint blue shift.

---

## 14. Rim and Border Highlights

This is the most distinctive visual feature of iOS liquid glass - bright highlights along the borders. Prismal has five layered contributions:

### `rimDiffuse` - Fresnel-gated shell glow

```glsl
float rimDiffuse = FedgeRim * schlickW * shellRim * u_rimStrength * (0.1 + 0.34 * wrapAlong) * depthFade;
```

`FedgeRim = pow(1 - cosVN, 3.25)` - strongest at grazing. `schlickW = F`. `shellRim` - a band that falls off inward from the silhouette. `wrapAlong` - a tangential component that boosts corners where the light direction runs along the border (like light wrapping around a cylinder).

### `rimInnerVeil` - soft interior haze

A second, wider Fresnel glow (`Fnv * schlickW * shellInner`) using a broader band (`shellInner`) to add depth to the frosted interior near edges.

### `rimCorner` - corner streak

`streakOpp` encodes which pair of opposite corners is lit by the current `u_lightDir`:

```glsl
float tl = max(0, min(-cn.x, -cn.y));  // top-left weight
float pairOpp = pow(mix(tl + br, trc + bl, lightDiag), 1.06);
```

`runAlong * max(sx, sy)` gives exponential fall-off perpendicular to the streak axis - creating a sharp diagonal highlight through the lit corners as if light skims along the glass edge.

### `rimBothBorders` - the opposite-side highlight

```glsl
float borderAlign = pow(abs(dot(gN, Lxy)), 1.0);
color += hiSoft * rimBothBorders * edgePunch;
```

`abs(dot(gN, Lxy))` - the absolute value of the dot product between the SDF gradient and the light direction. Using `abs` makes this **maximum on BOTH the lit border AND the opposite border** (the one facing 180° away from the light). This is a defining characteristic of iOS-style liquid glass — real backlit glass shows a highlight on the shaded side because light enters the glass, total-internally-reflects, and exits the opposite face.

### `rimLitSide` - lit-side emphasis

```glsl
float litAlign = pow(max(0.0, dot(gN, Lxy)), 1.3);
```

Without `abs`, this only fires on the lit border - an additional, slightly stronger boost on the primary lit edge.

### `faceSheenSoft` - face-on sheen

A soft glow on the glass face where the surface normal tilts toward the light:

```glsl
smoothstep(-0.08, 0.74, dot(N.xy, -Lxy)) * Fnv * schlickW * u_rimStrength * 0.038
```

This adds the subtle brightness gradient across the glass face that makes it look rounded even at the centre.

---

## 15. Caustics

A caustic is the bright pattern of focused light on a surface beneath a glass object (the sparkly patterns at the bottom of a swimming pool). In 2D, it approximates as a specular-like inner glow where the surface normal aligns with the key light:

```glsl
float causticDot = dot(normalize(vec3(gradH * u_normalStrength, 0.45)), Lp);
float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height;
color += caust * vec3(1.0, 0.96, 0.90);
```

The warm tint `(1, 0.96, 0.90)` mimics the warm-white colour of focused sunlight through glass. Gating by `height` keeps caustics away from the silhouette where the glass is thin and light doesn't focus.

---

## 16. Shadow

An inner shadow along the glass interior edge mimics the dark zone visible where thick glass blocks light from reaching the surface below:

```glsl
float innerShadow = 1.0 - smoothstep(0.0, shadowFalloff, edgeDist);
innerShadow = pow(innerShadow, 2.0) * 0.85 * (0.28 + height * 0.72);
color = mix(color, u_shadowColor.rgb * 0.25, innerShadow * u_shadowColor.a);
```

`shadowFalloff = avgDim * shadowExt` where `shadowExt` is derived from `u_shadowSoftness`:
- `softness ≤ 1` → `shadowExt = mix(0.15, 0.60, softness)` - moderate fall-off
- `softness > 1` → `shadowExt = mix(0.15, 0.60, softness / 20)` - softness is treated as a wide diffuse spread

`pow(..., 2.0)` makes the shadow fall off quadratically from the edge - a softer, more physically-plausible decay than linear.

---

## 17. Vibrancy

The background content visible through the glass is passed through a vibrancy filter before all lighting is added:

```glsl
vec3 applyVibrancy(vec3 rgb, float sat) {
    float L = dot(rgb, vec3(0.213, 0.715, 0.072));  // luminance (Rec.709)
    return clamp(mix(vec3(L), rgb, sat), 0.0, 1.0);
}
color = applyVibrancy(color, u_vibrancy);
```

This is a simple **chroma boost**: it interpolates between the greyscale luminance and the original colour by `sat`. Values above 1.0 push colours beyond their natural saturation. Prismal uses `u_vibrancy = 1.28` (hardcoded), matching the characteristic "pumped" saturation of Apple's vibrancy effect. iOS liquid glass never shows dull content through the frosted pane.

---

## 18. Spring Physics Animator

The slider, switch, and other animated controls use `SpringAnimator` - a lightweight Hooke's law spring integrated per display frame via `Choreographer`:

```
F = -k(x - target) - d·v      (spring force = restoring + damping)
v += F · dt
x += v · dt
```

Parameters:
- `k` = stiffness (resistance to displacement from target)
- `d = 2 · dampingRatio · √k` (critical damping formula - ensures smooth settling)
- `threshold` (default 0.001) - stops the animation when both position error and velocity are negligible

**Choreographer** provides frame-locked callbacks (`postFrameCallback`) - each frame the spring steps one `dt` forward based on the real elapsed nanoseconds, capped at 48ms to prevent large jumps after the screen is off.

The three spring instances used by each control:

| Spring | damping | stiffness | Purpose |
|--------|---------|-----------|---------|
| `posSpring` | 1.0 | 1000 | Thumb position (critically damped - no overshoot) |
| `pressSpring` | 1.0 | 1000 | Press state → glass opacity / optics transition |
| `scaleXSpring` | 0.6 | 250 | Thumb X scale (underdamped - slight overshoot for bounce) |
| `scaleYSpring` | 0.7 | 250 | Thumb Y scale (underdamped) |

### Velocity squish

While dragging, normalised drag velocity `normVel = xVelocity / travelPx` modulates the scale:

```kotlin
val sx = scaleXSpring.value / (1f - v * 0.75f)   // elongate along motion
val sy = scaleYSpring.value * (1f - abs(v) * 0.25f) // compress perpendicular
```

This produces the "squish" deformation: the thumb stretches forward into the direction of motion and squishes perpendicular to it - the same organic deformation seen in iOS animations.

---

## 19. Component Design - Slider, Switch, IconButton

### PrismalSlider

```
FrameLayout (PrismalSlider)
├── TrackView (custom View)    ← 6dp capsule, draws bg + fill rects in onDraw
└── PrismalFrameLayout (thumb) ← 40×24dp, gravity=CENTER_VERTICAL
    └── View (white overlay)   ← alpha=1 at rest, fades to 0 on press
```

- The white overlay sits above the GLSurface (which renders behind the Window surface) and is used to hide the glass at rest, revealing it only when the user presses — a white rect whose alpha fades from `1f` to `0f` as press progress increases.
- Background capture (`thumb.updateBackground()`) is called on every `ACTION_MOVE` event. The `captureScheduled` deduplication flag inside `PrismalFrameLayout` ensures only one capture per frame even if many events arrive.

### PrismalSwitch

```
FrameLayout (PrismalSwitch, gravity=CENTER_VERTICAL for children)
├── TrackView (custom View)    ← 64×28dp (proportional to psw_trackHeight)
│   └── lerpColor(offColor, onColor, fraction) drawn in onDraw
└── PrismalFrameLayout (thumb) ← 40×24dp, gravity=CENTER_VERTICAL
    └── View (white overlay)
```

- `fraction ∈ [0,1]` is the live animated value (driven by `posSpring`). The track colour and thumb X position are both keyed to `fraction` so the colour crossfade is perfectly in sync with thumb travel.
- `travelPx = trackW - thumbW - 2·padPx = 64 - 40 - 4 = 20dp` of horizontal drag travel.
- Tap vs drag: if `|rawX - dragStartX| > touchSlop` → drag mode (snap to nearest 0/1 on release). Otherwise → toggle mode (invert `isOn`).

### PrismalIconButton

- Calls `PrismalLiquidGlass.applyBase()` first to get the full optical recipe, then overrides with values scaled for a small (~52dp) circle.
- Critical override: `setThickness(dp(5f))` - the base sets 18dp which exceeds the half-height of a 52dp circle, collapsing the entire shape into the edge ramp (looks like a thick border).
- Press animation uses `ValueAnimator` to scale and pulse `normalStrength` (`8f * strength` on press) for the "glass activating" visual.

---

## 20. Key Parameter Reference

| Parameter | Uniform / Field | What it controls |
|---|---|---|
| `u_ior` | IOR | Snell deflection angle - higher = stronger bend. 1.3 (water) to 1.9 (heavy glass) |
| `u_heightTransitionWidth` | `setThickness()` | SDF ramp width in pixels. **Must be < ~40% of `minDim`** or entire shape is a border |
| `u_heightBlurFactor` | `setHeightBlurFactor()` | Depth-of-field blur gradient width. Proportional to view size (~25%) |
| `u_normalStrength` | `setNormalStrength()` | XY normal tilt scale - higher = steeper, sharper glass |
| `u_displacementScale` | `setDisplacementScale()` | Final UV offset multiplier applied to all refraction contributions |
| `u_liquidDome` | `setLiquidDomeStrength()` | 0 = flat slab, 1 = spherical sessile droplet |
| `u_fresnelReflect` | `setFresnelReflectStrength()` | Multiplier on Schlick F used for rim/parallax (0–2) |
| `u_rimStrength` | `setRimStrength()` | Master scale for all rim/border highlight contributions |
| `u_lensRefractionPx` | computed | Pixel-space lens displacement magnitude (auto-computed from size+IOR+thickness) |
| `u_lensRefractionScale` | `setLensRefractionScale()` | User scale on the computed lens px value |
| `u_blurRadius` | `setBlurRadius()` | Gaussian sigma for frosted blur pass |
| `u_chromaticAberration` | `setChromaticAberration()` | RGB channel split in pixels; 0 disables entirely |
| `u_specular` / `u_shininess` | `setSpecular()` | Blinn-Phong intensity and glossiness exponent |
| `u_causticIntensity` | `setCausticIntensity()` | Strength of focused-light caustic inner glow |
| `u_transmittance` | `setTransmittance()` | Final alpha multiplier (0 = invisible, 1 = opaque glass) |
| `u_shadowSoftness` | `setShadowProperties()` | ≤1 = hard shadow, >1 = soft (value ÷ 20 for wide spread) |

---

## 21. Critical Sizing Rule

The most common mistake when applying Prismal to small controls is setting `thickness` (i.e. `u_heightTransitionWidth`) to a value appropriate for a large card and not scaling it down.

**The shader computes:**

```glsl
float tw = max(u_heightTransitionWidth * (1.0 + 0.38 * dome) + minDim * 0.085, 1.0);
```

If `tw ≥ minDim` (the half-height of the shape), the entire shape is inside the edge ramp - every pixel reads as "near the silhouette edge" and the glass looks like a uniform glowing ring, not a glass object.

**Rule of thumb:**

```
u_heightTransitionWidth  ≤  0.35 × (min(width, height) / 2)
```

| View size | Max sensible thickness |
|---|---|
| 24dp thumb (slider/switch) | ≈ 4dp |
| 52dp icon button | ≈ 5–6dp |
| 100dp card | ≈ 12–16dp |
| 200dp hero card | ≈ 20–28dp |

`PrismalLiquidGlass.applyBase()` uses 18dp - correct for large cards, but it must be overridden for any view smaller than ~100dp.

---

*Document generated from the Prismal source - Saurav Sajeev, 2026.*