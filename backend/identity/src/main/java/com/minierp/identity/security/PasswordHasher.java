package com.minierp.identity.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65_536;
    private static final int PARALLELISM = 1;

    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    public String hash(char[] password) {
        try {
            return argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, password);
        } finally {
            argon2.wipeArray(password);
        }
    }

    public String hash(String password) {
        return hash(password.toCharArray());
    }

    public boolean verify(String hash, char[] password) {
        try {
            return argon2.verify(hash, password);
        } finally {
            argon2.wipeArray(password);
        }
    }

    public boolean verify(String hash, String password) {
        return verify(hash, password.toCharArray());
    }
}
