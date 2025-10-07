package com.onemount.javahexagonal.application.enums;

import lombok.Getter;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum StatusEnums {
    INIT(0, "INIT"),
    ACTIVE(1, "ACTIVE"),
    INACTIVE(2, "INACTIVE"),
    REJECT(3, "REJECT"),
    BLOCKED(4, "BLOCKED"),
    WAIT_FOR_CLOSE(5, "WAIT_FOR_CLOSE"),
    CLOSED(6, "CLOSED");

    private final int id;
    private final String value;

    StatusEnums(int id, String value) {
        this.id = id;
        this.value = value;
    }

    // --- Generic map builder ---
    private static <K> Map<K, StatusEnums> buildLookupMap(Function<StatusEnums, K> keyExtractor) {
        return Stream.of(values())
                .collect(Collectors.toUnmodifiableMap(keyExtractor, Function.identity()));
    }

    // --- Static immutable lookup maps ---
    private static final Map<Integer, StatusEnums> ID_MAP = buildLookupMap(StatusEnums::getId);
    private static final Map<String, StatusEnums> VALUE_MAP = buildLookupMap(StatusEnums::getValue);

    // --- Lookup methods ---
    public static StatusEnums findById(int id, StatusEnums defaultValue) {
        return ID_MAP.getOrDefault(id, defaultValue);
    }

    public static StatusEnums findById(int id) {
        return findById(id, null);
    }

    public static StatusEnums findByValue(String value, StatusEnums defaultValue) {
        return VALUE_MAP.getOrDefault(value, defaultValue);
    }

    public static StatusEnums findByValue(String value) {
        return findByValue(value, null);
    }
}
