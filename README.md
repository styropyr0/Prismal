# Prismal - Glassmorphism for Android (BETA)

> **Real-time glassmorphism rendering library for Android for Android XML Components**  
> High-performance OpenGL ES 2.0 library delivering physically accurate glass refraction, blur, and chromatic aberration effects for Android UI components.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![OpenGL ES](https://img.shields.io/badge/OpenGL%20ES-2.0-red.svg)](https://www.khronos.org/opengles/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-21-orange.svg)](https://developer.android.com/about/versions/lollipop)
---

## Overview

Prismal is an Android library that provides real-time glassmorphism effects through custom OpenGL ES 2.0 shaders. It captures content behind UI elements and applies optical distortions including refraction, blur, and chromatic aberration to simulate realistic glass materials.

### Key Features

- **Real-time Glass Rendering** - GPU-accelerated shader-based effects with live background capture
- **Physically Based Refraction** - Accurate light bending using Index of Refraction (IOR), Fresnel effects, and double refraction
- **Chromatic Aberration** - Realistic RGB color separation at glass edges
- **Interactive Components** - Touch-responsive animations and dynamic distortions
- **Pre-built Components** - Ready-to-use buttons, switches, sliders, and containers
- **Performance Optimized** - Efficient rendering with minimal overdraw and smart texture caching
- **Fully Customizable** - Extensive XML attributes and runtime APIs

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
    implementation 'com.github.styropyr0:prismal:1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.yourusername</groupId>
    <artifactId>prismal</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Requirements

- **Min SDK**: 25 (Android 7, Nougat)
- **Target SDK**: 36
- **OpenGL ES**: 2.0+
- **Kotlin**: 2.0.21

---

## Quick Start

### Basic Usage

Add Prismal components to your layout:

```xml
<com.matrix.prismal.PrismalFrameLayout
    android:layout_width="300dp"
    android:layout_height="200dp"
    app:ior="1.5"
    app:blurRadius="3"
    app:cornerRadius="20dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Glass Container"
        android:textColor="@android:color/white"
        android:layout_gravity="center" />

</com.matrix.prismal.PrismalFrameLayout>
```

### Programmatic Configuration

```kotlin
val glassLayout = findViewById<PrismalFrameLayout>(R.id.glassContainer)

glassLayout.apply {
    setIOR(1.5f)
    setCornerRadius(25f)
    setThickness(15f)
    setBlurRadius(4f)
    setBrightness(1.2f)
    setChromaticAberration(2f)
}
```

---

## Components

### PrismalFrameLayout

Base container that renders glass effects. Acts as a standard `FrameLayout` with an OpenGL-rendered glass surface beneath its children.
> Note that, all of the other Prismal views are subclasses of PrismalFrameLayout. PrismalFrameLayout handles most of the works within, such that it would be easier for users to make their own subclasses. All you need to do is inherit from PrismalFrameLayout, set up renderer (PrismalGlassRenderer), and implement methods for changing properties.

#### XML Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `app:glassWidth` | float | view width | Glass surface width |
| `app:glassHeight` | float | view height | Glass surface height |
| `app:ior` | float | 1.5 | Index of Refraction (1.0-2.0) |
| `app:glassThickness` | dimension | 15dp | Glass thickness affecting distortion |
| `app:normalStrength` | float | 1.2 | Surface normal influence |
| `app:displacementScale` | float | 1.0 | Displacement mapping intensity |
| `app:heightTransitionWidth` | float | 8.0 | Height field transition width |
| `app:minSmoothing` | float | 1.0 | SDF smoothing threshold |
| `app:blurRadius` | float | 2.5 | Background blur radius |
| `app:highlightWidth` | float | 4.0 | Edge highlight width |
| `app:chromaticAberration` | float | 2.0 | RGB color split intensity |
| `app:brightness` | float | 1.15 | Overall brightness multiplier |
| `app:cornerRadius` | dimension | 10dp | Corner rounding |
| `app:showNormals` | boolean | false | Debug: visualize surface normals |

#### API Methods

```kotlin
setRefractionInset(value: Float)
setGlassSize(width: Float, height: Float)
setCornerRadius(radius: Float)
setIOR(value: Float)
setThickness(value: Float)
setNormalStrength(value: Float)
setDisplacementScale(value: Float)
setHeightBlurFactor(value: Float)
setMinSmoothing(value: Float)
setBlurRadius(value: Float)
setHighlightWidth(value: Float)
setChromaticAberration(value: Float)
setBrightness(value: Float)
setShowNormals(show: Boolean)
setShadowProperties(color: Int, softness: Float)
setEdgeRefractionFalloff(value: Float)
updateBackground()
```

---

### PrismalButton

Pressable glass button with scale animations and interactive refraction effects.

#### XML Example

```xml
<com.matrix.prismal.PrismalButton
    android:id="@+id/glassButton"
    android:layout_width="200dp"
    android:layout_height="60dp"
    app:ior="1.85"
    app:normalStrength="8"
    app:blurRadius="1"
    app:cornerRadius="32dp">

    <TextView
        android:text="Press Me"
        android:textColor="#FFFFFF"
        android:layout_gravity="center" />

</com.matrix.prismal.PrismalButton>
```

#### Attributes

- `app:ior` (float, default: 1.85)
- `app:normalStrength` (float, default: 8)
- `app:displacementScale` (float, default: 10)
- `app:blurRadius` (float, default: 1)
- `app:chromaticAberration` (float, default: 8)
- `app:cornerRadius` (dimension, default: 32dp)
- `app:brightness` (float, default: 1.0)
- `app:highlightWidth` (float, default: 4)
- `app:showNormals` (boolean, default: false)

#### API

```kotlin
setIOR(value: Float)
setNormalStrength(value: Float)
setDisplacementScale(value: Float)
setBlurRadius(value: Float)
setChromaticAberration(value: Float)
setCornerRadius(value: Float)
setBrightness(value: Float)
setHighlightWidth(value: Float)
setShowNormals(enabled: Boolean)
setOnClickListener(l: OnClickListener?)
```

---

### PrismalIconButton

Circular glass button optimized for icons with automatic corner radius calculation.

#### XML Example

```xml
<com.matrix.prismal.PrismalIconButton
    android:id="@+id/iconButton"
    android:layout_width="56dp"
    android:layout_height="56dp"
    app:iconSrc="@drawable/ic_heart"
    app:iconPadding="12dp"
    app:ior="1.85"
    app:blurRadius="1.5"
    app:pressScale="0.88"
    app:animDuration="180" />
```

#### Attributes

- `app:iconSrc` (reference) - Icon drawable
- `app:iconPadding` (dimension, default: 8dp)
- `app:pressScale` (float, default: 0.88)
- `app:animDuration` (integer, default: 180ms)
- All optical parameters from `PrismalButton`

#### API

```kotlin
setIcon(resId: Int)
setIOR(value: Float)
setBlurRadius(value: Float)
setChromaticAberration(value: Float)
setDisplacementScale(value: Float)
setOnClickListener(l: OnClickListener?)
```

---

### PrismalSwitch

Animated toggle switch with glass thumb and color-changing track.

#### XML Example

```xml
<com.matrix.prismal.PrismalSwitch
    android:id="@+id/glassSwitch"
    android:layout_width="120dp"
    android:layout_height="60dp"
    app:isOn="false"
    app:animDuration="250"
    app:thumbWidth="60dp"
    app:trackHeight="22dp"
    app:onColor="#00B624"
    app:offColor="#555555"
    app:thumbIOR="1.85"
    app:thumbBlurRadius="1"
    app:thumbCornerRadius="50dp"
    app:thumbShadowAlpha="70"
    app:thumbShadowSoftness="0.2" />
```

#### Attributes

- `app:isOn` (boolean, default: false)
- `app:animDuration` (integer, default: 250ms)
- `app:thumbWidth` (dimension, default: auto)
- `app:trackHeight` (dimension, default: 22dp)
- `app:onColor` (color, default: #00B624)
- `app:offColor` (color, default: #555555)
- `app:thumbIOR` (float, default: 1.85)
- `app:thumbNormalStrength` (float, default: 8)
- `app:thumbDisplacementScale` (float, default: 10)
- `app:thumbBlurRadius` (float, default: 1)
- `app:thumbChromaticAberration` (float, default: 8)
- `app:thumbCornerRadius` (dimension, default: 50dp)
- `app:thumbBrightness` (float, default: 1.175)
- `app:thumbShadowSoftness` (float, default: 0.2)
- `app:thumbShadowAlpha` (integer, default: 70)

#### API

```kotlin
setOn(on: Boolean, animated: Boolean = false)
isOn(): Boolean
setThumbIOR(value: Float)
setThumbNormalStrength(value: Float)
setThumbBlurRadius(value: Float)
setThumbChromaticAberration(value: Float)
setThumbCornerRadius(value: Float)
setThumbBrightness(value: Float)
setThumbShadow(color: Int, radius: Float)
setOnToggleChangedListener(listener: (Boolean) -> Unit)
```

---

### PrismalSlider

Horizontal slider with draggable glass thumb on colored track.

#### XML Example

```xml
<com.matrix.prismal.PrismalSlider
    android:id="@+id/glassSlider"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    app:maxValue="200"
    app:thumbWidth="60dp"
    app:thumbCornerRadius="50"
    app:thumbIOR="1.85"
    app:thumbBlurRadius="1"
    app:thumbBrightness="1.175"
    app:thumbShadowAlpha="80"
    app:thumbShadowSoftness="0.2" />
```

#### Attributes

- `app:maxValue` (float, default: 100)
- `app:thumbWidth` (dimension, default: 60dp)
- All thumb optical parameters matching `PrismalSwitch`

#### API

```kotlin
setValue(value: Float)
getValue(): Float
setMaxValue(value: Float)
setOnValueChangedListener(listener: (Float) -> Unit)
setThumbWidthDp(dpValue: Float)
setThumbIOR(value: Float)
setThumbBlurRadius(value: Float)
setThumbChromaticAberration(value: Float)
setThumbCornerRadius(value: Float)
setThumbBrightness(value: Float)
setThumbShadow(color: Int, radius: Float)
getThumb(): PrismalFrameLayout
```

---

## Shader Architecture

Prismal uses custom GLSL ES 2.0 shaders to achieve realistic glass effects through a multi-stage rendering pipeline.

### Rendering Pipeline

1. **Background Capture** - Captures view hierarchy as OpenGL texture
2. **SDF Shape Generation** - Creates smooth rounded rectangle using signed distance fields
3. **Height Field Calculation** - Generates depth map from SDF with sigmoid transition
4. **Normal Computation** - Calculates surface normals via gradient sampling
5. **Refraction** - Double refraction (air→glass→air) using Snell's law
6. **Chromatic Aberration** - Separates RGB channels along refraction direction
7. **Blur Application** - Shape-aware 9-tap blur respecting boundaries
8. **Shadow & Highlights** - Adds depth with inner shadow and Fresnel highlights
9. **Final Composition** - Combines all layers with opacity

### Core Techniques

#### Signed Distance Fields (SDF)

Polynomial-smoothed SDFs create ultra-smooth glass edges:

```glsl
float smin_polynomial(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sdRoundedBoxSmooth(vec2 p, vec2 b, float r, float k_smooth) {
    vec2 q = abs(p) - b + r;
    float termA = smax_polynomial(q.x, q.y, k_smooth);
    float termB = smin_polynomial(termA, 0.0, k_smooth * 0.5);
    vec2 q_clamped = vec2(
        smax_polynomial(q.x, 0.0, k_smooth),
        smax_polynomial(q.y, 0.0, k_smooth)
    );
    return termB + length(q_clamped) - r;
}
```

#### Height Field Generation

Converts distance field to glass thickness using sigmoid:

```glsl
float getHeightFromSDF(vec2 p, vec2 b, float r, float k, float transition) {
    float dist = sdRoundedBoxSmooth(p, b, r, k);
    float normalized_dist = dist / transition;
    float height = 1.0 - (1.0 / (1.0 + exp(-normalized_dist * 6.0)));
    return clamp(height, 0.0, 1.0);
}
```

#### Fresnel Effect

Schlick's approximation for angle-dependent reflectivity:

```glsl
float fresnel(vec3 normal, vec3 viewDir, float ior) {
    float cosTheta = abs(dot(normal, viewDir));
    float r0 = pow((1.0 - ior) / (1.0 + ior), 2.0);
    return r0 + (1.0 - r0) * pow(1.0 - cosTheta, 5.0);
}
```

#### Double Refraction

Simulates light path through glass:

```glsl
vec3 refractedIn = refract(-viewDir, surfaceNormal3D, 1.0 / u_ior);
vec3 refractedOut = refract(refractedIn, -surfaceNormal3D, u_ior);
vec2 refractionOffset = refractedOut.xy * u_glassThickness * strength;
```

#### Chromatic Aberration

Wavelength-dependent refraction:

```glsl
float chromaIntensity = u_chromaticAberration * 0.002 * depthFalloff;
vec2 refractionDir = normalize(baseOffset);

vec2 offsetR = baseOffset - refractionDir * chromaIntensity;
vec2 offsetG = baseOffset;
vec2 offsetB = baseOffset + refractionDir * chromaIntensity;

vec3 refractedColor = vec3(cR.r, cG.g, cB.b);
```

#### Shape-Aware Blur

9-tap blur respecting glass boundaries:

```glsl
vec3 blur9(sampler2D tex, vec2 uv, vec2 offset, ...) {
    vec3 accum = vec3(0.0);
    float weightSum = 0.0;
    
    for(int y = -1; y <= 1; ++y) {
        for(int x = -1; x <= 1; ++x) {
            vec2 sampleUV = uv + offset + sampleOffset;
            float sampleOpacity = getShapeOpacity(sampleShapeCoord, ...);
            
            if(sampleOpacity > 0.001) {
                accum += texture2D(tex, sampleUV).rgb * sampleOpacity;
                weightSum += sampleOpacity;
            }
        }
    }
    
    return weightSum > 0.001 ? accum / weightSum : vec3(0.0);
}
```

### Shader Parameters (If you're a nerd or wish to contribute)

| Uniform | Type | Range | Description |
|---------|------|-------|-------------|
| `u_ior` | float | 1.0-2.0 | Index of Refraction |
| `u_glassThickness` | float | 1-100 | Glass depth |
| `u_normalStrength` | float | 0-20 | Surface bumpiness |
| `u_displacementScale` | float | 0.1-10 | Refraction displacement |
| `u_cornerRadius` | float | 0-∞ | Corner radius in pixels |
| `u_sminSmoothing` | float | 0-10 | SDF smoothing |
| `u_heightTransitionWidth` | float | 1-50 | Height field transition |
| `u_blurRadius` | float | 0-20 | Background blur |
| `u_chromaticAberration` | float | 0-20 | RGB color split |
| `u_brightness` | float | 0.5-2.0 | Brightness multiplier |
| `u_refractionInset` | float | 0-100 | Edge fade distance |
| `u_edgeRefractionFalloff` | float | 1-10 | Edge decay sharpness |
| `u_shadowColor` | vec4 | RGBA | Inner shadow color |
| `u_shadowSoftness` | float | 0-1 | Shadow blur extent |
| `u_showNormals` | int | 0/1 | Debug mode |

### Performance

**Per-frame operations:**
- 27 texture samples per fragment (9 × 3 RGB)
- 4 height field evaluations for gradients
- 2 refraction calculations
- Early fragment discard for optimization

**Typical performance:**
- 60 FPS on mid-range devices (Snapdragon 600+)
- ~2-3ms frame time for 300×200dp surface
- Scales efficiently with hierarchy complexity

---

## Shader Setup

### Directory Structure

```
app/
 └── src/
      └── main/
           └── res/
                └── raw/
                     ├── background_vert.vert
                     └── background_frag.frag
                     └── fragment_shader.frag
                     └── vertex_shader.frag
```

### Vertex Shader

It takes vertex positions (a_position) of a quad representing the glass surface, scales them by u_glassSize, and offsets them around the mouse position (u_mousePos). It then converts the result into clip-space coordinates for rendering (gl_Position). The shader also outputs normalized texture and shape coordinates for use in the fragment shader.

### Fragment Shader

The complete fragment shader is provided in `fragment_shader.glsl`. It includes:
- Smooth min/max polynomial functions
- SDF rounded box calculations
- Height field generation
- Fresnel computation
- Surface gradient calculation
- Shape opacity functions
- Blur implementation with shape awareness
- Main rendering pipeline

### Loading Shaders

```kotlin
object ShaderUtils {
    fun loadFromAssets(context: Context, filename: String): String {
        return context.assets.open(filename).bufferedReader().use { it.readText() }
    }
}

class PrismalGlassRenderer(private val context: Context) : GLSurfaceView.Renderer {
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vertexSource = ShaderUtils.loadFromAssets(context, "shaders/vertex_shader.glsl")
        val fragmentSource = ShaderUtils.loadFromAssets(context, "shaders/fragment_shader.glsl")
        // Compile and link shaders...
    }
}
```

---

## Integration Guide

### Basic Integration

1. Add dependency to `build.gradle`
2. Create shader files in `assets/shaders/`
3. Add Prismal components to layouts
4. Configure optical parameters

### Custom Glass Materials

```kotlin
object GlassMaterials {
    fun applyWindowGlass(layout: PrismalFrameLayout) {
        layout.apply {
            setIOR(1.52f)
            setThickness(20f)
            setBlurRadius(1f)
            setChromaticAberration(1.5f)
        }
    }
    
    fun applyFrostedGlass(layout: PrismalFrameLayout) {
        layout.apply {
            setIOR(1.5f)
            setThickness(15f)
            setBlurRadius(8f)
            setChromaticAberration(0.5f)
        }
    }
}
```

### Background Updates

```kotlin
// Manual update
glassLayout.updateBackground()

// After layout changes
glassLayout.viewTreeObserver.addOnGlobalLayoutListener {
    glassLayout.updateBackground()
}

// Throttled updates for performance
private var lastUpdate = 0L
private val updateInterval = 50L

fun updateIfNeeded() {
    val now = System.currentTimeMillis()
    if (now - lastUpdate > updateInterval) {
        glassLayout.updateBackground()
        lastUpdate = now
    }
}
```

---

## Performance Optimization

### Texture Capture

Minimize capture frequency:

```kotlin
// Good - manual control
glassLayout.updateBackground()

// Bad - unnecessary updates
glassLayout.setOnClickListener {
    glassLayout.updateBackground() // Content unchanged
}
```

### Render Mode

```kotlin
// Continuous - animations
glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

// On-demand - static content
glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
glSurfaceView.requestRender()
```

### Device-Specific Tuning

```kotlin
data class GlassSettings(
    val ior: Float,
    val blur: Float,
    val chromatic: Float
) {
    companion object {
        val HIGH_QUALITY = GlassSettings(1.75f, 4f, 8f)
        val BALANCED = GlassSettings(1.6f, 2f, 4f)
        val LOW_QUALITY = GlassSettings(1.5f, 1f, 0f)
    }
}
```

---

## Design Guidelines

### Recommended Parameter Ranges

| Effect | Subtle | Moderate | Dramatic |
|--------|--------|----------|----------|
| IOR | 1.3-1.4 | 1.5-1.6 | 1.7-2.0 |
| Blur Radius | 0.5-1.5 | 2.0-4.0 | 5.0-10.0 |
| Normal Strength | 0.5-2.0 | 3.0-8.0 | 10.0-20.0 |
| Chromatic Aberration | 0.5-1.5 | 2.0-5.0 | 6.0-15.0 |
| Brightness | 1.0-1.1 | 1.15-1.3 | 1.4-1.8 |

### Best Practices

- Keep glass surfaces under 400×400dp for optimal performance
- Use higher brightness (1.2-1.5) for content containers
- Reduce blur for text readability
- Match corner radius to app design language
- Consider device capabilities for parameter selection

---

## Troubleshooting

### Common Issues

**Glass effect not visible**
- Verify shaders in `assets/shaders/`
- Ensure background content exists
- Call `updateBackground()` after layout
- Increase `normalStrength` or `displacementScale`

**Distortion too strong**
- Reduce `normalStrength` to 1.0-3.0
- Lower `displacementScale` to 0.5-1.0
- Decrease `glassThickness` to 10-15

**Performance problems**
- Reduce `blurRadius` to 1-2
- Decrease surface dimensions
- Use `RENDERMODE_WHEN_DIRTY` for static content
- Disable chromatic aberration on low-end devices

**Sharp/pixelated edges**
- Increase `minSmoothing` to 2-5
- Ensure appropriate `cornerRadius`
- Verify EGL config includes anti-aliasing

### Debug Mode

```kotlin
glassLayout.setShowNormals(true)
```

Displays surface normals as RGB colors for debugging surface calculations.

---

## Sample Implementation

```kotlin
class MainActivity : AppCompatActivity() {
    
    private lateinit var glassContainer: PrismalFrameLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        glassContainer = findViewById(R.id.glassContainer)
        glassContainer.apply {
            setIOR(1.5f)
            setBlurRadius(3f)
            setCornerRadius(20f)
            setBrightness(1.2f)
        }
    }
    
    override fun onResume() {
        super.onResume()
        glassContainer.updateBackground()
    }
}
```

```xml
<RelativeLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.matrix.prismal.PrismalFrameLayout
        android:id="@+id/glassContainer"
        android:layout_width="300dp"
        android:layout_height="200dp"
        android:layout_centerInParent="true"
        app:ior="1.5"
        app:blurRadius="3"
        app:cornerRadius="20dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Glass Container"
            android:textColor="@android:color/white"
            android:layout_gravity="center" />

    </com.matrix.prismal.PrismalFrameLayout>

</RelativeLayout>
```

---

## Contributing

Contributions are welcome. Please follow these guidelines:

1. Fork the repository
2. Create a feature branch
3. Follow Kotlin coding conventions
4. Add tests for new features
5. Update documentation
6. Submit pull request with clear description

---

## Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/prismal/issues)
- **Documentation**: [Library Documentation](https://yourusername.github.io/prismal)
- **Changelog**: [CHANGELOG.md](CHANGELOG.md)

---