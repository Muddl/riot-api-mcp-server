package com.muddl.riot.lol.architecture;

import com.muddl.riot.account.identity.PlayerIdentityResolver;

/**
 * A deliberately non-allowlisted context that depends on {@link PlayerIdentityResolver}. Unlike
 * {@link ArchFixtureIllegalAccountUser}, this dependency is <em>legal</em>: identity resolution is
 * the open surface of the account library, so the split rule {@code
 * only_analytics_and_the_account_tool_use_the_account_domain} must NOT flag it. {@link
 * HexagonalArchitectureNegativeControlTest} asserts exactly that — it is the positive control for
 * the "identity is open" half of the split.
 *
 * <p>Do not "fix" this class by removing the dependency. Its legality is the point: if the rule ever
 * starts flagging it, the split has collapsed back into blanket confinement.
 */
@SuppressWarnings("unused")
class ArchFixtureLegalResolverUser {

    /** The allowed dependency: a non-allowlisted context using the open identity resolver. */
    private PlayerIdentityResolver resolver;
}
