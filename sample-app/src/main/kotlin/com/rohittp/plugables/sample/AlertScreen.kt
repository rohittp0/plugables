package com.rohittp.plugables.sample

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun AlertScreen() {
    AlertDialog(
        modifier = Modifier.padding(16.dp),
        onDismissRequest = {},
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved edits to this preview. Discarding will revert to the last render.") },
        confirmButton = { TextButton(onClick = {}) { Text("Discard") } },
        dismissButton = { TextButton(onClick = {}) { Text("Keep editing") } },
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun AlertScreenPreview() { AlertScreen() }
