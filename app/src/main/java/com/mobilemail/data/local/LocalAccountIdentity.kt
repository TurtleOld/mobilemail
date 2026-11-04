package com.mobilemail.data.local

import com.mobilemail.data.preferences.SavedSession

/**
 * Builds the local identity of a mailbox account.
 *
 * Server-side account ids are assigned per JMAP server, so the same value can point to different
 * accounts on different servers. Local rows must therefore be keyed by the pair that uniquely
 * identifies an account for the user: the server URL and the login email.
 */
object LocalAccountIdentity {
    /**
     * Returns the local storage key for one account.
     *
     * @param server Server URL as stored in [SavedSession].
     * @param email Login email as stored in [SavedSession].
     * @return Key used as `localAccountId` in the local database.
     */
    fun of(server: String, email: String): String = "$server|$email"

    /**
     * Maps legacy server account ids to local keys for the data migration.
     *
     * An id is mapped only when exactly one saved account owns it. Ids shared by several saved
     * accounts are ambiguous and stay absent, so their cached rows are not given to a random
     * account.
     *
     * @param sessions Accounts saved on this device.
     * @return Server account id to local account key.
     */
    internal fun legacyOwners(sessions: List<SavedSession>): Map<String, String> {
        return sessions
            .groupBy { it.accountId }
            .mapNotNull { (accountId, owners) ->
                owners
                    .distinctBy { it.server to it.email }
                    .singleOrNull()
                    ?.let { accountId to of(it.server, it.email) }
            }
            .toMap()
    }
}
