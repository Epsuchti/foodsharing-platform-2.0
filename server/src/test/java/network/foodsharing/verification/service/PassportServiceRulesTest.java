package network.foodsharing.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import network.foodsharing.verification.domain.PassportIssue;
import network.foodsharing.verification.repository.PassportIssueRepository;
import network.foodsharing.verification.repository.PassportRequestRepository;
import network.foodsharing.verification.service.PassportService.Availability;
import network.foodsharing.verification.service.PassportService.StatusView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Ve3 own passport rules the old e2e spec asserted (decision 180): only verified users get a passport (403 NOT_VERIFIED,
 * "locked"), an expired one is not downloadable until renewed (409 PASSPORT_NOT_VALID), and the wallets say
 * NOT_CONFIGURED while they are switched off (local, e2e) and NOT_VERIFIED for unverified users; a wallet pass then
 * answers 503 WALLET_NOT_CONFIGURED.
 */
class PassportServiceRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private final PassportIssueRepository issues = mock(PassportIssueRepository.class);
    private final PassportRequestRepository requests = mock(PassportRequestRepository.class);
    private final UserService users = mock(UserService.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final AppProperties properties = new AppProperties();
    private final WalletConfiguration wallets = new WalletConfiguration(properties);
    private final PassportService passports = new PassportService(issues, requests, null, wallets, users, null, null,
            currentActor, null, null, null, null, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    private final WalletService walletService = new WalletService(wallets, null, passports, null,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final User user = TestUsers.user("sleepy@local", Role.FOODSAVER);

    PassportServiceRulesTest() {
        user.setId(UUID.randomUUID());
        when(users.require(user.getId())).thenReturn(user);
        when(currentActor.requireUserId()).thenReturn(user.getId());
    }

    private void issued(LocalDate from, LocalDate until) {
        PassportIssue issue = new PassportIssue();
        issue.setUserId(user.getId());
        issue.setValidFrom(from);
        issue.setValidUntil(until);
        when(issues.findFirstByUserIdOrderByValidUntilDescCreatedAtDescIdDesc(user.getId())).thenReturn(Optional.of(issue));
    }

    private static ApiException thrown(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("expected an ApiException");
    }

    @Test
    void unverifiedUsersGetNoPassportAndTheWalletsSayNotVerified() {
        issued(LocalDate.of(2026, 1, 1), LocalDate.of(2029, 1, 1));
        assertThat(thrown(passports::requireVerifiedAndValid).getCode()).isEqualTo(ApiErrorCode.NOT_VERIFIED);
        assertThat(thrown(passports::requireVerifiedAndValid).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        StatusView status = passports.myStatus();
        assertThat(status.verified()).isFalse();
        assertThat(status.apple()).isEqualTo(Availability.NOT_VERIFIED);
        assertThat(status.google()).isEqualTo(Availability.NOT_VERIFIED);
    }

    @Test
    void anExpiredPassportIsNotDownloadableUntilRenewed() {
        user.setVerified(true);
        issued(LocalDate.of(2023, 10, 1), LocalDate.of(2026, 10, 1));
        ApiException expired = thrown(passports::requireVerifiedAndValid);
        assertThat(expired.getCode()).isEqualTo(ApiErrorCode.PASSPORT_NOT_VALID);
        assertThat(expired.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(passports.myStatus().valid()).isFalse();

        issued(LocalDate.of(2026, 11, 2), LocalDate.of(2029, 11, 2));
        assertThat(passports.requireVerifiedAndValid()).isSameAs(user);
        assertThat(passports.myStatus().valid()).isTrue();
    }

    @Test
    void aPendingRequestIsShownInTheStatus() {
        user.setVerified(true);
        when(requests.existsByUserIdAndFulfilledAtIsNull(user.getId())).thenReturn(true);
        assertThat(passports.myStatus().requestPending()).isTrue();
    }

    @Test
    void switchedOffWalletsAreNotConfiguredAndAPassAnswers503() {
        user.setVerified(true);
        issued(LocalDate.of(2026, 1, 1), LocalDate.of(2029, 1, 1));
        StatusView status = passports.myStatus();
        assertThat(status.apple()).isEqualTo(Availability.NOT_CONFIGURED);
        assertThat(status.google()).isEqualTo(Availability.NOT_CONFIGURED);
        for (Runnable pass : new Runnable[] {walletService::applePass, walletService::googleSaveUrl}) {
            ApiException off = thrown(pass);
            assertThat(off.getCode()).isEqualTo(ApiErrorCode.WALLET_NOT_CONFIGURED);
            assertThat(off.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
