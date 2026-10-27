package network.foodsharing.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Port of the reference rules (client/src/app/core/phone-number.ts) plus the E.164 normalisation. */
class PhoneNumberValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"+41 79 123 45 67", "079 123 45 67", "0791234567", "+41791234567", "044/123.45.67",
            "(044) 123-45-67", "1234567", "+491701234567", "123456789012345", "  079 123 45 67  "})
    void acceptsCommonFormats(String value) {
        assertThat(PhoneNumberValidator.isValid(value)).isTrue();
        assertThat(PhoneNumberValidator.isValidOrEmpty(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "1234567890123456", "+41 79 abc 45 67", "079 123 45 67 ext 3", "++41791234567",
            "41+791234567", "", " ", "phone"})
    void rejectsInvalidNumbers(String value) {
        assertThat(PhoneNumberValidator.isValid(value)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "079 123 45 67, +41791234567",
            "+41 79 123 45 67, +41791234567",
            "0041 79 123 45 67, +41791234567",
            "0049 170 1234567, +491701234567",
            "(044) 123-45-67, +41441234567",
            "+49 170 1234567, +491701234567"})
    void normalisesToE164WithSwitzerlandAsDefault(String input, String expected) {
        assertThat(PhoneNumberValidator.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"079 123 45 67, true", "075 123 45 67, true", "044 123 45 67, false", "+49 170 1234567, false"})
    void recognisesSwissMobileNumbers(String input, boolean mobile) {
        assertThat(PhoneNumberValidator.isSwissMobile(input)).isEqualTo(mobile);
    }

    @org.junit.jupiter.api.Test
    void emptyIsNoNumberAndInvalidCannotBeNormalised() {
        assertThat(PhoneNumberValidator.isValidOrEmpty(null)).isTrue();
        assertThat(PhoneNumberValidator.isValidOrEmpty("")).isTrue();
        assertThat(PhoneNumberValidator.isValid(null)).isFalse();
        assertThat(PhoneNumberValidator.normalize(" ")).isNull();
        assertThatThrownBy(() -> PhoneNumberValidator.normalize("12")).isInstanceOf(IllegalArgumentException.class);
    }
}
