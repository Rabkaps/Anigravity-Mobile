package com.hambalapps.agymobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 10-step Material 3 Expressive corner radius scale and asymmetric connected shapes.
 */
object M3ExpressiveShape {
    val None = RoundedCornerShape(0.dp)
    val ExtraSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val LargeIncreased = RoundedCornerShape(20.dp)
    val ExtraLarge = RoundedCornerShape(28.dp)
    val ExtraLargeIncreased = RoundedCornerShape(32.dp)
    val ExtraExtraLarge = RoundedCornerShape(48.dp)
    val Full = RoundedCornerShape(9999.dp)

    val groupLeftShape = RoundedCornerShape(
        topStart = 20.dp,
        bottomStart = 20.dp,
        topEnd = 4.dp,
        bottomEnd = 4.dp
    )

    val groupMiddleShape = RoundedCornerShape(4.dp)

    val groupRightShape = RoundedCornerShape(
        topStart = 4.dp,
        bottomStart = 4.dp,
        topEnd = 20.dp,
        bottomEnd = 20.dp
    )
}

val AgyExpressiveShapes = Shapes(
    extraSmall = M3ExpressiveShape.ExtraSmall,
    small = M3ExpressiveShape.Small,
    medium = M3ExpressiveShape.Large,
    large = M3ExpressiveShape.LargeIncreased,
    extraLarge = M3ExpressiveShape.ExtraLarge
)
