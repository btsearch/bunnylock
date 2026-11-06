package dev.ririxi.bunnylock.modem

import dev.ririxi.bunnylock.core.Bands

/**
 * Unisoc's LTE band mask, as `AT+SPLBAND=0` reports it:
 * `tdd_h,tdd_l,fdd_h,fdd_l[,fdd65_80]`.
 *
 * FDD bands 1..32 use bit (band - 1); TDD bands 33..64 use bit (band - 33);
 * FDD bands 65..80 use bit (band - 65). The two 16-bit halves are sent separately.
 */
internal data class UnisocLteMask(
    val tddHigh: Int = 0,
    val tddLow: Int = 0,
    val fddHigh: Int = 0,
    val fddLow: Int = 0,
    val fdd65To80: Int = 0,
) {
    /** The exact five-field form `AT+SPLBAND=0` returns. */
    val readback: String
        get() = listOf(tddHigh, tddLow, fddHigh, fddLow, fdd65To80).joinToString(",")

    /**
     * The form the engineering app sends after `AT+SPLBAND=1,`. It leaves out the last field when it is zero,
     * which is also the shorter command that has been verified on this modem.
     */
    val command: String
        get() = if (fdd65To80 == 0) listOf(tddHigh, tddLow, fddHigh, fddLow).joinToString(",") else readback

    fun bands(): List<Int> =
        buildList {
            for (i in 0..15) {
                if (fddLow and (1 shl i) != 0) add(i + 1)
                if (fddHigh and (1 shl i) != 0) add(i + 17)
                if (tddLow and (1 shl i) != 0) add(i + 33)
                if (tddHigh and (1 shl i) != 0) add(i + 49)
                if (fdd65To80 and (1 shl i) != 0) add(i + 65)
            }
        }.sorted()

    companion object {
        fun from(bands: Collection<Int>): UnisocLteMask {
            var tddHigh = 0
            var tddLow = 0
            var fddHigh = 0
            var fddLow = 0
            var fdd65To80 = 0
            for (band in bands.distinct()) {
                when (band) {
                    in 1..16 -> fddLow = fddLow or (1 shl (band - 1))
                    in 17..32 -> fddHigh = fddHigh or (1 shl (band - 17))
                    in 33..48 -> tddLow = tddLow or (1 shl (band - 33))
                    in 49..64 -> tddHigh = tddHigh or (1 shl (band - 49))
                    in 65..80 -> fdd65To80 = fdd65To80 or (1 shl (band - 65))
                }
            }
            return UnisocLteMask(tddHigh, tddLow, fddHigh, fddLow, fdd65To80)
        }

        fun parse(reply: String): UnisocLteMask? {
            val match = READBACK.find(reply) ?: return null
            return UnisocLteMask(
                tddHigh = match.groupValues[1].toIntOrNull() ?: return null,
                tddLow = match.groupValues[2].toIntOrNull() ?: return null,
                fddHigh = match.groupValues[3].toIntOrNull() ?: return null,
                fddLow = match.groupValues[4].toIntOrNull() ?: return null,
                fdd65To80 = match.groupValues[5].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0,
            )
        }

        private val READBACK = Regex("""\+SPLBAND\s*:\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*(\d+))?""")
    }
}

/** The five-field mask for [bands], for example `0,0,0,1,0` for LTE Band 1 alone. */
internal fun unisocLteMask(bands: Collection<Int>): String = UnisocLteMask.from(bands).readback

/** LTE bands Unisoc's mask can represent: FDD 1..32, TDD 33..64 and FDD 65..80. */
internal val UNISOC_LTE_BANDS: List<Int> = Bands.LTE.filter { it in 1..32 || it in 33..64 || it in 65..80 }

/**
 * The mask captured from the RMX3511 before its first lock. It is only the last resort for "unlock":
 * if bunnylock saw the phone's real mask before locking, that saved mask is used instead.
 */
internal val UNISOC_DEFAULT_LTE_MASK = UnisocLteMask(tddLow = 416, fddHigh = 2056, fddLow = 213)

/** The network mode, or Auto when the phone doesn't report one. */
internal val ModemState.netModeMask: Long
    get() = allowedMask ?: NetMode.AUTO.mask

/** The network mode with only [locking] and the networks that have a band lock of their own left on. */
internal fun ModemState.maskKeepingLocks(locking: Group): Long {
    val kept = groups.filter { it.group == locking || it.locked }.map { it.group.generation }.toSet()
    val dropped = groups.map { it.group.generation }.filter { it !in kept }
    return dropped.fold(netModeMask or locking.generation) { mask, generation -> mask and generation.inv() }
}

/** One name per network in [mask], such as "5G NR". */
internal fun ModemState.networkNames(mask: Long): List<String> =
    groups
        .map { it.group }
        .filter { it.generation and mask != 0L }
        .distinctBy { it.generation }
        .map { it.label }
