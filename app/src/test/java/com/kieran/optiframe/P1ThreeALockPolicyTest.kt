package com.kieran.optiframe

import com.kieran.optiframe.ui.optical.P1ThreeALockPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class P1ThreeALockPolicyTest {
    @Test
    fun `does nothing while anchors are never locked`() {
        val policy = P1ThreeALockPolicy()

        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(false, false, 0L))
        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(false, false, 2_000_000_000L))
        assertEquals(P1ThreeALockPolicy.State.IDLE, policy.state)
    }

    @Test
    fun `first locked frame meters`() {
        val policy = P1ThreeALockPolicy()

        assertEquals(P1ThreeALockPolicy.Action.METER, policy.onFrame(true, false, 0L))
        assertEquals(P1ThreeALockPolicy.State.METERING, policy.state)
    }

    @Test
    fun `locks after 500 milliseconds of continuous anchor lock`() {
        val policy = P1ThreeALockPolicy()

        assertEquals(P1ThreeALockPolicy.Action.METER, policy.onFrame(true, false, 0L))
        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(true, false, 499_000_000L))
        assertEquals(P1ThreeALockPolicy.State.METERING, policy.state)
        assertEquals(P1ThreeALockPolicy.Action.LOCK, policy.onFrame(true, true, 500_000_000L))
        assertEquals(P1ThreeALockPolicy.State.LOCKED, policy.state)
    }

    @Test
    fun `losing anchors while metering resets the policy`() {
        val policy = P1ThreeALockPolicy()

        assertEquals(P1ThreeALockPolicy.Action.METER, policy.onFrame(true, false, 0L))
        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(false, false, 200_000_000L))
        assertEquals(P1ThreeALockPolicy.State.IDLE, policy.state)
        assertEquals(P1ThreeALockPolicy.Action.METER, policy.onFrame(true, false, 300_000_000L))
        assertEquals(P1ThreeALockPolicy.State.METERING, policy.state)
    }

    @Test
    fun `locked state survives a brief loss and recovery`() {
        val policy = P1ThreeALockPolicy()
        policy.onFrame(true, false, 0L)
        assertEquals(P1ThreeALockPolicy.Action.LOCK, policy.onFrame(true, true, 500_000_000L))

        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(false, false, 600_000_000L))
        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(false, false, 1_599_000_000L))
        assertEquals(P1ThreeALockPolicy.State.LOCKED, policy.state)
        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(true, false, 1_600_000_000L))
        assertEquals(P1ThreeALockPolicy.State.LOCKED, policy.state)
    }

    @Test
    fun `unlocks after one second of continuous loss then meters again`() {
        val policy = P1ThreeALockPolicy()
        policy.onFrame(true, false, 0L)
        policy.onFrame(true, true, 500_000_000L)

        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(false, false, 600_000_000L))
        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(false, false, 1_599_999_999L))
        assertEquals(P1ThreeALockPolicy.Action.UNLOCK, policy.onFrame(false, false, 1_600_000_000L))
        assertEquals(P1ThreeALockPolicy.State.IDLE, policy.state)
        assertEquals(P1ThreeALockPolicy.Action.METER, policy.onFrame(true, false, 1_700_000_000L))
    }

    @Test
    fun `reset clears policy state and timers`() {
        val policy = P1ThreeALockPolicy()
        policy.onFrame(true, false, 0L)
        policy.onFrame(true, true, 500_000_000L)

        policy.reset()

        assertEquals(P1ThreeALockPolicy.State.IDLE, policy.state)
        assertEquals(P1ThreeALockPolicy.Action.METER, policy.onFrame(true, false, 600_000_000L))
    }

    @Test
    fun `constructor rejects non-positive durations`() {
        assertThrows(IllegalArgumentException::class.java) {
            P1ThreeALockPolicy(meterToLockNanos = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            P1ThreeALockPolicy(lossToUnlockNanos = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            P1ThreeALockPolicy(
                meterToLockNanos = 500_000_000L,
                maxMeteringNanos = 499_999_999L
            )
        }
    }

    @Test
    fun `waits beyond the minimum lock duration when metering is not converged`() {
        val policy = P1ThreeALockPolicy()

        assertEquals(P1ThreeALockPolicy.Action.METER, policy.onFrame(true, false, 0L))
        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(true, false, 500_000_000L))
        assertEquals(P1ThreeALockPolicy.State.METERING, policy.state)
    }

    @Test
    fun `locks when metering converges after the minimum duration`() {
        val policy = P1ThreeALockPolicy()

        assertEquals(P1ThreeALockPolicy.Action.METER, policy.onFrame(true, false, 0L))
        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(true, false, 500_000_000L))
        assertEquals(P1ThreeALockPolicy.Action.LOCK, policy.onFrame(true, true, 800_000_000L))
    }

    @Test
    fun `locks at the maximum metering duration if convergence never completes`() {
        val policy = P1ThreeALockPolicy()

        assertEquals(P1ThreeALockPolicy.Action.METER, policy.onFrame(true, false, 0L))
        assertEquals(P1ThreeALockPolicy.Action.NONE, policy.onFrame(true, false, 2_999_999_999L))
        assertEquals(P1ThreeALockPolicy.Action.LOCK, policy.onFrame(true, false, 3_000_000_000L))
    }
}
