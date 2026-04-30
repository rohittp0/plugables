package com.rohittp.plugables.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private data class Message(val from: String, val text: String, val mine: Boolean)

@Composable
fun ChatScreen() {
    val msgs = listOf(
        Message("Sam", "Hey, did you see the new codeview report?", false),
        Message("Me", "Yes, the per-preview overlays are great", true),
        Message("Sam", "Click into one — IDE deep-link works", false),
        Message("Me", "Trying it now…", true),
    )
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        msgs.forEach { Bubble(it) }
    }
}

@Composable
private fun Bubble(message: Message) {
    val align = if (message.mine) Arrangement.End else Arrangement.Start
    val bg = if (message.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = align) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ChatScreenPreview() { ChatScreen() }
