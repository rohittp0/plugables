package com.rohittp.plugables.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private data class CartItem(val name: String, val priceUsd: Double)

@Composable
fun CartScreen() {
    val items = listOf(
        CartItem("Compose Cookbook", 24.99),
        CartItem("USB-C cable", 9.50),
        CartItem("Mechanical keyboard", 119.00),
        CartItem("Coffee beans", 18.75),
    )
    val total = items.sumOf { it.priceUsd }
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Cart", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        items.forEach { it ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.name)
                Text("$${"%.2f".format(it.priceUsd)}")
            }
            Spacer(Modifier.height(6.dp))
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", style = MaterialTheme.typography.titleMedium)
            Text("$${"%.2f".format(total)}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun CartScreenPreview() { CartScreen() }
