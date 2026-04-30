package com.rohittp.plugables.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(name: String = "Ada Lovelace", bio: String = "First programmer, mathematician.") {
    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(bio, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ProfileScreenPreview() { ProfileScreen() }
