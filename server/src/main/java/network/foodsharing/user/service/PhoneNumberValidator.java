package network.foodsharing.user.service;

import java.util.regex.Pattern;

/**
 * Phone number rules, ported from the reference app ({@code client/src/app/core/phone-number.ts}; the client keeps
 * the same rules): an optional leading {@code +} followed by digits and the separators {@code space ( ) / . -},
 * 7 to 15 digits in total. {@link #normalize} stores numbers in E.164 with Switzerland as default country
 * ({@code 079 123 45 67} → {@code +41791234567}, {@code 0049…} → {@code +49…}).
 */
public final class PhoneNumberValidator {

    private static final Pattern PHONE_CHARACTERS = Pattern.compile("^\\+?[0-9 ()/.-]+$");
    private static final int MIN_DIGITS = 7;
    private static final int MAX_DIGITS = 15;
    private static final String DEFAULT_COUNTRY_CODE = "41";

    private PhoneNumberValidator() {
    }

    public static boolean isValid(String value) {
        String trimmed = value == null ? "" : value.strip();
        if (!PHONE_CHARACTERS.matcher(trimmed).matches()) {
            return false;
        }
        int digits = trimmed.replaceAll("\\D", "").length();
        return digits >= MIN_DIGITS && digits <= MAX_DIGITS;
    }

    /** Empty and blank values are "no number" and therefore fine. */
    public static boolean isValidOrEmpty(String value) {
        return value == null || value.isBlank() || isValid(value);
    }

    /**
     * E.164 form of a valid number ({@code +<digits>}); null for blank input.
     *
     * @throws IllegalArgumentException when the number is not valid
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!isValid(value)) {
            throw new IllegalArgumentException("invalid phone number");
        }
        String trimmed = value.strip();
        String digits = trimmed.replaceAll("\\D", "");
        if (trimmed.startsWith("+")) {
            return "+" + digits;
        }
        if (digits.startsWith("00")) {
            return "+" + digits.substring(2);
        }
        if (digits.startsWith("0")) {
            return "+" + DEFAULT_COUNTRY_CODE + digits.substring(1);
        }
        return "+" + digits;
    }

    /** Swiss mobile numbers: +41 75–79 (used for hints only; landlines are valid too). */
    public static boolean isSwissMobile(String value) {
        String normalized = isValid(value) ? normalize(value) : null;
        return normalized != null && normalized.matches("^\\+417[5-9]\\d{7}$");
    }
}
