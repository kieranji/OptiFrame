package com.kieran.optiframe.ui.optical

internal class P1ThreeALockPolicy(
    private val meterToLockNanos: Long = DEFAULT_METER_TO_LOCK_NANOS,
    private val lossToUnlockNanos: Long = DEFAULT_LOSS_TO_UNLOCK_NANOS,
    private val maxMeteringNanos: Long = DEFAULT_MAX_METERING_NANOS
) {
    enum class State { IDLE, METERING, LOCKED }
    enum class Action { NONE, METER, LOCK, UNLOCK }

    @get:Synchronized
    var state = State.IDLE
        private set

    private var meteringSinceNanos: Long? = null
    private var lostSinceNanos: Long? = null

    init {
        require(meterToLockNanos > 0L)
        require(lossToUnlockNanos > 0L)
        require(maxMeteringNanos >= meterToLockNanos)
    }

    @Synchronized
    fun onFrame(
        anchorsLocked: Boolean,
        meteringConverged: Boolean,
        nowNanos: Long
    ): Action = when (state) {
        State.IDLE -> if (anchorsLocked) {
            state = State.METERING
            meteringSinceNanos = nowNanos
            Action.METER
        } else {
            Action.NONE
        }
        State.METERING -> when {
            !anchorsLocked -> {
                state = State.IDLE
                meteringSinceNanos = null
                Action.NONE
            }
            else -> {
                val elapsedNanos = nowNanos - checkNotNull(meteringSinceNanos)
                if (elapsedNanos >= meterToLockNanos &&
                    (meteringConverged || elapsedNanos >= maxMeteringNanos)
                ) {
                    state = State.LOCKED
                    meteringSinceNanos = null
                    Action.LOCK
                } else {
                    Action.NONE
                }
            }
        }
        State.LOCKED -> if (anchorsLocked) {
            lostSinceNanos = null
            Action.NONE
        } else {
            val lostSince = lostSinceNanos ?: nowNanos.also { lostSinceNanos = it }
            if (nowNanos - lostSince >= lossToUnlockNanos) {
                state = State.IDLE
                lostSinceNanos = null
                Action.UNLOCK
            } else {
                Action.NONE
            }
        }
    }

    @Synchronized
    fun reset() {
        state = State.IDLE
        meteringSinceNanos = null
        lostSinceNanos = null
    }

    companion object {
        const val DEFAULT_METER_TO_LOCK_NANOS: Long = 500_000_000L
        const val DEFAULT_LOSS_TO_UNLOCK_NANOS: Long = 1_000_000_000L
        const val DEFAULT_MAX_METERING_NANOS: Long = 3_000_000_000L
    }
}
