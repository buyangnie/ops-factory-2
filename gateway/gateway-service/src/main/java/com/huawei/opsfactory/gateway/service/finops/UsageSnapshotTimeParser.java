/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.finops;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Normalizes timestamp values stored by goosed session databases.
 *
 * @since 2026-05-28
 */
final class UsageSnapshotTimeParser {

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
    );

    Instant parseInstant(Object value) {
        if (value == null) {
            return Instant.EPOCH;
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw > 9_999_999_999L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        }
        String text = value.toString();
        if (text.isBlank()) {
            return Instant.EPOCH;
        }
        Optional<Instant> parsedInstant = parseIsoInstant(text);
        if (parsedInstant.isPresent()) {
            return parsedInstant.get();
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            Optional<Instant> localDateTime = parseLocalDateTime(text, formatter);
            if (localDateTime.isPresent()) {
                return localDateTime.get();
            }
        }
        Optional<Long> epoch = parseLong(text);
        return epoch.map(raw -> raw > 9_999_999_999L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw))
            .orElse(Instant.EPOCH);
    }

    private Optional<Instant> parseIsoInstant(String text) {
        try {
            return Optional.of(Instant.parse(text));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private Optional<Instant> parseLocalDateTime(String text, DateTimeFormatter formatter) {
        try {
            return Optional.of(LocalDateTime.parse(text, formatter).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private Optional<Long> parseLong(String text) {
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
