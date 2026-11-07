package com.tulas.canteen.service;

import java.security.SecureRandom;

/** Easy-to-read random passwords (no 0/O, 1/l/I) for admins to hand to users. */
public final class TempPassword {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TempPassword() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        return sb.toString();
    }
}
