package com.petcommerce.user.domain.policy;

import java.util.Locale;

/** Business rule: emails are stored and compared trimmed and lower-cased. */
public final class EmailPolicy {

    private EmailPolicy() {
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
