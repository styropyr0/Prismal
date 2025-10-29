# PrismalFrameLayout

## Overview

`PrismalFrameLayout` is a custom Android `FrameLayout` that simulates a realistic **glass refraction** and **blur effect** using **OpenGL ES 2.0 shaders**.
It captures the area behind the layout in real time, applies optical distortions such as refraction, blur, and chromatic aberration, and renders the result as a dynamic glass surface beneath its child content.

This layout behaves like a normal `FrameLayout`-you can freely place any child views inside-while automatically maintaining a live-rendered glass background.

---

## Features

* Real-time background capture and refraction rendering.
* Shader-based glass simulation using OpenGL ES 2.0.
* Configurable parameters for refraction, thickness, blur, brightness, and chromatic aberration.
* Fully functional as a container; supports any child layout or view.
* Efficient rendering with GPU acceleration.
* Interactive distortion and touch-based highlights.

---

## Structure

```
PrismalFrameLayout
├── GLSurfaceView (background rendering layer)
│   └── PrismalGlassRenderer – handles texture and shader operations
└── Child Views (foreground content)
```

---

## How It Works

1. The layout captures the visible content of the root view behind itself.
2. The captured bitmap is cropped to match the layout’s boundaries.
3. The cropped texture is passed to an OpenGL renderer.
4. The renderer applies fragment and vertex shaders simulating:

   * Light refraction through a glass medium
   * Background blur and diffusion
   * Chromatic aberration for light dispersion
   * Highlight and brightness modulation
5. The GLSurfaceView renders beneath the layout’s child views.

---

## Usage Example

### XML

```xml
<com.matrix.prismal.PrismalFrameLayout
    android:id="@+id/glassContainer"
    android:layout_width="200dp"
    android:layout_height="200dp"
    android:layout_gravity="center">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Glass Frame"
        android:textColor="@android:color/white"
        android:textSize="18sp"
        android:gravity="center" />

</com.matrix.prismal.PrismalFrameLayout>
```

### Kotlin

```kotlin
val glassLayout = findViewById<PrismalFrameLayout>(R.id.glassContainer)

glassLayout.apply {
    setIOR(1.5f)
    setRefractionInset(0.2f)
    setCornerRadius(25f)
    setThickness(0.8f)
    setNormalStrength(0.6f)
    setBlurRadius(4f)
    setBrightness(1.2f)
    setChromaticAberration(0.03f)
}
```

---

## API Reference

| Method                                      | Description                                            |
| ------------------------------------------- | ------------------------------------------------------ |
| `setRefractionInset(value: Float)`          | Sets the intensity of inward refraction.               |
| `setGlassSize(width: Float, height: Float)` | Defines the size of the rendered glass surface.        |
| `setCornerRadius(radius: Float)`            | Sets the corner rounding of the glass effect.          |
| `setIOR(value: Float)`                      | Defines the Index of Refraction for light bending.     |
| `setThickness(value: Float)`                | Adjusts perceived glass thickness.                     |
| `setNormalStrength(value: Float)`           | Controls the surface normal influence on distortion.   |
| `setDisplacementScale(value: Float)`        | Sets the displacement mapping intensity.               |
| `setHeightBlurFactor(value: Float)`         | Scales the blur relative to surface height variations. |
| `setMinSmoothing(value: Float)`             | Sets minimal normal smoothing.                         |
| `setBlurRadius(value: Float)`               | Adjusts the blur radius of the background.             |
| `setHighlightWidth(value: Float)`           | Sets the highlight sharpness and spread.               |
| `setChromaticAberration(value: Float)`      | Adds color dispersion for a realistic light split.     |
| `setBrightness(value: Float)`               | Adjusts glass brightness.                              |
| `setShowNormals(show: Boolean)`             | Toggles debug visualization of surface normals.        |

---

## Shader File Organization

The rendering effect is defined by GLSL shader files that are stored in your `assets` folder.

**Recommended structure:**

```
app/
 └── src/
      └── main/
           └── assets/
                └── shaders/
                     ├── prismal_vertex.vert
                     └── prismal_fragment.frag
```

Each shader file should contain standard GLSL ES 2.0 code:

* **Vertex shader (`.vert`)** - handles vertex positions, normal maps, and texture coordinates.
* **Fragment shader (`.frag`)** - handles light refraction, chromatic aberration, and blur effects.

**Loading shaders:**
Use a small utility method like this:

```kotlin
object ShaderUtils {
    fun loadFromAssets(context: Context, filename: String): String {
        val builder = StringBuilder()
        context.assets.open(filename).bufferedReader().useLines { lines ->
            lines.forEach { builder.append(it).append('\n') }
        }
        return builder.toString()
    }
}
```

Then in your renderer:

```kotlin
val vertexSource = ShaderUtils.loadFromAssets(context, "shaders/prismal_vertex.vert")
val fragmentSource = ShaderUtils.loadFromAssets(context, "shaders/prismal_fragment.frag")
```

This keeps the shaders organized, editable, and independent from your Kotlin source.

---

## Design Notes

* The `GLSurfaceView` is drawn **behind** all child views to keep the content visible.
* Background capture occurs only when the layout is resized, avoiding unnecessary recomputation.
* All OpenGL operations are dispatched through `queueEvent()` to run safely on the GL thread.
* The implementation supports dynamic resizing and live visual adjustments.
* You can extend the renderer to support custom light sources, reflections, or animated normal maps.

---

## Troubleshooting

| Issue                                      | Cause                                        | Fix                                                                                |
| ------------------------------------------ | -------------------------------------------- | ---------------------------------------------------------------------------------- |
| Child views appear blurred or missing      | The GLSurfaceView is rendering above them    | The layout enforces drawing order using `getChildDrawingOrder()`                   |
| The glass effect disappears after rotation | EGL context loss during configuration change | Reinitialize textures in `onSurfaceCreated()`                                      |
| High memory usage                          | Frequent background captures                 | Background capture runs only on size changes; avoid unnecessary invalidation calls |

---

## Contributing

Contributions are welcome.
You can extend the renderer, improve shader quality, or optimize the capture pipeline. Submit pull requests or open issues for feature discussions.

---

## License

This project is released under the **MIT License**.
You may use, modify, and distribute it freely with attribution.
