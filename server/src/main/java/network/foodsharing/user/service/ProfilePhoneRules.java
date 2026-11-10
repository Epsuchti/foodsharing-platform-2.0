package network.foodsharing.user.service;

/**
 * Mobile phone rule of a profile save (D2, plan §8.3): the mobile number is mandatory on the own profile, so an own
 * save may not clear it. A save that leaves the field out ({@code null}) keeps the stored value and is fine, and
 * administrators may still clear it ({@code PROFILE_UPDATED_BY_ADMIN}). Pickup eligibility stays per store
 * ({@code requiresPhone}). Lives next to {@link UserService} rather than in {@code profile.service.ProfileRules},
 * which depends on this module.
 */
public final class ProfilePhoneRules {

    private ProfilePhoneRules() {
    }

    /**
     * Whether the requested mobile number may be saved.
     *
     * @param own   the user changes their own profile
     * @param value the requested number; {@code null} = unchanged, blank = clear
     */
    public static boolean mobileOk(boolean own, String value) {
        if (value == null || !own) {
            return true;
        }
        return PhoneNumberValidator.isValid(value);
    }
}
