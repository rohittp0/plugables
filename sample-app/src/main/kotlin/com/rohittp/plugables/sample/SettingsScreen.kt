package com.rohittp.plugables.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Settings")
        Text("Adjust your preferences.")
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Settings")
@Composable
fun SettingsScreenPreview() { SettingsScreen() }
