package com.crystaln.vpn.wg;

import com.wireguard.android.backend.Statistics;

/** Kotlin interop helper: record accessors are easier from plain Java. */
public final class WgStats {
    private WgStats() {}

    /** Returns {rx, tx} byte totals, or nulls-as-0 when stats are absent. */
    public static long[] totals(Statistics stats) {
        if (stats == null) {
            return new long[] {0L, 0L};
        }
        return new long[] {stats.totalRx(), stats.totalTx()};
    }
}
