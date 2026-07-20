package com.muddl.riot.tft.architecture;

import com.muddl.riot.account.identity.PlayerIdentityResolver;

/**
 * A deliberately non-allowlisted context depending on {@link PlayerIdentityResolver}. This is
 * <em>legal</em>: identity resolution is the open surface of the account library. Do not "fix" this
 * class — its legality is the point.
 */
@SuppressWarnings("unused")
class ArchFixtureLegalResolverUser {
    private PlayerIdentityResolver resolver;
}
