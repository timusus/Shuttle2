package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.designsystem.R

/**
 * Google Sans Flex (SIL OFL 1.1), bundled as one variable file cut down to its weight (300 to 800) and optical size
 * (16 to 36) axes. Each weight is a variation of that file, so any weight costs nothing more.
 */
private fun googleSansFlex(opticalSize: Float): FontFamily = FontFamily(
    (300..800 step 100).map { weight ->
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize)),
        )
    },
)

/** The display cut of Google Sans Flex, for display and headline sizes: tighter spacing and finer detail. */
val S2DisplayFontFamily: FontFamily = googleSansFlex(opticalSize = 36f)

/** The text cut of Google Sans Flex, for title, body and label sizes. */
val S2TextFontFamily: FontFamily = googleSansFlex(opticalSize = 16f)

private val baseline = Typography()

private fun TextStyle.s2(
    family: FontFamily,
    weight: FontWeight,
    letterSpacing: TextUnit,
): TextStyle = copy(fontFamily = family, fontWeight = weight, letterSpacing = letterSpacing)

/**
 * The M3 type scale's sizes and line heights on Google Sans Flex, with more contrast than the baseline: display and
 * headline sizes (screen titles) are heavier and tracked tighter, titles are medium weight, body and label text keep
 * their size and open tracking. Each emphasized style is its base style a step or two heavier.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val S2Typography: Typography = run {
    val displayLarge = baseline.displayLarge.s2(S2DisplayFontFamily, FontWeight.Medium, (-0.02).em)
    val displayMedium = baseline.displayMedium.s2(S2DisplayFontFamily, FontWeight.Medium, (-0.02).em)
    val displaySmall = baseline.displaySmall.s2(S2DisplayFontFamily, FontWeight.Medium, (-0.015).em)
    val headlineLarge = baseline.headlineLarge.s2(S2DisplayFontFamily, FontWeight.SemiBold, (-0.015).em)
    val headlineMedium = baseline.headlineMedium.s2(S2DisplayFontFamily, FontWeight.SemiBold, (-0.01).em)
    val headlineSmall = baseline.headlineSmall.s2(S2DisplayFontFamily, FontWeight.SemiBold, (-0.01).em)
    val titleLarge = baseline.titleLarge.s2(S2TextFontFamily, FontWeight.Medium, 0.sp)
    val titleMedium = baseline.titleMedium.s2(S2TextFontFamily, FontWeight.Medium, 0.1.sp)
    val titleSmall = baseline.titleSmall.s2(S2TextFontFamily, FontWeight.Medium, 0.1.sp)
    val bodyLarge = baseline.bodyLarge.s2(S2TextFontFamily, FontWeight.Normal, 0.15.sp)
    val bodyMedium = baseline.bodyMedium.s2(S2TextFontFamily, FontWeight.Normal, 0.2.sp)
    val bodySmall = baseline.bodySmall.s2(S2TextFontFamily, FontWeight.Normal, 0.3.sp)
    val labelLarge = baseline.labelLarge.s2(S2TextFontFamily, FontWeight.Medium, 0.1.sp)
    val labelMedium = baseline.labelMedium.s2(S2TextFontFamily, FontWeight.Medium, 0.4.sp)
    val labelSmall = baseline.labelSmall.s2(S2TextFontFamily, FontWeight.Medium, 0.5.sp)
    Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        displaySmall = displaySmall,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
        displayLargeEmphasized = displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMediumEmphasized = displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmallEmphasized = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLargeEmphasized = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMediumEmphasized = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmallEmphasized = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLargeEmphasized = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMediumEmphasized = titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmallEmphasized = titleSmall.copy(fontWeight = FontWeight.Bold),
        bodyLargeEmphasized = bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMediumEmphasized = bodyMedium.copy(fontWeight = FontWeight.Medium),
        bodySmallEmphasized = bodySmall.copy(fontWeight = FontWeight.Medium),
        labelLargeEmphasized = labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMediumEmphasized = labelMedium.copy(fontWeight = FontWeight.Bold),
        labelSmallEmphasized = labelSmall.copy(fontWeight = FontWeight.Bold),
    )
}
