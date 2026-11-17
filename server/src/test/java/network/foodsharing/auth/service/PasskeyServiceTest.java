package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.PasskeyCredential;
import network.foodsharing.auth.domain.PasskeyUserEntity;
import network.foodsharing.auth.repository.PasskeyCredentialRepository;
import network.foodsharing.auth.repository.PasskeyUserEntityRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.MutableClock;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Registering and using passkeys (was PasskeyRepositoriesIT): owner check, default label, use updates, history. */
class PasskeyServiceTest {

    private final PasskeyUserEntityRepository userEntities = mock(PasskeyUserEntityRepository.class);
    private final PasskeyCredentialRepository credentials = mock(PasskeyCredentialRepository.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final MutableClock clock = new FixedClockConfig().clock();
    private final UUID me = UUID.randomUUID();
    private PasskeyService service;

    @BeforeEach
    void setUp() {
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        when(credentials.findByCredentialId(any())).thenReturn(Optional.empty());
        when(credentials.save(any())).thenAnswer(invocation -> {
            PasskeyCredential credential = invocation.getArgument(0);
            credential.setId(UUID.randomUUID());
            return credential;
        });
        service = new PasskeyService(userEntities, credentials, mock(UserService.class), actors, history, clock);
    }

    @Test
    void registrationNeedsASession() {
        when(actors.requireUserId()).thenThrow(ApiException.unauthenticated());
        assertThatThrownBy(() -> service.saveCredential(data("handle", "YubiKey")))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(401));
        verify(credentials, never()).save(any());
    }

    @Test
    void aForeignOrUnknownUserHandleIsRefused() {
        when(actors.requireUserId()).thenReturn(me);
        when(userEntities.findByHandle("unknown")).thenReturn(Optional.empty());
        when(userEntities.findByHandle("foreign")).thenReturn(Optional.of(owner(UUID.randomUUID(), "foreign")));
        for (String handle : new String[] {"unknown", "foreign"}) {
            assertThatThrownBy(() -> service.saveCredential(data(handle, "x")))
                    .isInstanceOfSatisfying(ApiException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        }
        verify(credentials, never()).save(any());
    }

    @Test
    void registrationStoresTheCredentialWithADefaultLabelAndRecordsIt() {
        when(actors.requireUserId()).thenReturn(me);
        when(userEntities.findByHandle("mine")).thenReturn(Optional.of(owner(me, "mine")));

        PasskeyCredential saved = service.saveCredential(data("mine", "   "));
        assertThat(saved.getUserId()).isEqualTo(me);
        assertThat(saved.getLabel()).isEqualTo(PasskeyService.DEFAULT_LABEL);
        assertThat(saved.getSignatureCount()).isEqualTo(7);
        assertThat(saved.getTransports()).isEqualTo("hybrid,usb");
        assertThat(saved.isBackupEligible()).isTrue();
        assertThat(service.saveCredential(data("mine", "  Phone ")).getLabel()).isEqualTo("Phone");
        verify(history).record(argThat(event -> event.action() == AuthAction.PASSKEY_REGISTERED
                && event.subjectId().equals(me) && "Passkey".equals(event.metadata().get("label"))));
    }

    @Test
    void anAuthenticationUpdatesCounterFlagsAndLastUseWithoutASession() {
        PasskeyCredential existing = new PasskeyCredential();
        existing.setUserId(me);
        existing.setSignatureCount(7);
        when(credentials.findByCredentialId("cred")).thenReturn(Optional.of(existing));

        PasskeyCredentialData used = new PasskeyCredentialData("cred", "mine", "key", 8, null, null, null, true, true,
                true, null, null);
        assertThat(service.saveCredential(used)).isSameAs(existing);
        assertThat(existing.getSignatureCount()).isEqualTo(8);
        assertThat(existing.isBackupState()).isTrue();
        assertThat(existing.getLastUsedAt()).isEqualTo(clock.instant());
        verify(actors, never()).requireUserId();
        verify(history, never()).record(any());
    }

    @Test
    void theUserHandleIsStoredOnce() {
        PasskeyUserEntity known = owner(me, "first");
        when(userEntities.findByUserId(me)).thenReturn(Optional.of(known));
        assertThat(service.saveUserEntity(me, "second")).isSameAs(known);
        verify(userEntities, never()).save(any());
    }

    private static PasskeyCredentialData data(String handle, String label) {
        return new PasskeyCredentialData("cred-" + UUID.randomUUID(), handle, "AQIDBAU", 7, "hybrid,usb", label,
                "public-key", true, true, false, "att", "client");
    }

    private static PasskeyUserEntity owner(UUID userId, String handle) {
        PasskeyUserEntity entity = new PasskeyUserEntity();
        entity.setUserId(userId);
        entity.setHandle(handle);
        return entity;
    }
}
