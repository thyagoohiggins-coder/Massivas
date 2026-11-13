package com.massivas.fai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
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
            .alpha(if (strike) 0.45f else 1f)
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
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

/** Botão grande de seleção (usado no lugar de chips pequenos). */
@Composable
fun SelectTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    accent: Color? = null,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    val contentColor = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = contentColor,
        border = if (selected) null else BorderStroke(1.dp, (accent ?: MaterialTheme.colorScheme.outlineVariant).copy(alpha = if (accent != null) 0.7f else 1f)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (accent != null) {
                Box(
                    Modifier.size(12.dp).background(if (selected) contentColor else accent, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
            }
            content(contentColor)
        }
    }
}

/** Grade simples com N colunas de mesma largura. */
@Composable
fun <T> Grid(
    items: List<T>,
    columns: Int = 2,
    spacing: Dp = 8.dp,
    cell: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { cell(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}
