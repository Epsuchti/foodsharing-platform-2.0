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
import network.foodsharing.auth.repository.AuthIdentityRepository;
import network.foodsharing.auth.repository.PasskeyCredentialRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Passkeys in the login methods (was PasskeyRepositoriesIT): rename, delete, never the last login method. */
class AuthMethodsServiceTest {

    private final AuthIdentityRepository identities = mock(AuthIdentityRepository.class);
    private final PasskeyCredentialRepository passkeys = mock(PasskeyCredentialRepository.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final UUID me = UUID.randomUUID();
    private final PasskeyCredential phone = new PasskeyCredential();
    private AuthMethodsService service;

    @BeforeEach
    void setUp() {
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        when(actors.requireUserId()).thenReturn(me);
        phone.setId(UUID.randomUUID());
        phone.setUserId(me);
        phone.setLabel("Phone");
        when(passkeys.findByIdAndUserId(any(), any())).thenAnswer(invocation ->
                phone.getId().equals(invocation.getArgument(0)) && me.equals(invocation.getArgument(1))
                        ? Optional.of(phone) : Optional.empty());
        service = new AuthMethodsService(identities, passkeys, actors, history);
    }

    @Test
    void renameStripsTheLabelAndRecordsTheChange() {
        assertThat(service.renamePasskey(phone.getId(), "  Laptop ").getLabel()).isEqualTo("Laptop");
        verify(history).record(argThat(event -> event.action() == AuthAction.PASSKEY_RENAMED
                && phone.getId().equals(event.secondarySubjectId()) && "Phone".equals(event.oldValue())
                && "Laptop".equals(event.newValue())));
    }

    @Test
    void anUnchangedNameRecordsNothing() {
        service.renamePasskey(phone.getId(), "Phone ");
        verify(history, never()).record(any());
    }

    @Test
    void someoneElsesOrAnUnknownPasskeyIsNotFound() {
        assertThatThrownBy(() -> service.renamePasskey(UUID.randomUUID(), "x"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.PASSKEY_NOT_FOUND));
        when(actors.requireUserId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.deletePasskey(phone.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.PASSKEY_NOT_FOUND));
        verify(passkeys, never()).delete(any());
    }

    @Test
    void aPasskeyIsDeletedWhileAnotherLoginMethodRemains() {
        when(identities.countByUserId(me)).thenReturn(0L);
        when(passkeys.countByUserId(me)).thenReturn(2L);
        service.deletePasskey(phone.getId());
        verify(passkeys).delete(phone);
        verify(history).record(argThat(event -> event.action() == AuthAction.PASSKEY_DELETED
                && "Phone".equals(event.metadata().get("label"))));
    }

    @Test
    void theLastLoginMethodCannotBeRemoved() {
        when(identities.countByUserId(me)).thenReturn(0L);
        when(passkeys.countByUserId(me)).thenReturn(1L);
        assertThatThrownBy(() -> service.deletePasskey(phone.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.LAST_LOGIN_METHOD_CANNOT_BE_REMOVED));
        verify(passkeys, never()).delete(any());
        verify(history, never()).record(any());
    }
}
