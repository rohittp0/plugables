package com.rohittp.plugables.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SearchScreen(query: String = "compose preview", results: List<String> = sampleResults()) {
    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {},
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        results.forEach { row ->
            Text(row, modifier = Modifier.padding(vertical = 8.dp))
            HorizontalDivider()
        }
    }
}

private fun sampleResults() = listOf(
    "Best practices for Compose previews",
    "Compose preview annotations explained",
    "Test your Compose UI with codeview",
    "Layout Inspector vs codeview",
)

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun SearchScreenPreview() { SearchScreen() }
