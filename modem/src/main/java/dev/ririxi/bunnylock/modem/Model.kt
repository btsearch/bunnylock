package dev.ririxi.bunnylock.modem

import android.telephony.TelephonyManager
import dev.ririxi.bunnylock.core.Bands
import dev.ririxi.bunnylock.data.Carrier
import dev.ririxi.bunnylock.data.Cell
import dev.ririxi.bunnylock.data.Signal
import org.json.JSONObject

enum class Backend(
    val label: String,
) {
    QUALCOMM("Qualcomm QMI"),
    MEDIATEK("MediaTek AT"),
    PIXEL("Pixel (Tensor NV)"),
    ANDROID("Android radio HAL"),
}

enum class Group(
    val label: String,
    val isNr: Boolean,
) {
    LTE("4G LTE", false),
    NR("5G NR", true),
    NR_SA("5G SA", true),
    NR_NSA("5G NSA", true),
    WCDMA("3G WCDMA", false),
    GSM("2G GSM", false),
    ;

    fun bandLabel(band: Int) =
        when (this) {
            LTE, WCDMA -> "B$band"
            NR, NR_SA, NR_NSA -> "n$band"
            GSM -> "$band"
        }

    fun frequencyName(band: Int): String? =
        when (this) {
            LTE -> Bands.LTE_FREQUENCY_NAMES[band]
            NR, NR_SA, NR_NSA -> Bands.NR_FREQUENCY_NAMES[band]
            WCDMA -> Bands.WCDMA_FREQUENCY_NAMES[band]
            GSM -> null
        }

    fun defaults(): List<Int> =
        when (this) {
            LTE -> Bands.LTE
            NR, NR_SA, NR_NSA -> Bands.NR
            WCDMA -> Bands.WCDMA
            GSM -> Bands.GSM
        }
}

data class BandGroupState(
    val group: Group,
    val supported: List<Int>,
    /** null = unknown / unrestricted. */
    val current: Set<Int>?,
    /** A band the phone is camped on although the lock excludes it, so the modem isn't following the lock. */
    val stray: Int? = null,
) {
    val locked get() = !current.isNullOrEmpty() && !current.containsAll(supported)

    val allowed get() = current ?: supported.toSet()
}

/** Masks are TelephonyManager NETWORK_TYPE_BITMASK_* values. */
enum class NetMode(
    val label: String,
    val mask: Long,
) {
    AUTO("Auto", Masks.G2 or Masks.G3 or Masks.G4 or Masks.G5 or Masks.CDMA),
    NR_LTE("5G and 4G", Masks.G4 or Masks.G5),
    NR_ONLY("5G only", Masks.G5),
    LTE_ONLY("4G only", Masks.G4),
    NO_NR("No 5G", Masks.G2 or Masks.G3 or Masks.G4 or Masks.CDMA),
    WCDMA_ONLY("3G only", Masks.G3),
    GSM_ONLY("2G only", Masks.G2),
    ;

    companion object {
        /**
         * The mode [mask] stands for, going by which of 2G, 3G, 4G and 5G it allows. A modem drops the network types
         * it lacks (TD-SCDMA or CDMA, say) from the mask it was given, so the bits themselves rarely match.
         */
        fun of(mask: Long?): NetMode? {
            mask ?: return null
            val generations = listOf(Masks.G2, Masks.G3, Masks.G4, Masks.G5)
            return entries.firstOrNull { mode -> generations.all { (mode.mask and it != 0L) == (mask and it != 0L) } }
        }
    }
}

object Masks {
    /** Network type n is bit n - 1 of an allowed-network-types bitmask. */
    private fun maskOf(vararg networkTypes: Int) = networkTypes.fold(0L) { mask, type -> mask or (1L shl (type - 1)) }

    val G2 = maskOf(
        TelephonyManager.NETWORK_TYPE_GSM,
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE
    )

    // The framework deprecated the CDMA types, but the Auto and No 5G masks still carry them for phones that have CDMA.
    @Suppress("DEPRECATION")
    val CDMA =
        maskOf(
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
        )

    val G3 =
        maskOf(
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        )
    val G4 = maskOf(TelephonyManager.NETWORK_TYPE_LTE)
    val G5 = maskOf(TelephonyManager.NETWORK_TYPE_NR)

    // TelephonyManager.NETWORK_TYPE_LTE_CA, which the SDK hides.
    private const val NETWORK_TYPE_LTE_CA = 19

    /** LTE also fits a 5G mask, since it anchors 5G NSA. */
    fun allows(
        mask: Long,
        networkType: Int,
    ): Boolean {
        val type = if (networkType == NETWORK_TYPE_LTE_CA) TelephonyManager.NETWORK_TYPE_LTE else networkType
        val anchorsNr = type == TelephonyManager.NETWORK_TYPE_LTE && mask and G5 != 0L
        return mask and maskOf(type) != 0L || anchorsNr
    }

    /** QMI NAS mode-preference bits. */
    fun toQmi(mask: Long): Int {
        var modePreference = 0
        if (mask and G2 != 0L) modePreference = modePreference or 0x04
        if (mask and G3 != 0L) modePreference = modePreference or 0x08
        if (mask and G4 != 0L) modePreference = modePreference or 0x10
        if (mask and G5 != 0L) modePreference = modePreference or 0x40
        return modePreference
    }
}

/** QMI and Samsung both take the ordinal as the 5G mode number, so the order matters. */
enum class NrMode(
    val label: String,
) {
    BOTH("SA and NSA"),
    NSA("NSA only"),
    SA("SA only"),
}

enum class LockRat(
    val label: String,
) {
    LTE("LTE"),
    NR("5G NR"),
    NSA("LTE and NR"),
}

/** "26201" -> "262 01", "310260" -> "310 260". */
fun formatPlmn(numeric: String): String =
    if (numeric.length >= 5) numeric.substring(0, 3) + " " + numeric.substring(3) else numeric

fun andList(items: List<String>): String = joinNatural(items, "and")

fun orList(items: List<String>): String = joinNatural(items, "or")

private fun joinNatural(
    items: List<String>,
    word: String,
): String =
    if (items.size <= 1) items.joinToString("") else items.dropLast(1).joinToString(", ") + " $word " + items.last()

data class SimInfo(
    val slot: Int,
    val subId: Int,
    val name: String,
)

data class DeviceInfo(
    val vendor: String,
    val qrtr: Boolean,
    val socModel: String,
    val platform: String,
    val manufacturer: String,
    val model: String,
    val baseband: String,
    val ril: String,
    val sdk: Int,
    val atNodes: List<String>,
    val pixelRouter: Boolean,
    val samsungMenu: Boolean,
    val aospChannels: Boolean,
    val aospNrDc: Boolean,
    /** MediaTek's phoneEx AT passthrough, usable without root. */
    val mtkOemHook: Boolean,
    val mtkOemHookStatus: String,
    val mtkOemHookChecked: List<String>,
    /** Samsung's ISemTelephony 5G mode control (works without root). */
    val samsungNrMode: Boolean,
) {
    val vendorLabel
        get() =
            when (vendor) {
                "qualcomm" -> "Qualcomm Snapdragon"
                "mediatek" -> "MediaTek"
                "tensor" -> "Google Tensor (Shannon modem)"
                "exynos" -> "Samsung Exynos (Shannon modem)"
                "unisoc" -> "Unisoc"
                else -> "Unknown modem"
            }

    private val hasMediatekAtPort get() = atNodes.any { it.startsWith("/dev/radio") || it == "/dev/ttyC0" }

    fun suggested(): Backend =
        when {
            qrtr -> Backend.QUALCOMM
            vendor == "mediatek" && (mtkOemHook || hasMediatekAtPort) -> Backend.MEDIATEK
            vendor == "tensor" && pixelRouter -> Backend.PIXEL
            else -> Backend.ANDROID
        }

    /** [shellOnly] is Shizuku with ADB access, which reaches telephony services but not the modem. */
    fun allows(
        backend: Backend,
        shellOnly: Boolean,
    ): Boolean = !shellOnly || backend == Backend.ANDROID || (backend == Backend.MEDIATEK && mtkOemHook)

    companion object {
        fun from(json: JSONObject) =
            DeviceInfo(
                vendor = json.optString("vendor"),
                qrtr = json.optBoolean("qrtr"),
                socModel = json.optString("socModel"),
                platform = json.optString("platform"),
                manufacturer = json.optString("manufacturer"),
                model = json.optString("model"),
                baseband = json.optString("baseband"),
                ril = json.optString("ril"),
                sdk = json.optInt("sdk"),
                atNodes = json.stringList("atNodes"),
                pixelRouter = json.optBoolean("pixelRouter"),
                samsungMenu = json.optBoolean("samsungMenu"),
                aospChannels = json.optBoolean("aospChannels"),
                aospNrDc = json.optBoolean("aospNrDc"),
                mtkOemHook = json.optBoolean("mtkOemHook"),
                mtkOemHookStatus = json.optString("mtkOemHookStatus"),
                mtkOemHookChecked = json.stringList("mtkOemHookChecked"),
                samsungNrMode = json.optBoolean("samsungNrMode"),
            )

        private fun JSONObject.stringList(key: String): List<String> =
            optJSONArray(key)?.let { array -> List(array.length()) { array.getString(it) } } ?: emptyList()
    }
}

enum class Access(
    val label: String,
) {
    ROOT("Root"),
    SHIZUKU_ROOT("Shizuku (root)"),
    SHIZUKU_ADB("Shizuku (ADB)"),
}

sealed interface RootState {
    data object Checking : RootState

    data object Granted : RootState

    data class Denied(
        val reason: String,
    ) : RootState
}

data class ModemState(
    val root: RootState = RootState.Checking,
    /** What the connection attempt is doing right now, shown while [root] is Checking. */
    val connectStep: String = "",
    val access: Access? = null,
    val device: DeviceInfo? = null,
    val backend: Backend? = null,
    val autoBackend: Backend? = null,
    val sims: List<SimInfo> = emptyList(),
    /** Counted from 1 as users see it, so one more than the SIM's slot index. */
    val sim: Int = 1,
    val refreshing: Boolean = false,
    val groups: List<BandGroupState> = emptyList(),
    val allowedMask: Long? = null,
    val supportedTypes: Long? = null,
    val netModeHeldBack: String? = null,
    val netModeMasks: String? = null,
    val nrMode: NrMode? = null,
    val nrDc: Boolean? = null,
    val cells: List<Cell> = emptyList(),
    /** Primary first. */
    val carriers: List<Carrier> = emptyList(),
    val signals: List<Signal> = emptyList(),
    val lteLock: String? = null,
    val nrLock: String? = null,
    /** A 2G or 3G cell lock. bunnylock doesn't set these, but a MediaTek modem may hold one made elsewhere. */
    val legacyCellLock: String? = null,
    /** e.g. "EARFCN 75": the channel the phone is camped on although a frequency lock excludes it. */
    val strayChannel: String? = null,
    /** The MediaTek modem doesn't take AT+EMMCHLCK, so a cell lock has to go through the radio HAL. */
    val mtkCellLockRefused: Boolean = false,
    /** e.g. "262 01". Null when selection is automatic. */
    val plmnLock: String? = null,
    val registeredPlmn: String? = null,
    val registeredName: String? = null,
    val homePlmn: String? = null,
    val homeName: String? = null,
    val persist: Boolean = true,
    val atNode: String = "",
    val log: List<String> = emptyList(),
) {
    val anyBandLock get() = groups.any { it.locked }

    /** e.g. "B1": the band the phone is camped on although a band lock excludes it. */
    val strayBand
        get() = groups.firstNotNullOfOrNull { groupState ->
            groupState.stray?.let {
                groupState.group.bandLabel(
                    it
                )
            }
        }
    val anyCellLock get() = lteLock !== null || nrLock !== null || legacyCellLock !== null
    val anyPlmnLock get() = plmnLock !== null

    /** ADB-level access only reaches the framework's telephony APIs, not the modem directly. */
    val limited get() = access == Access.SHIZUKU_ADB

    /** With ADB-level access only the HAL backend works, plus MediaTek when its RIL passthrough exists. */
    fun allows(candidate: Backend): Boolean = device?.allows(candidate, limited) == true

    /** The networks worth offering for a lock, as PLMN to operator name: home first, then the current one, then every visible one. */
    val plmnPicks: List<Pair<String, String>>
        get() {
            val picks = LinkedHashMap<String, String>()
            homePlmn?.let { picks[it] = homeName ?: "Home" }
            registeredPlmn?.let { picks.putIfAbsent(it, registeredName ?: "Current") }
            cells.forEach { cell -> cell.plmn?.let { picks.putIfAbsent(it, cell.operator ?: "Visible") } }
            return picks.toList()
        }

    val supports5g: Boolean
        get() {
            val types = supportedTypes ?: return true
            // A report without LTE is incomplete, so it can't rule 5G out.
            return types and Masks.G4 == 0L || types and Masks.G5 != 0L || groups.any { it.group.isNr }
        }

    val netModes: List<NetMode>
        get() = if (supports5g) NetMode.entries else NetMode.entries.filter { it.mask and Masks.G5 == 0L } - NetMode.NO_NR

    val netMode: NetMode?
        get() = NetMode.of(allowedMask).let { if (it == NetMode.NO_NR && !supports5g) NetMode.AUTO else it }

    val supportsNrMode
        get() =
            supports5g &&
                    when (backend) {
                        Backend.QUALCOMM, Backend.PIXEL -> true
                        Backend.ANDROID -> device?.samsungNrMode == true
                        else -> false
                    }
    val supportsNrDc get() = supports5g && device?.aospNrDc == true
    val mtkAtCellLock get() = backend == Backend.MEDIATEK && !mtkCellLockRefused

    /** Every other cell lock goes through the radio HAL, which locks a frequency but never a PCI. */
    val supportsPciLock get() = backend == Backend.QUALCOMM || mtkAtCellLock
}

data class CellLockRequest(
    val rat: LockRat,
    val earfcn: Long? = null,
    val ltePcis: List<Int> = emptyList(),
    val nrArfcn: Long? = null,
    val nrPcis: List<Int> = emptyList(),
    val nrBand: Int? = null,
    /** Subcarrier spacing in kHz. */
    val nrScs: Int = 30,
)

class ModemException(
    override val message: String,
) : Exception(message)

fun String.asSentence(): String {
    val text = trim().replaceFirstChar { it.uppercase() }
    return if (text.endsWith('.') || text.endsWith('?') || text.endsWith('!')) text else "$text."
}

/** For quoting a message inside a sentence: no final period, and no capital unless the first word is an acronym. */
fun String.asClause(): String {
    val text = trim().trimEnd('.')
    val startsWithCapitalisedWord = text.length > 1 && text[0].isUpperCase() && text[1].isLowerCase()
    return if (startsWithCapitalisedWord) text[0].lowercase() + text.substring(1) else text
}

fun reason(e: Exception): String = e.message?.takeIf { it.isNotBlank() }?.asSentence() ?: "Something went wrong."
