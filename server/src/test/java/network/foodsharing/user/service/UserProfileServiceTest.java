package network.foodsharing.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.ContactVisibility;
import network.foodsharing.user.domain.Gender;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Own profile changes (plan §8.3), admin edits with reason (A4/AD1), enabling/disabling accounts. */
@JpaSliceTest
@Import(UserService.class)
class UserProfileServiceTest {

    @Autowired
    private UserService service;

    @Autowired
    private UserRepository users;

    @Autowired
    private HistoryEntryRepository history;

    private User user;
    private User orga;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(TestUsers.user("lea@test.local", Role.FOODSHARER));
        orga = users.saveAndFlush(TestUsers.user("orga@test.local", Role.ORGA));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private static ProfileChange change(String firstName, String mobile, String street, Gender gender) {
        return new ProfileChange(firstName, null, null, gender, null, mobile, null, null, null, street, null, null,
                null, null, null, null, null, null, null, null);
    }

    @Test
    void ownProfileChangesAreRecordedPerFieldWithRedactedPhone() {
        TestAuth.login(TestUsers.principal(user));

        service.updateMyProfile(new ProfileChange("Lea", "Brunner", "Lea B.", Gender.FEMALE, LocalDate.of(2000, 1, 2),
                "079 123 45 67", "", "https://lea.example", ContactVisibility.PUBLIC, "Weststrasse 1", "8003",
                "Zürich", "ch", 47.37, 8.51, "Hallo", "intern", "Neu", true, null));

        User saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.getMobilePhone()).isEqualTo("+41791234567");
        assertThat(saved.getLandlinePhone()).isNull();
        assertThat(saved.getCountry()).isEqualTo("CH");
        assertThat(saved.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(saved.getContactVisibility()).isEqualTo(ContactVisibility.PUBLIC);
        assertThat(saved.isNewsletterOptIn()).isTrue();

        List<HistoryEntry> entries = history.findAll();
        assertThat(entries).filteredOn(entry -> "mobilePhone".equals(entry.getField())).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo("PHONE_CHANGED");
                    assertThat(entry.getNewValue()).isEqualTo("***");
                });
        assertThat(entries).filteredOn(entry -> "dateOfBirth".equals(entry.getField())).singleElement()
                .extracting(HistoryEntry::getNewValue).isEqualTo("***");
        assertThat(entries).filteredOn(entry -> "street".equals(entry.getField())).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo("PROFILE_UPDATED");
                    assertThat(entry.getNewValue()).isEqualTo("Weststrasse 1");
                });
        // firstName/lastName unchanged values are not recorded; FIELD_CHANGED is not recorded twice
        assertThat(entries).noneMatch(entry -> entry.getAction().equals("FIELD_CHANGED")
                && "street".equals(entry.getField()));
    }

    @Test
    void invalidPhoneNumbersAre400WithFieldErrors() {
        TestAuth.login(TestUsers.principal(user));
        assertThatThrownBy(() -> service.updateMyProfile(change(null, "12ab", null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.INVALID_PHONE_NUMBER);
                    assertThat(e.getStatus().value()).isEqualTo(400);
                    assertThat(e.getFieldErrors()).singleElement().satisfies(error ->
                            assertThat(error.field()).isEqualTo("mobilePhone"));
                });
        assertThat(users.findById(user.getId()).orElseThrow().getMobilePhone()).isNull();
    }

    @Test
    void blankDisplayNameIsRefusedAndEmptyStringsClearFields() {
        TestAuth.login(TestUsers.principal(user));
        assertThatThrownBy(() -> service.updateMyProfile(new ProfileChange(null, null, " ", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        service.updateMyProfile(change("", null, null, null));
        assertThat(users.findById(user.getId()).orElseThrow().getFirstName()).isNull();
    }

    @Test
    void adminEditsNeedPermissionAndAReason() {
        User other = users.saveAndFlush(TestUsers.user("other@test.local", Role.FOODSAVER));
        TestAuth.login(TestUsers.principal(other));
        assertThatThrownBy(() -> service.updateProfileAsAdmin(user.getId(), change("X", null, null, null), "why"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));

        TestAuth.login(TestUsers.principal(orga));
        assertThatThrownBy(() -> service.updateProfileAsAdmin(user.getId(), change("X", null, null, null), " "))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));

        service.updateProfileAsAdmin(user.getId(), change("Lena", "+41 79 000 00 00", null, Gender.FEMALE),
                "typo in the first name");

        assertThat(users.findById(user.getId()).orElseThrow().getFirstName()).isEqualTo("Lena");
        assertThat(history.findAll()).filteredOn(entry -> "firstName".equals(entry.getField())).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo("PROFILE_UPDATED_BY_ADMIN");
                    assertThat(entry.getReason()).isEqualTo("typo in the first name");
                    assertThat(entry.getActorId()).isEqualTo(orga.getId());
                });
        assertThat(history.findAll()).filteredOn(entry -> "mobilePhone".equals(entry.getField())).singleElement()
                .extracting(HistoryEntry::getAction).isEqualTo("PROFILE_UPDATED_BY_ADMIN");
    }

    @Test
    void homeRegionNeedsTheRegionModule() {
        TestAuth.login(TestUsers.principal(orga));
        // without a RegionDirectory (slice test) every region is unknown
        assertThatThrownBy(() -> service.updateProfileAsAdmin(user.getId(), ProfileChange.admin(null, null, null, null,
                null, null, null, null, java.util.UUID.randomUUID()), "move"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.REGION_NOT_FOUND));
    }

    @Test
    void deactivatingAndActivatingIsRecordedAndNeverOnOneself() {
        TestAuth.login(TestUsers.principal(orga));
        assertThatThrownBy(() -> service.setActive(orga.getId(), false, "test"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.CANNOT_DEACTIVATE_SELF));

        service.setActive(user.getId(), false, "spam account");
        assertThat(users.findById(user.getId()).orElseThrow().isActive()).isFalse();
        service.setActive(user.getId(), true, "mistake");
        assertThat(history.findAll()).extracting(HistoryEntry::getAction).contains("DEACTIVATED", "ACTIVATED");
        assertThat(history.findAll()).filteredOn(entry -> entry.getAction().equals("DEACTIVATED")).singleElement()
                .extracting(HistoryEntry::getReason).isEqualTo("spam account");
    }

    @Test
    void contactVisibility() {
        TestAuth.login(TestUsers.principal(orga));
        assertThat(service.mayCurrentUserSeeContact(user)).isTrue();
        User other = users.saveAndFlush(TestUsers.user("viewer@test.local", Role.FOODSAVER));
        TestAuth.login(TestUsers.principal(other));
        assertThat(service.mayCurrentUserSeeContact(user)).isFalse();
        user.setContactVisibility(ContactVisibility.PUBLIC);
        assertThat(service.mayCurrentUserSeeContact(user)).isTrue();
        assertThat(service.mayCurrentUserSeeContact(other)).isTrue();
    }

    @Test
    void inactiveAccountsAreWarnedOncePerInactivityPeriod() {
        java.time.Instant now = java.time.Instant.parse("2026-06-01T00:00:00Z");
        user.setLastLoginAt(now.minus(java.time.Duration.ofDays(400)));
        orga.setLastLoginAt(now.minus(java.time.Duration.ofDays(400)));
        orga.setNoAutomaticDelete(true);
        users.saveAndFlush(user);
        users.saveAndFlush(orga);
        java.time.Instant cutoff = now.minus(java.time.Duration.ofDays(365));
        assertThat(service.inactiveUnwarned(cutoff)).extracting(User::getId).containsExactly(user.getId());

        service.markInactivityWarned(user.getId());
        users.flush();
        assertThat(service.inactiveUnwarned(cutoff)).isEmpty();
        assertThat(history.findAll()).extracting(HistoryEntry::getAction).contains("INACTIVITY_WARNED");
    }
}
