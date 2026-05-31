package com.example.assistive

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        // Premium Squircle App Logo Container
        Surface(
            modifier = Modifier.size(96.dp),
            shape = RoundedCornerShape(28.dp), // Sleek iOS/Squircle-style rounded corners
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = "App Logo",
                    modifier = Modifier.size(52.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // App Branding
        Text(
            text = "Assistive Touch Ball",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(6.dp))

        // Modernized Version Badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = "v1.0.0 (Stable)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(40.dp))

        // Application Info Block Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Application Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                InfoRow(
                    icon = Icons.Rounded.Person,
                    label = "Developer",
                    value = "Per Jansen M. Victoriano"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                InfoRow(
                    icon = Icons.Rounded.Code,
                    label = "Platform",
                    value = "Android Native\n(Jetpack Compose)",
                    isMultiLineValue = true
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                InfoRow(
                    icon = Icons.Rounded.Info,
                    label = "Purpose",
                    value = "Accessibility Quick Controls & Shortcuts",
                    isMultiLineValue = true
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        // Copyright Footer
        Text(
            text = "© 2026 All Rights Reserved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    isMultiLineValue: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = if (isMultiLineValue) Alignment.Top else Alignment.CenterVertically
    ) {
        // Label Side with Icon
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }

        // Value Side
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            lineHeight = 18.sp
        )
    }
}