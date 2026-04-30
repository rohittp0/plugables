package com.rohittp.plugables.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private data class Notification(val title: String, val body: String, val time: String)

@Composable
fun NotificationScreen() {
    val items = listOf(
        Notification("Build complete", "codeviewReportDebug finished in 12s", "2m ago"),
        Notification("PR review requested", "@rohittp wants you to review #42", "11m ago"),
        Notification("Deploy succeeded", "main → production rolled out", "1h ago"),
        Notification("Weekly digest", "5 new previews, 0 broken", "yesterday"),
    )
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Notifications", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { index, n ->
            Row {
                Column {
                    Text(n.title, style = MaterialTheme.typography.titleSmall)
                    Text(n.body, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Text(n.time, style = MaterialTheme.typography.labelSmall)
            }
            if (index < items.lastIndex) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun NotificationScreenPreview() { NotificationScreen() }
