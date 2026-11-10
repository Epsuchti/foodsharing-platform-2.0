package network.foodsharing.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** D2: the mobile number is mandatory on the own profile; partial saves and admin edits are not affected. */
class ProfilePhoneRulesTest {

    @Test
    void ownSaveThatClearsTheMobileNumberIsRefused() {
        assertThat(ProfilePhoneRules.mobileOk(true, "")).isFalse();
        assertThat(ProfilePhoneRules.mobileOk(true, "   ")).isFalse();
    }

    @Test
    void ownSaveWithAValidNumberIsAllowed() {
        assertThat(ProfilePhoneRules.mobileOk(true, "079 123 45 67")).isTrue();
        assertThat(ProfilePhoneRules.mobileOk(true, "+41791234567")).isTrue();
    }

    @Test
    void ownPartialSaveWithoutTheFieldIsAllowed() {
        assertThat(ProfilePhoneRules.mobileOk(true, null)).isTrue();
    }

    @Test
    void adminMayClearTheMobileNumber() {
        assertThat(ProfilePhoneRules.mobileOk(false, "")).isTrue();
        assertThat(ProfilePhoneRules.mobileOk(false, null)).isTrue();
    }
}
