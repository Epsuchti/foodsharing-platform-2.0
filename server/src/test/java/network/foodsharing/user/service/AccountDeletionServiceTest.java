package network.foodsharing.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.user.domain.SleepStatus;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;

/** A40: anonymised columns and the archive snapshot. */
class AccountDeletionServiceTest {

    @Test
    void anonymiseClearsPersonalDataAndKeepsTheRow() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("anna@example.org");
        user.setDisplayName("Anna");
        user.setFirstName("Anna");
        user.setLastName("Muster");
        user.setMobilePhone("+41790000000");
        user.setStreet("Seestrasse 1");
        user.setLatitude(47.3);
        user.setRole(Role.AMBASSADOR);
        user.setVerified(true);
        user.setActive(true);
        user.setSleepStatus(SleepStatus.FULL);
        user.setTelegramChatId("42");
        String snapshot = AccountDeletionService.snapshot(user);
        UUID orga = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-10T10:00:00Z");

        AccountDeletionService.anonymise(user, orga, "Wunsch", now);

        assertThat(snapshot).contains("anna@example.org", "+41790000000", "Seestrasse 1", "AMBASSADOR");
        assertThat(user.getEmail()).isEqualTo("deleted-" + user.getId() + "@deleted.invalid");
        assertThat(user.getDisplayName()).isEqualTo(AccountDeletionService.DELETED_NAME);
        assertThat(user.getFirstName()).isNull();
        assertThat(user.getMobilePhone()).isNull();
        assertThat(user.getStreet()).isNull();
        assertThat(user.getLatitude()).isNull();
        assertThat(user.getTelegramChatId()).isNull();
        assertThat(user.getSleepStatus()).isEqualTo(SleepStatus.NONE);
        assertThat(user.getRole()).isEqualTo(Role.FOODSHARER);
        assertThat(user.isVerified()).isFalse();
        assertThat(user.isActive()).isFalse();
        assertThat(user.getDeletedAt()).isEqualTo(now);
        assertThat(user.getDeletedBy()).isEqualTo(orga);
        assertThat(user.getDeleteReason()).isEqualTo("Wunsch");
    }
}
