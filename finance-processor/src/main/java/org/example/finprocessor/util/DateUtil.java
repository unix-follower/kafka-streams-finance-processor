package org.example.finprocessor.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class DateUtil {
    private DateUtil() {
    }

    public static OffsetDateTime estToUtc(LocalDateTime date) {
        return ZonedDateTime.of(date, ZoneOffset.of("-5"))
            .withZoneSameInstant(ZoneOffset.UTC)
            .toOffsetDateTime();
    }
}
