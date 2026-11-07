package dev.pocketserver.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceStateTest {
    @Test
    fun followsLifecycleTransitions() {
        assertTrue(ServiceState.STOPPED.canTransitionTo(ServiceState.STARTING))
        assertTrue(ServiceState.STARTING.canTransitionTo(ServiceState.RUNNING))
        assertTrue(ServiceState.STARTING.canTransitionTo(ServiceState.FAILED))
        assertTrue(ServiceState.RUNNING.canTransitionTo(ServiceState.STOPPING))
        assertTrue(ServiceState.STOPPING.canTransitionTo(ServiceState.STOPPED))
        assertFalse(ServiceState.STOPPED.canTransitionTo(ServiceState.RUNNING))
        assertFalse(ServiceState.RUNNING.canTransitionTo(ServiceState.STARTING))
    }
}
