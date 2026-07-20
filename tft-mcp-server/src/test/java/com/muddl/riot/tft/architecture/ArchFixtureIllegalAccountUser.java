package com.muddl.riot.tft.architecture;

import com.muddl.riot.account.domain.RiotAccount;

/**
 * A deliberate architecture violation, used only as a negative control by {@link
 * HexagonalArchitectureNegativeControlTest}: a non-allowlisted context referencing the account
 * domain. Do not "fix" this class — its violation is the point.
 */
@SuppressWarnings("unused")
class ArchFixtureIllegalAccountUser {
    private RiotAccount account;
}
