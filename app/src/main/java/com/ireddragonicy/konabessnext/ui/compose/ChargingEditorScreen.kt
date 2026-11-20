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

/** Decimal view; cells >= 0x80000000 are shown as signed 32-bit (0xffffff9c -> -100). */
private fun ChargingProperty.display(): String =
    cells.joinToString(" ") { it.toInt().toString() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingEditorScreen(
    chargingNodes: List<ChargingNode>,
    onBack: () -> Unit,
    parseCells: (String) -> List<Long>?,
    onSave: (fragmentName: String, path: List<String>, propertyName: String, cells: List<Long>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<ChargingEditTarget?>(null) }

    val numberQuery = remember(query) { parseNumberQuery(query) }
    val filtered = remember(chargingNodes, query, numberQuery) {
        val q = query.trim()
        when {
            q.isEmpty() -> chargingNodes
            numberQuery != null -> chargingNodes.mapNotNull { node ->
                node.copy(properties = node.properties.filter { p -> p.cells.any { numberQuery.matches(it) } })
                    .takeIf { it.properties.isNotEmpty() }
            }
            else -> chargingNodes.mapNotNull { node ->
                if (node.displayPath.contains(q, true) || node.compatible.contains(q, true)) node
                else node.copy(properties = node.properties.filter { it.name.contains(q, true) })
                    .takeIf { it.properties.isNotEmpty() }
            }
        }
    }
    val matchCount = remember(filtered, numberQuery) {
        if (numberQuery == null) 0
        else filtered.sumOf { n -> n.properties.sumOf { p -> p.cells.count { numberQuery.matches(it) } } }
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
                if (numberQuery != null) {
                    Text(
                        text = stringResource(R.string.charging_number_results, matchCount, filtered.sumOf { it.properties.size }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
            items(filtered, key = { "${it.fragmentName}:${it.displayPath}" }) { node ->
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
                                if (numberQuery != null) {
                                    Text(
                                        highlightCells(prop, numberQuery, MaterialTheme.colorScheme.tertiary),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
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
                        onSave(target.node.fragmentName, target.node.path, target.prop.name, parsed!!)
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

/** Numeric search: single value ("2750", "-100", "0xabe") or range ("2700-2800", "2700..2800"). */
private data class NumberQuery(val min: Long, val max: Long) {
    /** Matches a raw 32-bit cell both as signed and unsigned value. */
    fun matches(cell: Long): Boolean {
        val signed = cell.toInt().toLong()
        val unsigned = cell and 0xFFFFFFFFL
        return signed in min..max || unsigned in min..max
    }
}

private fun parseNum(t: String): Long? {
    val s = t.trim()
    return when {
        s.startsWith("-0x", true) -> s.substring(3).toLongOrNull(16)?.let { -it }
        s.startsWith("0x", true) -> s.substring(2).toLongOrNull(16)
        else -> s.toLongOrNull()
    }
}

private fun parseNumberQuery(input: String): NumberQuery? {
    val q = input.trim()
    if (q.isEmpty()) return null
    parseNum(q)?.let { return NumberQuery(it, it) }
    val range = Regex("""^(-?(?:0x[0-9a-fA-F]+|\d+))\s*(?:\.\.|-|–|:)\s*(-?(?:0x[0-9a-fA-F]+|\d+))$""").find(q)
        ?: return null
    val a = parseNum(range.groupValues[1]) ?: return null
    val b = parseNum(range.groupValues[2]) ?: return null
    return NumberQuery(minOf(a, b), maxOf(a, b))
}

private fun highlightCells(
    prop: ChargingProperty,
    query: NumberQuery,
    highlight: androidx.compose.ui.graphics.Color
): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    prop.cells.forEachIndexed { i, cell ->
        if (i > 0) append(" ")
        val text = cell.toInt().toString()
        if (query.matches(cell)) {
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = highlight,
                    fontWeight = FontWeight.Bold,
                    background = highlight.copy(alpha = 0.18f)
                )
            )
            append(text)
            pop()
        } else {
            append(text)
        }
    }
}
