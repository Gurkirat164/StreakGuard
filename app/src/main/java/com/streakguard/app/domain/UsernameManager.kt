package com.streakguard.app.domain

enum class UsernameStatus { PENDING, VALID, INVALID }
data class UsernameAccount(val username: String, val revision: String, val status: UsernameStatus)
enum class UsernameSaveResult { SAVED, PENDING, INVALID }

/** An unavailable server never counts as proof that a profile is invalid. */
class UsernameManager(
    private val profileExists: suspend (String) -> Boolean?,
    private val saveAccount: suspend (String, Boolean) -> Unit,
    private val readAccount: suspend () -> UsernameAccount,
    private val completeValidation: suspend (UsernameAccount, Boolean) -> Unit,
    private val enqueueRefresh: () -> Unit,
) {
    suspend fun save(username: String): UsernameSaveResult {
        val exists = profileExists(username)
        if (exists == false) return UsernameSaveResult.INVALID
        saveAccount(username, exists == true)
        enqueueRefresh()
        return if (exists == true) UsernameSaveResult.SAVED else UsernameSaveResult.PENDING
    }

    /** False requests a network retry. Account revisions also protect change-away-and-back. */
    suspend fun validatePending(): Boolean {
        val account = readAccount()
        if (account.username.isBlank() || account.status != UsernameStatus.PENDING) return true
        val exists = profileExists(account.username) ?: return false
        if (readAccount() == account) completeValidation(account, exists)
        return true
    }
}
