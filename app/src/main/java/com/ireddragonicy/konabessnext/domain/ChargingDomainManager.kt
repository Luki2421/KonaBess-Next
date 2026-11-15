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
        for (container in fragmentContainers) {
            for (fragment in container.children) {
                if (!DtboDomainUtils.isFragmentNode(fragment)) continue
                val fragmentIndex = DtboDomainUtils.parseFragmentIndex(fragment.name)
                val overlay = fragment.getChild("__overlay__") ?: continue
                collect(overlay, fragmentIndex, emptyList(), inheritedMatch = false, out = result)
            }
        }
        return result.distinctBy { it.fragmentIndex to it.path }
    }

    private fun collect(
        node: DtsNode,
        fragmentIndex: Int,
        parentPath: List<String>,
        inheritedMatch: Boolean,
        out: MutableList<ChargingNode>
    ) {
        for (child in node.children) {
            val path = parentPath + child.name
            val compatible = child.getProperty("compatible")?.originalValue?.replace("\"", "") ?: ""
            val matches = inheritedMatch || nodeKeyword.containsMatchIn(child.name) || nodeKeyword.containsMatchIn(compatible)
            if (matches) {
                val props = child.properties.mapNotNull { toChargingProperty(it.name, it.originalValue) }
                if (props.isNotEmpty()) {
                    out.add(ChargingNode(fragmentIndex, path, compatible, props))
                }
            }
            collect(child, fragmentIndex, path, matches, out)
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

    fun findNode(root: DtsNode, fragmentIndex: Int, path: List<String>): DtsNode? {
        val fragmentContainers = listOf(root) + root.children.filter { it.name == "/" || it.name == "root" }
        for (container in fragmentContainers) {
            for (fragment in container.children) {
                if (!DtboDomainUtils.isFragmentNode(fragment)) continue
                if (DtboDomainUtils.parseFragmentIndex(fragment.name) != fragmentIndex) continue
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
