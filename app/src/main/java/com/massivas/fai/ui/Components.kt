package com.massivas.fai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.massivas.fai.CarColor

@Composable
fun Pill(
    text: String,
    color: Color,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier,
    strike: Boolean = false,
) {
    Box(
        modifier
            .alpha(if (strike) 0.5f else 1f)
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
            maxLines = 1,
        )
    }
}

@Composable
fun ColorDot(color: CarColor, size: Int = 14) {
    Box(
        Modifier
            .size(size.dp)
            .background(color.swatch, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

@Composable
fun ColorDotRaw(color: Color, size: Int = 12) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}
