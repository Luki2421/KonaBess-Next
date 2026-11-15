package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.model.display.ChargingNode
import com.ireddragonicy.konabessnext.model.display.ChargingProperty

private data class ChargingEditTarget(val node: ChargingNode, val prop: ChargingProperty)

private fun ChargingProperty.display(): String =
    cells.joinToString(" ") { if (isHex) "0x" + java.lang.Long.toHexString(it) else it.toInt().toString() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingEditorScreen(
    chargingNodes: List<ChargingNode>,
    onBack: () -> Unit,
    parseCells: (String) -> List<Long>?,
    onSave: (fragmentIndex: Int, path: List<String>, propertyName: String, cells: List<Long>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<ChargingEditTarget?>(null) }

    val filtered = remember(chargingNodes, query) {
        val q = query.trim()
        if (q.isEmpty()) chargingNodes
        else chargingNodes.mapNotNull { node ->
            if (node.displayPath.contains(q, true) || node.compatible.contains(q, true)) node
            else node.copy(properties = node.properties.filter { it.name.contains(q, true) })
                .takeIf { it.properties.isNotEmpty() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(tonalElevation = 3.dp, shadowElevation = 3.dp, modifier = Modifier.zIndex(1f)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_back), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_back))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(stringResource(R.string.charging_search_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (chargingNodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_charging_nodes_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.charging_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            items(filtered, key = { "${it.fragmentIndex}:${it.displayPath}" }) { node ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = stringResource(R.string.dtbo_node_format_with_fragment, node.nodeName, node.fragmentIndex),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (node.compatible.isNotEmpty()) {
                            Text(
                                text = node.compatible,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        node.properties.forEach { prop ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editTarget = ChargingEditTarget(node, prop) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        prop.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "×${prop.cells.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    prop.display(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        var input by remember(target) { mutableStateOf(target.prop.display()) }
        val parsed = parseCells(input)
        val countOk = parsed != null && parsed.size == target.prop.cells.size
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(target.prop.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.charging_edit_desc, target.prop.cells.size, target.node.displayPath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        isError = !countOk,
                        supportingText = {
                            Text("${parsed?.size ?: "?"} / ${target.prop.cells.size}")
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 260.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = countOk,
                    onClick = {
                        onSave(target.node.fragmentIndex, target.node.path, target.prop.name, parsed!!)
                        editTarget = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
