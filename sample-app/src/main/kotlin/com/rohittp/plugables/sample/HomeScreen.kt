package com.rohittp.plugables.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Home")
        Text("Welcome to the codeview sample.")
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun HomeScreenPreview() { HomeScreen() }
