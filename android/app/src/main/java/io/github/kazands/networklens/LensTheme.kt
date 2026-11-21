package io.github.kazands.networklens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Static brand schemes. Switching themes never interpolates text through its background. */
internal val LensDay = lightColorScheme(
    primary=Color(0xFF0B6B51), onPrimary=Color.White,
    primaryContainer=Color(0xFFBFEBD5), onPrimaryContainer=Color(0xFF002418), inversePrimary=Color(0xFFA5E4BB),
    secondary=Color(0xFF4A6258), onSecondary=Color.White,
    secondaryContainer=Color(0xFFE3ECDD), onSecondaryContainer=Color(0xFF172D25),
    tertiary=Color(0xFF2B6A7D), onTertiary=Color.White,
    tertiaryContainer=Color(0xFFCBE8F2), onTertiaryContainer=Color(0xFF001F29),
    background=Color(0xFFF5F6F1), onBackground=Color(0xFF172D25),
    surface=Color.White, onSurface=Color(0xFF172D25),
    surfaceVariant=Color(0xFFE3ECDD), onSurfaceVariant=Color(0xFF52665B), surfaceTint=Color(0xFF0B6B51),
    inverseSurface=Color(0xFF25352E), inverseOnSurface=Color(0xFFEAF2EA),
    error=Color(0xFFB3261E), onError=Color.White,
    errorContainer=Color(0xFFF9DEDC), onErrorContainer=Color(0xFF410E0B),
    outline=Color(0xFF6B7E73), outlineVariant=Color(0xFFCFD9CA), scrim=Color.Black,
    surfaceBright=Color.White, surfaceDim=Color(0xFFDCE2D4),
    surfaceContainerLowest=Color.White, surfaceContainerLow=Color(0xFFFAFBF7),
    surfaceContainer=Color(0xFFF0F3EA), surfaceContainerHigh=Color(0xFFE9EEE1), surfaceContainerHighest=Color(0xFFE1E7D9),
    primaryFixed=Color(0xFFBFEBD5), primaryFixedDim=Color(0xFFA5D8BF),
    onPrimaryFixed=Color(0xFF002418), onPrimaryFixedVariant=Color(0xFF0E5340),
    secondaryFixed=Color(0xFFE3ECDD), secondaryFixedDim=Color(0xFFC7D3C1),
    onSecondaryFixed=Color(0xFF172D25), onSecondaryFixedVariant=Color(0xFF384E43),
    tertiaryFixed=Color(0xFFCBE8F2), tertiaryFixedDim=Color(0xFFA6D3E2),
    onTertiaryFixed=Color(0xFF001F29), onTertiaryFixedVariant=Color(0xFF1F4C5D),
)
internal val LensNight = darkColorScheme(
    primary=Color(0xFFA5E4BB), onPrimary=Color(0xFF06281B),
    primaryContainer=Color(0xFF0E5340), onPrimaryContainer=Color(0xFFC2F2D5), inversePrimary=Color(0xFF0B6B51),
    secondary=Color(0xFFB4CBBD), onSecondary=Color(0xFF1F3329),
    secondaryContainer=Color(0xFF243B2D), onSecondaryContainer=Color(0xFFEEF4EC),
    tertiary=Color(0xFFA6D3E2), onTertiary=Color(0xFF073543),
    tertiaryContainer=Color(0xFF1F4C5D), onTertiaryContainer=Color(0xFFCBE8F2),
    background=Color(0xFF101A17), onBackground=Color(0xFFEEF4EC),
    surface=Color(0xFF1B2923), onSurface=Color(0xFFEEF4EC),
    surfaceVariant=Color(0xFF243B2D), onSurfaceVariant=Color(0xFFA9BDB1), surfaceTint=Color(0xFFA5E4BB),
    inverseSurface=Color(0xFFEEF4EC), inverseOnSurface=Color(0xFF1B2923),
    error=Color(0xFFF2B8B5), onError=Color(0xFF601410),
    errorContainer=Color(0xFF8C1D18), onErrorContainer=Color(0xFFF9DEDC),
    outline=Color(0xFF80958A), outlineVariant=Color(0xFF33453C), scrim=Color.Black,
    surfaceBright=Color(0xFF33443C), surfaceDim=Color(0xFF0C1512),
    surfaceContainerLowest=Color(0xFF0A110F), surfaceContainerLow=Color(0xFF16221D),
    surfaceContainer=Color(0xFF202F28), surfaceContainerHigh=Color(0xFF273830), surfaceContainerHighest=Color(0xFF2F4238),
    primaryFixed=LensDay.primaryFixed, primaryFixedDim=LensDay.primaryFixedDim,
    onPrimaryFixed=LensDay.onPrimaryFixed, onPrimaryFixedVariant=LensDay.onPrimaryFixedVariant,
    secondaryFixed=LensDay.secondaryFixed, secondaryFixedDim=LensDay.secondaryFixedDim,
    onSecondaryFixed=LensDay.onSecondaryFixed, onSecondaryFixedVariant=LensDay.onSecondaryFixedVariant,
    tertiaryFixed=LensDay.tertiaryFixed, tertiaryFixedDim=LensDay.tertiaryFixedDim,
    onTertiaryFixed=LensDay.onTertiaryFixed, onTertiaryFixedVariant=LensDay.onTertiaryFixedVariant,
)

private fun lensText(size: Int, line: Int, weight: FontWeight=FontWeight.Normal) =
    TextStyle(fontFamily=FontFamily.SansSerif,fontSize=size.sp,lineHeight=line.sp,fontWeight=weight)
internal val LensTypography = Typography(
    displayLarge=lensText(57,64,FontWeight.SemiBold), displayMedium=lensText(45,52,FontWeight.SemiBold), displaySmall=lensText(36,44,FontWeight.SemiBold),
    headlineLarge=lensText(28,34,FontWeight.SemiBold), headlineMedium=lensText(26,32,FontWeight.SemiBold), headlineSmall=lensText(22,28,FontWeight.SemiBold),
    titleLarge=lensText(20,26,FontWeight.SemiBold), titleMedium=lensText(16,22,FontWeight.SemiBold), titleSmall=lensText(14,20,FontWeight.Medium),
    bodyLarge=lensText(16,24), bodyMedium=lensText(14,21), bodySmall=lensText(13,19),
    labelLarge=lensText(14,20,FontWeight.Medium), labelMedium=lensText(12,18,FontWeight.Medium), labelSmall=lensText(11,16,FontWeight.Medium),
)
internal object LensShape {
    val hero=RoundedCornerShape(28.dp)
    val card=RoundedCornerShape(16.dp)
    val tile=RoundedCornerShape(12.dp)
    val chip=RoundedCornerShape(50)
}
private val LensShapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=LensShape.tile,medium=LensShape.card,large=RoundedCornerShape(20.dp),extraLarge=LensShape.hero)

@Immutable internal data class LensTone(val container: Color,val ink: Color,val icon: Color)
internal enum class LensGroup { BRAND, NETWORK, COMPUTERS, MEDIA, HOME, UNNAMED, OTHER }
internal val LensDayTones = mapOf(
    LensGroup.NETWORK to LensTone(Color(0xFFDCE8F7),Color(0xFF14335A),Color(0xFF2A5C9E)),
    LensGroup.COMPUTERS to LensTone(Color(0xFFD3EDED),Color(0xFF0A4446),Color(0xFF1B7679)),
    LensGroup.MEDIA to LensTone(Color(0xFFE8E1F6),Color(0xFF37285F),Color(0xFF6548B0)),
    LensGroup.HOME to LensTone(Color(0xFFF7E7BC),Color(0xFF4E3500),Color(0xFF8E6200)),
    LensGroup.UNNAMED to LensTone(Color(0xFFEAE7DD),Color(0xFF46422F),Color(0xFF746E58)),
    LensGroup.OTHER to LensTone(Color(0xFFE3ECDD),Color(0xFF172D25),Color(0xFF4A6258)),
)
internal val LensNightTones = mapOf(
    LensGroup.NETWORK to LensTone(Color(0xFF1F3350),Color(0xFFCBDCF6),Color(0xFF8FB4EE)),
    LensGroup.COMPUTERS to LensTone(Color(0xFF173F3E),Color(0xFFBEE8E6),Color(0xFF6FCBC7)),
    LensGroup.MEDIA to LensTone(Color(0xFF32285A),Color(0xFFDDD3F6),Color(0xFFB7A3EE)),
    LensGroup.HOME to LensTone(Color(0xFF483610),Color(0xFFF7E1A8),Color(0xFFE8BE5A)),
    LensGroup.UNNAMED to LensTone(Color(0xFF2D2C25),Color(0xFFDBD7C9),Color(0xFFA9A38F)),
    LensGroup.OTHER to LensTone(Color(0xFF243B2D),Color(0xFFEEF4EC),Color(0xFFB4CBBD)),
)
private val LocalLensTones=staticCompositionLocalOf { LensDayTones }

/** Colour follows the existing physical-category filters, never incidental audio/Matter services. */
internal fun lensGroup(device: Device): LensGroup = when {
    device.self || device.gateway -> LensGroup.BRAND
    UiDevicePolicy.matches(device,"",DeviceFilter.NETWORK) -> LensGroup.NETWORK
    UiDevicePolicy.matches(device,"",DeviceFilter.COMPUTERS) -> LensGroup.COMPUTERS
    UiDevicePolicy.matches(device,"",DeviceFilter.MEDIA) -> LensGroup.MEDIA
    UiDevicePolicy.matches(device,"",DeviceFilter.HOME) -> LensGroup.HOME
    device.presentation().unnamed -> LensGroup.UNNAMED
    else -> LensGroup.OTHER
}
@Composable internal fun lensTone(device: Device): LensTone {
    val scheme=MaterialTheme.colorScheme
    val group=lensGroup(device)
    return if(group==LensGroup.BRAND) LensTone(scheme.primaryContainer,scheme.onPrimaryContainer,scheme.onPrimaryContainer)
        else LocalLensTones.current.getValue(group)
}
@Composable internal fun LensTheme(dark: Boolean,content: @Composable ()->Unit) {
    CompositionLocalProvider(LocalLensTones provides if(dark) LensNightTones else LensDayTones,
        LocalLensMotionAllowed provides rememberLensMotionAllowed()) {
        MaterialTheme(colorScheme=if(dark) LensNight else LensDay,typography=LensTypography,shapes=LensShapes,content=content)
    }
}
