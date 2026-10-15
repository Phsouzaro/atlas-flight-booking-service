package com.atlas.booking.internal;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** Localizador de 6 caracteres, sem os que se confundem ao ler (0/O, 1/I/L). */
@Component
class BookingCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();
    private final BookingRepository bookings;

    BookingCodeGenerator(BookingRepository bookings) {
        this.bookings = bookings;
    }

    String next() {
        // 31^6 ≈ 887 milhões de códigos: colisão é rara, mas o índice único é que decide
        String code;
        do {
            StringBuilder sb = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (bookings.existsByCode(code));
        return code;
    }
}
