package com.simplecityapps.shuttle.compose.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider

val OpenSans = FontFamily(
    Font(resId = R.font.opensans_regular, weight = FontWeight.Normal),
    Font(resId = R.font.opensans_light, weight = FontWeight.Light),
    Font(resId = R.font.opensans_italic, style = FontStyle.Italic),
    Font(resId = R.font.opensans_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.opensans_medium, weight = FontWeight.Medium),
)

val Typography = Typography(
    defaultFontFamily = OpenSans,
    h6 = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp
    ),
    subtitle1 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    subtitle2 = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    body1 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    body2 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    button = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
)

@Composable
fun Font(name: String, style: TextStyle) {
    Text(text = name, style = MaterialTheme.typography.caption, color = MaterialColors.onBackground, fontSize = 12.sp)
    Spacer(modifier = Modifier.size(4.dp))
    Text(text = "Almost before we knew it, we had left the ground.", style = style, color = MaterialColors.onBackground)
}

@Preview(showBackground = true)
@Composable
fun FontPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Column(
            modifier = Modifier
                .background(MaterialColors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Font("H6", MaterialTheme.typography.h6)
            Font("Subtitle1", MaterialTheme.typography.subtitle1)
            Font("Subtitle2", MaterialTheme.typography.subtitle2)
            Font("Body1", MaterialTheme.typography.body1)
            Font("Body2", MaterialTheme.typography.body2)
        }
    }
}