package com.muddl.riot.lol.architecture;

import com.muddl.riot.account.domain.RiotAccount;

/**
 * A deliberate architecture violation, used only as a negative control by {@link
 * HexagonalArchitectureNegativeControlTest}.
 *
 * <p>It reaches into the shared account domain from a package outside the allowlist, which {@code
 * only_analytics_and_the_account_tool_use_the_account_domain} forbids. It lives in test sources, so
 * {@code ImportOption.DoNotIncludeTests} keeps it out of the real scan in {@link
 * HexagonalArchitectureTest} — it can never fail the production rule. The negative control imports
 * it explicitly by class, which bypasses that filter.
 *
 * <p>Do not "fix" this class. Its violation is the point: it is the only evidence that the rule
 * still fails when it should, as opposed to passing vacuously.
 */
@SuppressWarnings("unused")
class ArchFixtureIllegalAccountUser {

    /** The forbidden dependency: a non-allowlisted context referencing the account domain. */
    private RiotAccount account;
}
