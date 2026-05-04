package com.minierp.shared.security;

import java.util.Optional;

public final class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private CurrentUserHolder() {}

    public static void set(CurrentUser user) {
        CURRENT.set(user);
    }

    public static Optional<CurrentUser> tryGet() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static CurrentUser require() {
        CurrentUser user = CURRENT.get();
        if (user == null) {
            throw new IllegalStateException("No authenticated user on this thread");
        }
        return user;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
