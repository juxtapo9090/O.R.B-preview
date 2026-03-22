package com.phantom.ai.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.phantom.ai.R

@Composable
fun BubbleContent(
    size: Float = 56f,
    opacity: Float = 0.9f,
    onTap: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .alpha(opacity),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_orb),
            contentDescription = "O.R.B.",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
