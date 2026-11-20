package com.ireddragonicy.konabessnext.domain

import com.ireddragonicy.konabessnext.model.display.ChargingNode
import com.ireddragonicy.konabessnext.model.display.ChargingProperty
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds charger / battery / fast-charge (VOOC, SVOOC, UFCS, PPS, FFC, gauge…) nodes in a DTBO
 * and exposes their numeric cell-array properties for editing.
 *
 * Edits never change a property's cell count: DTBO overlays are fragile and resizing
 * arrays (e.g. strategy tables) is a common cause of boot failure.
 */
@Singleton
class ChargingDomainManager @Inject constructor() {

    private val nodeKeyword = Regex(
        "(?i)(chg|charg|batt|battery|vooc|ufcs|pps|ffc|gauge|fuel|bms|adapter|wls|wireless|cp_|charge_pump|mmi_|smart_chg|pd_)"
    )
    private val excludedProps = Regex(
        "(?i)^(phandle|linux,phandle|reg|interrupts|interrupt-parent|#.*-cells|.*-gpios?|pinctrl-.*|io-channels|.*-supply|status|clocks|power-domains|dmas|iommus|qcom,hw-settle-time|qcom,pre-scaling|qcom,scale-fn-type|qcom,adc-tm-type|qcom,decimation|qcom,ratiometric|power-source|qcom,drive-strength|drive-strength|bias-.*|input-enable|output-.*|polling-delay.*|thermal-sensors)$"
    )
    private val singleCellGroup = Regex("""^<\s*([0-9a-fA-FxX\s]+)\s*>$""")

    fun findChargingNodes(root: DtsNode): List<ChargingNode> {
        val result = ArrayList<ChargingNode>()
        val fragmentContainers = listOf(root) + root.children.filter { it.name == "/" || it.name == "root" }
        val referenceProps = collectReferenceProps(fragmentContainers)
        for (container in fragmentContainers) {
            for (fragment in container.children) {
                if (!DtboDomainUtils.isFragmentNode(fragment)) continue
                val fragmentIndex = DtboDomainUtils.parseFragmentIndex(fragment.name)
                val overlay = fragment.getChild("__overlay__") ?: continue
                collect(overlay, fragmentIndex, fragment.name, emptyList(), inheritedMatch = false, referenceProps, result)
            }
        }
        return result.distinctBy { it.fragmentName to it.path }
    }

    /**
     * Properties whose cells are phandle references, keyed as "fragment@N/__overlay__/a/b:prop".
     * They come from __fixups__ (references to the base DT, resolved by the bootloader) and
     * __local_fixups__ (references inside the overlay, relocated by the bootloader).
     * Editing them would break the overlay, so they are never offered for editing.
     */
    private fun collectReferenceProps(containers: List<DtsNode>): Set<String> {
        val refs = HashSet<String>()
        val quoted = Regex("\"([^\"]+)\"")
        for (container in containers) {
            container.getChild("__fixups__")?.properties?.forEach { prop ->
                quoted.findAll(prop.originalValue).forEach { m ->
                    // "/fragment@31/__overlay__/node:property:offset"
                    val parts = m.groupValues[1].trimStart('/').split(':')
                    if (parts.size >= 2) refs.add(parts[0] + ":" + parts[1])
                }
            }
            container.getChild("__local_fixups__")?.let { walkLocalFixups(it, "", refs) }
        }
        return refs
    }

    private fun walkLocalFixups(node: DtsNode, path: String, out: MutableSet<String>) {
        for (prop in node.properties) {
            if (path.isNotEmpty()) out.add("$path:${prop.name}")
        }
        for (child in node.children) {
            walkLocalFixups(child, if (path.isEmpty()) child.name else "$path/${child.name}", out)
        }
    }

    private fun collect(
        node: DtsNode,
        fragmentIndex: Int,
        fragmentName: String,
        parentPath: List<String>,
        inheritedMatch: Boolean,
        referenceProps: Set<String>,
        out: MutableList<ChargingNode>
    ) {
        for (child in node.children) {
            val path = parentPath + child.name
            val compatible = child.getProperty("compatible")?.originalValue?.replace("\"", "") ?: ""
            val matches = inheritedMatch || nodeKeyword.containsMatchIn(child.name) || nodeKeyword.containsMatchIn(compatible)
            if (matches) {
                val keyPrefix = "$fragmentName/__overlay__/" + path.joinToString("/") + ":"
                val props = child.properties
                    .filter { (keyPrefix + it.name) !in referenceProps }
                    .mapNotNull { toChargingProperty(it.name, it.originalValue) }
                if (props.isNotEmpty()) {
                    out.add(ChargingNode(fragmentIndex, fragmentName, path, compatible, props))
                }
            }
            collect(child, fragmentIndex, fragmentName, path, matches, referenceProps, out)
        }
    }

    private fun toChargingProperty(name: String, raw: String): ChargingProperty? {
        if (excludedProps.matches(name)) return null
        val m = singleCellGroup.find(raw.trim()) ?: return null
        val tokens = m.groupValues[1].trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val cells = tokens.map { parseCell(it) ?: return null }
        val isHex = tokens.any { it.startsWith("0x", ignoreCase = true) }
        return ChargingProperty(name, cells, isHex)
    }

    private fun parseCell(token: String): Long? =
        if (token.startsWith("0x", ignoreCase = true)) token.substring(2).toLongOrNull(16) else token.toLongOrNull()

    /** Formats cells back into a DTS value in the property's original radix. */
    fun formatCells(cells: List<Long>, hex: Boolean): String =
        cells.joinToString(" ", prefix = "<", postfix = ">") { v ->
            val u = v and 0xFFFFFFFFL
            if (hex) "0x" + java.lang.Long.toHexString(u) else u.toString()
        }

    /** Parses user input ("4510 4520", "0x11a2, 0x11b0", negatives allowed) into 32-bit cells. */
    fun parseUserCells(input: String): List<Long>? {
        val tokens = input.trim().split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.map { t ->
            val v = when {
                t.startsWith("0x", true) -> t.substring(2).toLongOrNull(16)
                t.startsWith("-0x", true) -> t.substring(3).toLongOrNull(16)?.let { -it }
                else -> t.toLongOrNull()
            } ?: return null
            if (v < Int.MIN_VALUE || v > 0xFFFFFFFFL) return null
            v and 0xFFFFFFFFL
        }
    }

    fun findNode(root: DtsNode, fragmentName: String, path: List<String>): DtsNode? {
        val fragmentContainers = listOf(root) + root.children.filter { it.name == "/" || it.name == "root" }
        for (container in fragmentContainers) {
            for (fragment in container.children) {
                if (fragment.name != fragmentName) continue
                var cur: DtsNode? = fragment.getChild("__overlay__")
                for (name in path) {
                    cur = cur?.getChild(name)
                }
                if (cur != null) return cur
            }
        }
        return null
    }
}
