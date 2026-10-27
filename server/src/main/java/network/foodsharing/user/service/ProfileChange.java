package network.foodsharing.user.service;

import java.time.LocalDate;
import java.util.UUID;
import network.foodsharing.user.domain.ContactVisibility;
import network.foodsharing.user.domain.Gender;

/**
 * Profile fields to change (own profile or admin edit). {@code null} = unchanged; an empty string clears a text
 * field. Admin edits only use the admin-editable subset (names, address, mobile phone, date of birth, gender, home
 * region).
 */
public record ProfileChange(
        String firstName,
        String lastName,
        String displayName,
        Gender gender,
        LocalDate dateOfBirth,
        String mobilePhone,
        String landlinePhone,
        String homepage,
        ContactVisibility contactVisibility,
        String street,
        String postcode,
        String city,
        String country,
        Double latitude,
        Double longitude,
        String aboutMePublic,
        String aboutMeInternal,
        String positionDescription,
        Boolean newsletterOptIn,
        UUID homeRegionId) {

    /** The admin-editable fields only (AdminUserProfileUpdate). */
    public static ProfileChange admin(String firstName, String lastName, String street, String postcode, String city,
                                      String mobilePhone, LocalDate dateOfBirth, Gender gender, UUID homeRegionId) {
        return new ProfileChange(firstName, lastName, null, gender, dateOfBirth, mobilePhone, null, null, null, street,
                postcode, city, null, null, null, null, null, null, null, homeRegionId);
    }
}
