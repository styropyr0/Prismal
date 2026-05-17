package com.matrix.prismal

/**
 * Controls how aggressively the background bitmap is downsampled before being
 * uploaded as a GPU texture. Lower resolution reduces memory bandwidth and GPU
 * upload cost; the blur pass hides the loss of sharpness.
 *
 * When [OFF], the bitmap is captured at full view resolution (highest quality,
 * most memory). [AGGRESSIVE] captures at 25 % resolution - ideal for large,
 * heavily-blurred glass surfaces.
 *
 * If no mode is set on a [PrismalFrameLayout], the scale is derived
 * automatically from the blur radius (legacy behaviour).
 */
enum class DownsampleMode(internal val scale: Float) {
    OFF(1.0f),
    SUBTLE(0.65f),
    BALANCED(0.4f),
    AGGRESSIVE(0.15f)
}