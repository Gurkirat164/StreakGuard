package com.streakguard.app.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UsernameManagerTest {
    private class Fixture {
        var account = UsernameAccount("old", "old-revision", UsernameStatus.VALID)
        var response: Boolean? = true
        var saves = 0
        var queued = 0
        var notifications = 0
        var applications = 0
        var cachedAccount: String? = "old"
        var duringLookup: (() -> Unit)? = null
        val manager = UsernameManager(
            profileExists = { duringLookup?.invoke(); response },
            saveAccount = { name, verified ->
                saves++
                account = UsernameAccount(name, "revision-$saves", if (verified) UsernameStatus.VALID else UsernameStatus.PENDING)
                cachedAccount = null
            },
            readAccount = { account },
            completeValidation = { expected, valid ->
                applications++
                if (account == expected && account.status == UsernameStatus.PENDING) {
                    account = account.copy(status = if (valid) UsernameStatus.VALID else UsernameStatus.INVALID)
                    if (!valid) notifications++
                }
            },
            enqueueRefresh = { queued++ },
        )
    }

    @Test fun onlineInvalidCandidatePreservesExistingAccountAndCache() = runBlocking {
        val f = Fixture().apply { response = false }
        assertEquals(UsernameSaveResult.INVALID, f.manager.save("bad"))
        assertEquals("old", f.account.username)
        assertEquals("old", f.cachedAccount)
        assertEquals(0, f.saves)
        assertEquals(0, f.queued)
    }

    @Test fun onlineValidCandidateClearsOldCacheAndQueuesFreshData() = runBlocking {
        val f = Fixture()
        assertEquals(UsernameSaveResult.SAVED, f.manager.save("new"))
        assertEquals(UsernameStatus.VALID, f.account.status)
        assertEquals("new", f.account.username)
        assertNull(f.cachedAccount)
        assertEquals(1, f.queued)
    }

    @Test fun offlineSavePersistsPendingAccountAndQueuesRecovery() = runBlocking {
        val f = Fixture().apply { response = null }
        assertEquals(UsernameSaveResult.PENDING, f.manager.save("new"))
        assertEquals(UsernameStatus.PENDING, f.account.status)
        assertNull(f.cachedAccount)
        assertEquals(1, f.queued)
        assertFalse(f.manager.validatePending())
        assertEquals(UsernameStatus.PENDING, f.account.status)
        assertEquals(0, f.notifications)
    }

    @Test fun recoveredNetworkValidatesPendingAccount() = runBlocking {
        val f = Fixture().apply { response = null }
        f.manager.save("new")
        f.response = true
        assertTrue(f.manager.validatePending())
        assertEquals(UsernameStatus.VALID, f.account.status)
        assertEquals(0, f.notifications)
    }

    @Test fun confirmedInvalidPendingAccountNotifiesOnlyOnce() = runBlocking {
        val f = Fixture().apply { response = null }
        f.manager.save("bad")
        f.response = false
        assertTrue(f.manager.validatePending())
        assertTrue(f.manager.validatePending())
        assertEquals(UsernameStatus.INVALID, f.account.status)
        assertEquals(1, f.notifications)
        assertEquals(1, f.applications)
    }

    @Test fun accountChangedDuringValidationIgnoresStaleResult() = runBlocking {
        val f = Fixture().apply { response = null }
        f.manager.save("first")
        f.response = false
        f.duringLookup = { f.account = UsernameAccount("second", "new-revision", UsernameStatus.PENDING) }
        assertTrue(f.manager.validatePending())
        assertEquals("second", f.account.username)
        assertEquals(0, f.applications)
        assertEquals(0, f.notifications)
    }

    @Test fun sameUsernameWithNewRevisionDoesNotReceiveOldValidation() = runBlocking {
        val f = Fixture().apply { response = null }
        f.manager.save("same")
        f.response = false
        f.duringLookup = { f.account = f.account.copy(revision = "re-added") }
        assertTrue(f.manager.validatePending())
        assertEquals(UsernameStatus.PENDING, f.account.status)
        assertEquals(0, f.applications)
    }

    @Test fun existingValidAndEmptyAccountsDoNotNeedDeferredLookup() = runBlocking {
        val f = Fixture()
        f.duringLookup = { fail("Unexpected lookup") }
        assertTrue(f.manager.validatePending())
        f.account = UsernameAccount("", "", UsernameStatus.PENDING)
        assertTrue(f.manager.validatePending())
    }
}
