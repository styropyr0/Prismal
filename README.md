# Prismal - Liquid Glass for Android

> **Real-time liquid glass rendering library for Android**  
> OpenGL ES 2.0 library delivering physically accurate refraction, blur, chromatic aberration, Fresnel, specular, and spring-physics animations for Android UI components.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![OpenGL ES](https://img.shields.io/badge/OpenGL%20ES-2.0-red.svg)](https://www.khronos.org/opengles/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-25-orange.svg)](https://developer.android.com/about/versions/nougat)

---

## Overview

Prismal renders an iOS-style liquid glass material on Android. Each component captures the view hierarchy behind it into a GPU texture, then applies a physically derived rendering pipeline: signed-distance-field shape, circular-arc height profile, spherical meniscus normals, Snell's-law double refraction, two-pass Gaussian blur, dual Blinn-Phong specular, Fresnel rim highlights on both the lit and opposite borders, caustics, and a spring-physics animation system.

### Key Features

- **Physically based rendering** - Snell's law double refraction, Schlick Fresnel, dual Blinn-Phong specular, spherical meniscus edge profile
- **Circular-arc height field** - `√(2t − t²)` cross-section guarantees zero thickness at the silhouette; no flat linear edges
- **Dual border rim highlights** - both the directly lit rim and the opposite rim glow simultaneously, matching real polished glass
- **Two-pass Gaussian blur** - separable horizontal + vertical passes for efficient frosted-glass depth
- **Spring physics** - `Choreographer`-driven damped harmonic oscillator replaces `ValueAnimator` for press, travel, and click feedback
- **Animated glass cards** - `setOnClickWithAnimationListener()` adds spring press-scale and radial glow without conflicting with `setOnClickListener` or child controls
- **Optional shared scene** - `pfl_sharedHierarchicalCapture` / `PrismalScene` for one root capture + one blur pass across multiple glass views (default: independent per view)
- **Canonical material preset** - `PrismalLiquidGlass.applyBase()` applies the full calibrated optical recipe in one call
- **Pre-built components** - `PrismalFrameLayout`, `PrismalIconButton`, `PrismalSwitch`, `PrismalSlider`, `PrismalButton`
- **Fully customizable** - extensive XML attributes and runtime Kotlin API

---

## Installation

### Gradle

Add the JitPack repository to your root `build.gradle`:

```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency:

```gradle
dependencies {
    implementation 'com.github.styropyr0:Prismal:v1.0.0'
}
```

### Requirements

| Item | Requirement |
|------|-------------|
| Min SDK | 25 (Android 7, Nougat) |
| Target SDK | 36 |
| OpenGL ES | 2.0+ |
| Kotlin | 2.0+ |

---

## Quick Start

```xml
<com.matrix.prismal.PrismalFrameLayout
    android:layout_width="match_parent"
    android:layout_height="120dp"
    app:pfl_cornerRadius="24dp"
    app:pfl_blurRadius="3">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="Liquid glass"
        android:textColor="#FFFFFF" />

</com.matrix.prismal.PrismalFrameLayout>
```

```kotlin
val glassCard = findViewById<PrismalFrameLayout>(R.id.glassCard)

// Apply the built-in calibrated material recipe
PrismalLiquidGlass.applyBase(glassCard)

// Override individual parameters as needed
glassCard.setIOR(1.65f)
glassCard.setBlurRadius(4f)

// Refresh after the background changes
glassCard.updateBackground()

// Optional: iOS-style press animation + glow (separate from setOnClickListener)
glassCard.setOnClickWithAnimationListener {
    // handle tap
}
```

---

## Components

### PrismalFrameLayout

Base container. Renders the glass material over its children using an embedded `GLSurfaceView`. All other Prismal components are built on top of this class.

**How it works:** `updateBackground()` draws the view hierarchy beneath this layout into a `Bitmap`, uploads it to the GPU, and triggers a new render. The GLSL fragment shader then applies the full optical pipeline.

#### Shared hierarchical capture (opt-in)

By default each `PrismalFrameLayout` owns its own backdrop capture and blur. For screens with **many glass panels** (notification stacks, settings lists), enable shared capture so the window root is sampled once:

```
Root scene → one backdrop texture → one blur pass → each member draws its glass quad
```

```xml
<com.matrix.prismal.PrismalFrameLayout
    app:pfl_sharedHierarchicalCapture="true"
    ... />
```

```kotlin
// Runtime opt-in (set before attach when possible, or via XML)
glassRow.setSharedHierarchicalCapture(true)

// Optional explicit scene handle (same root groups siblings)
val root = findViewById<ViewGroup>(android.R.id.content)
PrismalScene.getOrCreate(root)
```

| Mode | Capture | Blur | GL surfaces |
|------|---------|------|-------------|
| Default | Per view | Per view | One per `PrismalFrameLayout` |
| Shared | Once per root update | Once (max member blur radius) | One hidden master + one per member (shared EGL context) |

**Notes**

- Does not apply when [setCaptureHost] is set (switch/slider thumbs keep local capture).
- Each member still renders in its own `GLSurfaceView` so children stay **above** the glass (correct z-order).
- Call `updateBackground()` on any member — or scroll/layout — to refresh the shared backdrop for all members.

#### Interactive click

Use **`setOnClickWithAnimationListener`** for tappable glass cards (toolbars, notification rows, hero panels). It is intentionally separate from Android’s **`setOnClickListener`**:

| API | Behavior |
|-----|----------|
| `setOnClickWithAnimationListener` | Spring press-scale (default → 0.96), radial touch glow, then fires your callback on release |
| `setOnClickListener` | Standard Android click; optional glow only when a listener is registered |

Subclasses such as `PrismalSwitch` and `PrismalSlider` manage their own touch handling on the thumb - they are unaffected unless you call the animated API on them directly. `PrismalIconButton` uses its own press optics and glow via an internal `PrismalFrameLayout`.

```kotlin
glassCard.setOnClickWithAnimationListener {
    startActivity(Intent(this, DetailActivity::class.java))
}

// Tune press depth (0.5 – 1.0; 1.0 = no shrink)
glassCard.setClickAnimationPressScale(0.94f)

// Java
glassCard.setOnClickWithAnimationListener(OnClickListener { v -> /* … */ })
```

#### XML Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `pfl_glassWidth` | float | Override rendered surface width (px) |
| `pfl_glassHeight` | float | Override rendered surface height (px) |
| `pfl_cornerRadius` | dimension | Corner radius (px) |
| `pfl_glassThickness` | dimension | Edge ramp width - see sizing rule below |
| `pfl_ior` | float | Index of Refraction (1.0 – 2.0, default 1.5) |
| `pfl_normalStrength` | float | Surface normal influence (default 1.2) |
| `pfl_displacementScale` | float | Lens distortion intensity (default 1.0) |
| `pfl_heightTransitionWidth` | float | Height field ramp width (deprecated - use `pfl_glassThickness`) |
| `pfl_minSmoothing` | float | SDF edge smoothing (default 1.0) |
| `pfl_blurRadius` | float | Background blur radius in dp (default 2.5) |
| `pfl_chromaticAberration` | float | RGB channel split in px (default 2.0) |
| `pfl_brightness` | float | Overall brightness multiplier (default 1.15) |
| `pfl_specular` | float | Specular highlight intensity |
| `pfl_shininess` | float | Specular exponent (Blinn-Phong) |
| `pfl_rimStrength` | float | Rim / border highlight intensity |
| `pfl_highlightWidth` | float | Top-surface highlight band width |
| `pfl_causticIntensity` | float | Caustic light concentration intensity |
| `pfl_liquidDome` | float | Dome curvature strength (0 – 1) |
| `pfl_fresnelReflect` | float | Fresnel reflectivity boost at grazing angles |
| `pfl_lensRefractionScale` | float | Lens distortion scale factor |
| `pfl_lightDirX` | float | Light direction X component |
| `pfl_lightDirY` | float | Light direction Y component |
| `pfl_shadowSoftness` | float | Drop shadow blur extent (0 – 1) |
| `pfl_transmittance` | float | Glass transmittance (opacity of refracted background) |
| `pfl_showNormals` | boolean | Debug: visualize surface normals as RGB |
| `pfl_sharedHierarchicalCapture` | boolean | Opt into [PrismalScene] shared root capture (default `false`) |

#### API

```kotlin
// Shape
setGlassSize(width: Float, height: Float)
setCornerRadius(radius: Float)
setRefractionInset(value: Float)

// Optics
setIOR(value: Float)
setThickness(value: Float)               // edge ramp width in px - keep < ~40% of min(w,h)/2
setNormalStrength(value: Float)
setDisplacementScale(value: Float)
setMinSmoothing(value: Float)
setLiquidDomeStrength(value: Float)
setFresnelReflectStrength(value: Float)
setLensRefractionScale(value: Float)

// Blur & chromatic
setBlurRadius(value: Float)
setChromaticAberration(value: Float)
setHeightBlurFactor(value: Float)

// Lighting
setLightDirection(x: Float, y: Float)
setSpecular(strength: Float, shininess: Float)
setRimStrength(value: Float)
setHighlightWidth(value: Float)
setCausticIntensity(value: Float)

// Color
setBrightness(value: Float)
setGlassColor(color: Int)
setTransmittance(value: Float)

// Shadow
setShadowProperties(color: Int, softness: Float)

// Debug
setShowNormals(show: Boolean)
setEdgeRefractionFalloff(value: Float)

// Capture (advanced - used by switch/slider thumbs for aligned backdrop)
setCaptureHost(host: ViewGroup?)

// Click
setOnClickWithAnimationListener(listener: (() -> Unit)?)
setOnClickWithAnimationListener(listener: OnClickListener?)
setClickAnimationPressScale(scale: Float)

// Shared scene (opt-in)
setSharedHierarchicalCapture(enabled: Boolean)
isSharedHierarchicalCapture(): Boolean

// Update
updateBackground()
```

See also [PrismalScene] for root-level scene management.

#### Critical Sizing Rule

`thickness` must be less than roughly **40% of `min(width, height) / 2`**. If it exceeds that, the entire shape falls within the edge ramp and renders as a hollow glowing ring. Recommended values by view size:

| View size | Thickness |
|-----------|-----------|
| Large card (≥ 120 dp) | 18 dp (library default) |
| Medium card (60 – 120 dp) | 8 – 12 dp |
| Switch / slider thumb (24 dp) | 4 dp |
| Icon button (52 – 56 dp) | 5 dp |

---

### PrismalScene

Coordinates optional shared capture for a window root. Created automatically when any child calls `setSharedHierarchicalCapture(true)`; you can also warm it up explicitly:

```kotlin
PrismalScene.getOrCreate(activity.findViewById(android.R.id.content))
```

All opted-in `PrismalFrameLayout` instances under the same root share one hierarchical bitmap upload and one Gaussian blur. Each view still applies its own optical parameters via [GlassRenderState].

---

### PrismalLiquidGlass

A singleton that holds the calibrated optical recipe for the liquid glass material. Call `applyBase(view)` to apply the full set of parameters to any `PrismalFrameLayout` in one step - IOR, thickness, specular, rim, caustic, dispersion, lighting, shadow, and transmittance.

```kotlin
// Apply the full recipe (recommended starting point for any glass surface)
PrismalLiquidGlass.applyBase(myGlassView)

// Then override individual params for your specific component size
myGlassView.setThickness(dp(5f))    // scale down for small views
myGlassView.setIOR(1.65f)
```

The base recipe is calibrated for large cards (≥ 120 dp). When applying to smaller components, override `thickness` and `heightBlurFactor` proportionally.

---

### PrismalIconButton

Circular glass button for toolbar actions and compact controls. Sizes to `wrap_content` or layout dimensions; glass thickness, blur, and refraction scale with the measured diameter. Applies `PrismalLiquidGlass.applyBase()` internally, then overrides parameters per size on `onSizeChanged`.

Press feedback uses two springs: scale (slight overshoot on release) and optics (blur → 0, chromatic aberration → 3.5 px, lens scale up while held).

#### XML Example

```xml
<com.matrix.prismal.PrismalIconButton
    android:id="@+id/playButton"
    android:layout_width="48dp"
    android:layout_height="48dp"
    app:pib_iconSrc="@drawable/ic_play"
    app:pib_iconPadding="12dp"
    app:pib_iconTint="#FFFFFF"
    app:pib_ior="1.55"
    app:pib_blurRadius="2.5"
    app:pib_pressScale="0.82" />
```

#### XML Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `pib_iconSrc` | reference | - | Icon drawable resource |
| `pib_iconPadding` | dimension | 8 dp | Padding inside the glass surface |
| `pib_iconTint` | color | black | Icon tint color |
| `pib_buttonSize` | dimension | - | Hint for minimum size when using `wrap_content` |
| `pib_pressScale` | float | 0.82 | Scale factor at full press |
| `pib_ior` | float | 1.55 | Index of Refraction |
| `pib_blurRadius` | float | 2.0 | Resting background blur in dp |
| `pib_normalStrength` | float | 1.0 | Surface normal influence |
| `pib_displacementScale` | float | 0.9 | Lens distortion intensity |
| `pib_chromaticAberration` | float | 0.0 | RGB split at rest (animated on press) |
| `pib_brightness` | float | 1.12 | Brightness multiplier |
| `pib_highlightWidth` | float | 1.2 | Top-surface highlight band |
| `pib_liquidDomeStrength` | float | 0.72 | Dome curvature |
| `pib_fresnelReflectStrength` | float | 1.3 | Fresnel reflect boost |
| `pib_lensRefractionScale` | float | 0.55 | Lens distortion at rest |
| `pib_shadowColor` | color | `#22000000` | Drop shadow colour |
| `pib_shadowSoftness` | float | 0.18 | Drop shadow blur |
| `pib_showNormals` | boolean | false | Debug: show surface normals |

#### API

```kotlin
setIcon(resId: Int)
setIOR(value: Float)
setBlurRadius(value: Float)
setChromaticAberration(value: Float)
setDisplacementScale(value: Float)
setOnClickListener(l: OnClickListener?)
updateBackground()
```

---

### PrismalSwitch

iOS-style toggle switch: 64 × 28 dp capsule track that colour-crossfades from grey to green, with a 40 × 24 dp capsule glass thumb. The thumb is frosted-white at rest and reveals the live glass material on press. Spring physics drive position, press scale, and velocity squish.

Thumb travel is exactly `trackWidth − thumbWidth − 2 × padding` = **20 dp** at default size.

#### XML Example

```xml
<com.matrix.prismal.PrismalSwitch
    android:id="@+id/mySwitch"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:psw_isOn="false"
    app:psw_trackHeight="31dp" />
```

#### XML Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `psw_isOn` | boolean | false | Initial toggle state |
| `psw_trackHeight` | dimension | 28 dp | Track height; thumb and travel scale proportionally |
| `psw_onColor` / `psw_offColor` | color | green / grey | Track colours |
| `psw_thumbCornerRadius` | dimension | auto | Thumb corner radius |
| `psw_thumbIOR` | float | calibrated | Thumb index of refraction |
| `psw_thumbBlurRadius` | float | calibrated | Thumb blur |
| `psw_thumbNormalStrength` | float | calibrated | Thumb normal strength |
| `psw_thumbDisplacementScale` | float | calibrated | Thumb lens scale |
| `psw_thumbChromaticAberration` | float | 0 | Thumb RGB split |
| `psw_thumbBrightness` | float | calibrated | Thumb brightness |
| `psw_thumbThickness` | dimension | calibrated | Thumb edge ramp |
| `psw_thumbHighlightWidth` | float | calibrated | Thumb highlight band |
| `psw_thumbHeightBlurFactor` | float | calibrated | Height-field blur factor |
| `psw_thumbMinSmoothing` | float | calibrated | SDF smoothing |
| `psw_thumbRefractionInset` | float | calibrated | Refraction inset |
| `psw_thumbEdgeRefractionFalloff` | float | calibrated | Edge refraction falloff |
| `psw_thumbShadowColor` / `psw_thumbShadowSoftness` | color / float | - | Thumb shadow |
| `psw_thumbShowNormals` | boolean | false | Debug normals on thumb |

#### API

```kotlin
setOn(on: Boolean, animate: Boolean = false)
isOn(): Boolean
toggle(animate: Boolean = true)
setOnToggleChangedListener(l: (Boolean) -> Unit)
updateBackground()

// Thumb optical overrides
setThumbIOR(value: Float)
setThumbBlurRadius(value: Float)
setThumbBrightness(value: Float)
setThumbNormalStrength(value: Float)
setThumbDisplacementScale(value: Float)
setThumbThickness(value: Float)
setThumbChromaticAberration(value: Float)
setThumbCornerRadius(value: Float)
setThumbHighlightWidth(value: Float)
setThumbShadow(color: Int, radius: Float)
setThumbHeightBlurFactor(value: Float)
setThumbRefractionInset(value: Float)
setThumbEdgeRefractionFalloff(value: Float)
```

---

### PrismalSlider

Horizontal slider: 6 dp capsule track with an accent-coloured fill, and a 40 × 24 dp capsule glass thumb. The thumb reveals the live glass material on press with velocity-based squish deformation. Calls `thumb.updateBackground()` on every `ACTION_MOVE` so the refracted background updates live as the thumb moves.

#### XML Example

```xml
<com.matrix.prismal.PrismalSlider
    android:id="@+id/volumeSlider"
    android:layout_width="match_parent"
    android:layout_height="44dp"
    app:psl_maxValue="100"
    app:psl_trackColor="#0088FF" />
```

#### XML Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `psl_maxValue` | float | 100 | Maximum slider value |
| `psl_trackColor` | color | #0088FF | Accent fill colour |
| `psl_thumbCornerRadius` | dimension | auto | Thumb corner radius |
| `psl_thumbIOR` | float | calibrated | Thumb index of refraction |
| `psl_thumbBlurRadius` | float | calibrated | Thumb blur |
| `psl_thumbNormalStrength` | float | calibrated | Thumb normal strength |
| `psl_thumbDisplacementScale` | float | calibrated | Thumb lens scale |
| `psl_thumbChromaticAberration` | float | 0 | Thumb RGB split |
| `psl_thumbBrightness` | float | calibrated | Thumb brightness |
| `psl_thumbThickness` | dimension | calibrated | Thumb edge ramp |
| `psl_thumbHighlightWidth` | float | calibrated | Thumb highlight band |
| `psl_thumbHeightBlurFactor` | float | calibrated | Height-field blur factor |
| `psl_thumbMinSmoothing` | float | calibrated | SDF smoothing |
| `psl_thumbRefractionInset` | float | calibrated | Refraction inset |
| `psl_thumbEdgeRefractionFalloff` | float | calibrated | Edge refraction falloff |
| `psl_thumbParallaxScale` | float | calibrated | Backdrop parallax under thumb |
| `psl_thumbShadowColor` / `psl_thumbShadowSoftness` | color / float | - | Thumb shadow |
| `psl_thumbShowNormals` | boolean | false | Debug normals on thumb |

#### API

```kotlin
setValue(value: Float)
getValue(): Float
setMaxValue(value: Float)
setOnValueChangedListener(l: (Float) -> Unit)
updateBackground()
getThumb(): PrismalFrameLayout

// Thumb optical overrides
setThumbIOR(value: Float)
setThumbBlurRadius(value: Float)
setThumbBrightness(value: Float)
setThumbNormalStrength(value: Float)
setThumbDisplacementScale(value: Float)
setThumbThickness(value: Float)
setThumbChromaticAberration(value: Float)
setThumbCornerRadius(value: Float)
setThumbShadow(color: Int, radius: Float)
setThumbHeightBlurFactor(value: Float)
setThumbRefractionInset(value: Float)
```

---

### PrismalButton

General-purpose pressable glass container. Renders the glass material over any child views with a scale animation on press.

#### XML Example

```xml
<com.matrix.prismal.PrismalButton
    android:layout_width="200dp"
    android:layout_height="60dp"
    app:pbtn_ior="1.55"
    app:pbtn_blurRadius="2"
    app:pbtn_cornerRadius="32dp">

    <TextView
        android:text="Press me"
        android:textColor="#FFFFFF"
        android:layout_gravity="center" />

</com.matrix.prismal.PrismalButton>
```

#### XML Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `pbtn_ior` | float | Index of Refraction |
| `pbtn_normalStrength` | float | Surface normal influence |
| `pbtn_displacementScale` | float | Lens distortion intensity |
| `pbtn_blurRadius` | float | Background blur in dp |
| `pbtn_chromaticAberration` | float | RGB split in px |
| `pbtn_cornerRadius` | dimension | Corner radius |
| `pbtn_highlightWidth` | float | Top-surface highlight band |
| `pbtn_brightness` | float | Brightness multiplier |
| `pbtn_showNormals` | boolean | Debug: show surface normals |

---

## Rendering Architecture

### Pipeline

```
updateBackground()
    │
    ├─ Draw view hierarchy → Bitmap → GL texture (bgTex)
    │
    ├─ H-pass Gaussian blur: bgTex → blurFbo1/blurTex1
    ├─ V-pass Gaussian blur: blurTex1 → blurFbo2/blurTex2   (frosted tex)
    │
    └─ Fragment shader per pixel:
           SDF shape + height field
           → surface normals (finite differences + meniscus blend)
           → Snell's law double refraction offset
           → chromatic aberration (per-channel offsets)
           → sample blurTex2 at refracted UV  (frosted background)
           → dual Blinn-Phong specular (key + fill)
           → Fresnel rim highlights (lit border + opposite border)
           → caustic overlay
           → shadow
           → composite
```

### Height Field - Circular Arc

The glass thickness cross-section follows a quarter-circle profile - zero at the silhouette, steep initial rise, flattening toward the centre. This matches the profile of a water droplet or polished glass lens:

```glsl
float getHeightFromDist(float dist, float tw) {
    float t = clamp(-dist / tw, 0.0, 1.0);
    return sqrt(max(0.0, 2.0 * t - t * t));
}
```

The formula `√(2t − t²)` traces a quarter-circle: `h(t=0) = 0` at the silhouette, `h(t=1) = 1` at the centre.

### Spherical Meniscus Normal Blending

Surface normals at the rim zone are blended toward a spherically curved meniscus normal, tilted outward and downward along the circular cross-section. This makes the silhouette read as a curved glass edge rather than a flat vertical wall:

```glsl
float menW     = clamp(edgeDist / tw, 0.0, 1.0);
float menCirc  = sqrt(max(0.0, 1.0 - menW * menW));
vec3 N_meniscus = normalize(vec3(-outward * menCirc * 0.95, 0.26 + 0.74 * menW));
float menBlend  = smoothstep(tw * 0.5, 0.0, edgeDist)
                * smoothstep(-6.0, 0.0, distMask) * 0.82;
N = normalize(mix(N, N_meniscus, menBlend));
```

### Dual Border Rim Highlights

Two rim terms illuminate both the lit border and the opposite border simultaneously. A real glass slab lit from one side shows a highlight on the far edge due to total internal reflection - the `abs()` in `borderAlign` reproduces this:

```glsl
float borderAlign = pow(abs(dot(gN, Lxy)), 1.0);    // both borders
float litAlign    = pow(max(0.0, dot(gN, Lxy)), 1.3); // lit border only

float rimBothBorders = borderAlign * shellRim * u_rimStrength * 0.26;
float rimLitSide     = litAlign    * shellRim * u_rimStrength * 0.32;
```

### Snell's Law Double Refraction

Light passes through two interfaces (air → glass → air):

```glsl
vec3 refIn  = refract(-V, N, 1.0 / u_ior);
vec3 refOut = refract(refIn, -N, u_ior);
vec2 refractionOffset = refOut.xy * u_glassThickness * strength;
```

### Fresnel (Schlick)

Angle-dependent reflectivity, artificially boosted near the silhouette for flat panels:

```glsl
float r0 = pow((u_ior - 1.0) / (u_ior + 1.0), 2.0);
float fresnelTerm = r0 + (1.0 - r0) * pow(1.0 - cosVNeff, 5.0);
```

### Shader Uniforms Reference

| Uniform | Range | Description |
|---------|-------|-------------|
| `u_ior` | 1.0 – 2.0 | Index of Refraction |
| `u_glassThickness` | 1 – 100 px | Edge ramp width (see sizing rule) |
| `u_normalStrength` | 0 – 20 | Surface normal influence |
| `u_displacementScale` | 0.1 – 10 | Lens distortion multiplier |
| `u_blurRadius` | 0 – 20 dp | Background blur |
| `u_chromaticAberration` | 0 – 20 px | RGB channel split |
| `u_brightness` | 0.5 – 2.0 | Output brightness multiplier |
| `u_rimStrength` | 0 – 3 | Rim highlight intensity |
| `u_specularStrength` | 0 – 3 | Specular highlight intensity |
| `u_shininess` | 8 – 256 | Blinn-Phong specular exponent |
| `u_causticIntensity` | 0 – 1 | Caustic overlay strength |
| `u_liquidDomeStrength` | 0 – 1 | Dome curvature amount |
| `u_fresnelReflectStrength` | 0 – 3 | Fresnel boost factor |
| `u_lensRefractionScale` | 0 – 2 | Lens distortion scale |
| `u_cornerRadius` | 0 – ∞ px | Shape corner radius |
| `u_shadowColor` | RGBA | Drop shadow colour |
| `u_shadowSoftness` | 0 – 1 | Drop shadow blur extent |

---

## Spring Physics

Interactive components use `SpringAnimator` - a `Choreographer`-driven damped harmonic oscillator - instead of `ValueAnimator`. This includes slider/switch thumb travel, `PrismalIconButton` press optics, and `PrismalFrameLayout.setOnClickWithAnimationListener` press-scale.

**Model:** `F = −k(x − target) − d·v`  
**Damping coefficient:** `d = 2 · ζ · √k` (derived analytically from `dampingRatio` and `stiffness`)  
**Frame delta cap:** 48 ms - prevents large jumps after the screen turns off.

```kotlin
// Example: critically damped spring (ζ = 1.0) with k = 1000
val spring = SpringAnimator(dampingRatio = 1.0f, stiffness = 1000f)
spring.onUpdate = { value -> myView.translationX = value }
spring.animateTo(targetPx)   // smooth spring transition
spring.snapTo(valuePx)       // instant jump, no animation
spring.cancel()              // remove pending FrameCallback
```

**Velocity squish:** During a drag gesture, the thumb deforms based on instantaneous normalised velocity:

```kotlin
val sx = scaleXSpring.value / (1f - normVel * 0.75f)   // elongates along drag direction
val sy = scaleYSpring.value * (1f - abs(normVel) * 0.25f)  // compresses perpendicular
```

---

## Design Guidelines

### Parameter Ranges

| Parameter | Subtle | Calibrated | Dramatic |
|-----------|--------|------------|----------|
| IOR | 1.3 – 1.4 | 1.5 – 1.6 | 1.7 – 2.0 |
| Blur radius | 1 – 2 dp | 2 – 4 dp | 5 – 10 dp |
| Normal strength | 0.5 – 1.0 | 1.0 – 1.5 | 2.0 – 5.0 |
| Chromatic aberration | 0 | 1 – 3 px | 5 – 10 px |
| Brightness | 1.0 – 1.05 | 1.08 – 1.15 | 1.2 – 1.5 |
| Rim strength | 0.5 – 1.0 | 1.2 – 1.8 | 2.0 – 3.0 |
| Specular | 0.5 – 1.0 | 1.2 – 1.8 | 2.0 – 3.0 |

### Best Practices

- Use `PrismalLiquidGlass.applyBase()` as the starting point for any glass surface, then tune from there
- For tappable cards, prefer `setOnClickWithAnimationListener` over wiring scale animation yourself
- Apply `clipChildren = false` on the parent of any glass component with a press-scale animation so the scaled view is not clipped
- Call `updateBackground()` after the content behind a glass component changes (scroll, layout change, content update)
- Keep `thickness` well below 40% of the view's half-height or the shape will render as a border ring
- Chromatic aberration at rest looks artificial; consider keeping it at 0 and animating it to 4 – 6 px on press only

---

## Sample app

The `app` module demonstrates every component on a wallpaper background:

| Screen | Purpose |
|--------|---------|
| `MainActivity` | Switch, sliders, icon buttons, notification-style glass rows; tap the center glass card to open the playground |
| `GlassPlaygroundActivity` | Live sliders for every `PrismalFrameLayout` optical parameter; prefs sync back to home via `GlassPlaygroundPrefs` |
| `DragShowActivity` | Draggable glass panel over a custom or picked background |

---

## Troubleshooting

**Glass has no visible effect**
- Ensure there is content behind the `PrismalFrameLayout` in the view hierarchy
- Call `updateBackground()` after `onResume()` or after the first layout pass
- Increase `normalStrength` or `displacementScale` to make the distortion more visible

**Shape looks like a glowing border ring instead of a glass panel**
- `thickness` is too large for the view's size - reduce it; see the sizing rule above
- For a 24 dp thumb, use `dp(4f)`; for a 56 dp button, use `dp(5f)`; for a large card, use `dp(18f)`

**Thumb clipped during press-scale animation**
- The parent layout has `clipChildren = true` by default
- Both `PrismalSlider` and `PrismalSwitch` set `parent.clipChildren = false` in `onAttachedToWindow()`, but if you embed them inside a custom container, ensure that container also has `clipChildren = false`
- The same applies to `PrismalFrameLayout` cards using `setOnClickWithAnimationListener` - set `clipChildren="false"` on the parent `ViewGroup`

**Refracted texture is stale during drag**
- `thumb.updateBackground()` must be called on every `ACTION_MOVE` event
- `PrismalSlider` does this internally; if you implement a custom draggable glass view, add the call in your move handler

**Glass texture not updating after scroll**
- Wire `updateBackground()` to the scroll listener of the parent `NestedScrollView` / `RecyclerView`

**Debug: surface looks wrong**
```kotlin
glassView.setShowNormals(true)   // visualize surface normals as RGB
```
The rendered colours map to surface normal direction. Flat blue means the height field is not being computed - check that `thickness` is not zero and the view has non-zero size.

---

## Contributing

1. Fork the repository
2. Create a feature branch from `master`
3. Follow Kotlin coding conventions
4. Update `CHANGELOG.md` and, if relevant, `TECHNICAL.md`
5. Submit a pull request with a clear description of the change and its motivation

---

## Resources

- **Technical reference**: [TECHNICAL.md](TECHNICAL.md) - rendering math, SDF derivation, spring physics formulas, component architecture
- **Changelog**: [CHANGELOG.md](CHANGELOG.md)
- **Issues**: [github.com/styropyr0/Prismal/issues](https://github.com/styropyr0/Prismal/issues)

---

## License

MIT License - see [LICENSE](LICENSE) for details.
