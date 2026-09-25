package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.google.android.material.math.MathUtils.lerp
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/** A decorative snowfall overlay, seeded once per composition by [forecast] (0.0-1.0 odds of snow). */
@Composable
fun Snowfall(
    forecast: Double,
    modifier: Modifier = Modifier
) {
    val random = remember { SecureRandom() }
    var isSnowing by remember { mutableStateOf(false) }

    LaunchedEffect(forecast) {
        if (forecast > 0.0 && random.nextDouble() <= forecast) {
            isSnowing = true
        }
    }

    if (isSnowing) {
        SnowfallCanvas(modifier)
    }
}

@Composable
private fun SnowfallCanvas(modifier: Modifier) {
    val random = remember { SecureRandom() }
    val snowflakes = remember { mutableListOf<Snowflake>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var generation by remember { mutableStateOf(0) }

    LaunchedEffect(canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect

        delay(TimeUnit.SECONDS.toMillis(random.nextInt(60).coerceAtLeast(5).toLong()))

        val snowfallDuration = TimeUnit.SECONDS.toMillis(random.nextInt(180).coerceAtLeast(60).toLong())
        val stopGeneratingAt = System.currentTimeMillis() + snowfallDuration
        while (System.currentTimeMillis() < stopGeneratingAt) {
            val flakesToAdd = (10 + random.nextInt(FLAKE_INCREMENT)).coerceAtMost(TOTAL_FLAKES - snowflakes.size)
            if (flakesToAdd > 0) {
                repeat(flakesToAdd) { snowflakes.add(newSnowflake(random, canvasSize)) }
            }
            delay(TimeUnit.SECONDS.toMillis(random.nextInt(8).coerceAtLeast(2).toLong()))
        }
    }

    LaunchedEffect(canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        while (true) {
            delay(FRAME_INTERVAL_MS)
            val iterator = snowflakes.iterator()
            while (iterator.hasNext()) {
                val flake = iterator.next()
                flake.snowX += flake.velX
                flake.snowY += flake.velY
                if (flake.snowY > canvasSize.height) {
                    iterator.remove()
                }
            }
            generation++
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        generation
        snowflakes.forEach { flake ->
            drawCircle(
                color = Color.White.copy(alpha = flake.alpha / 255f),
                radius = flake.snowR,
                center = Offset(flake.snowX, flake.snowY)
            )
        }
    }
}

private fun newSnowflake(random: SecureRandom, canvasSize: IntSize): Snowflake {
    val angle = Math.toRadians(lerp(MIN_ANGLE, MAX_ANGLE, random.nextFloat()).toDouble())
    val speed: Float = lerp(MIN_SPEED, MAX_SPEED, random.nextFloat())
    val velX = (speed.toDouble() * StrictMath.cos(angle)).toFloat()
    val velY = (speed.toDouble() * StrictMath.sin(angle)).toFloat()
    val size: Float = lerp(MIN_SIZE, MAX_SIZE, random.nextFloat())
    val startX: Float = lerp(0f, canvasSize.width.toFloat(), random.nextFloat())
    val startY: Float = lerp(0f, canvasSize.height.toFloat(), random.nextFloat()) - canvasSize.height.toFloat() + size
    val alpha = lerp(MIN_ALPHA.toFloat(), MAX_ALPHA.toFloat(), random.nextFloat()).toInt()
    return Snowflake(startX, startY, velX, velY, size, alpha)
}

private class Snowflake(
    var snowX: Float,
    var snowY: Float,
    val velX: Float,
    val velY: Float,
    val snowR: Float,
    val alpha: Int
)

/** The total number of snowflakes to generate */
private const val TOTAL_FLAKES = 200

/** The increment with which to generate more snowflakes */
private const val FLAKE_INCREMENT = 30

/** Default min and max snowflake alpha */
private const val MIN_ALPHA = 100
private const val MAX_ALPHA = 250

/** Default min and max snowflake angle */
private const val MIN_ANGLE = 80f
private const val MAX_ANGLE = 100f

/** Default min and max snowflake velocity */
private const val MIN_SPEED = 1f
private const val MAX_SPEED = 5f

/** Default min and max snowflake size */
private const val MIN_SIZE = 2f
private const val MAX_SIZE = 15f

/** Snowflake position update interval, ~60fps */
private const val FRAME_INTERVAL_MS = 16L
