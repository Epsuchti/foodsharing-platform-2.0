package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.domain.PickupDayConversation;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupDayConversationRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.service.DayChatPort;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * PU3 day chat (decision 165, was PickupsApiIT): one chat per store and day; it needs somebody else signed up that day,
 * is created with the given name once, and opening it again keeps the name and only adds who is missing (the port
 * never removes anybody). Who may open it is DayChatRuleTest.
 */
class PickupDayConversationServiceTest {

    static final UUID STORE = UUID.randomUUID();
    static final UUID ME = UUID.randomUUID();
    static final UUID SARAH = UUID.randomUUID();
    static final UUID MARCO = UUID.randomUUID();
    static final LocalDate DAY = LocalDate.parse("2026-11-26");
    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    PickupDayConversationRepository mappings = mock(PickupDayConversationRepository.class);
    PickupSignupRepository signups = mock(PickupSignupRepository.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    DayChatPort chats = mock(DayChatPort.class);
    Store store = new Store();
    PickupDayConversationService service;

    @BeforeEach
    void setUp() {
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        StoreService stores = mock(StoreService.class);
        when(stores.require(STORE)).thenReturn(store);
        when(stores.lock(STORE)).thenReturn(store);
        PickupScheduleService schedule = mock(PickupScheduleService.class);
        when(schedule.zoneOf(store)).thenReturn(ZURICH);
        CurrentActorService actor = mock(CurrentActorService.class);
        when(actor.requirePrincipal()).thenReturn(new SessionPrincipal(ME, UUID.randomUUID(), Role.STORE_MANAGER,
                "me@test.local"));
        when(rights.teamStatus(ME, STORE)).thenReturn(UserTeamStatus.COORDINATOR);
        when(chats.name(any())).thenReturn("Abholung S-01, Do 26.11.");
        when(mappings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new PickupDayConversationService(mappings, signups, schedule, stores, rights, chats, actor,
                mock(HistoryService.class, RETURNS_DEEP_STUBS));
    }

    void signedUp(UUID... users) {
        Instant at = DAY.atTime(19, 0).atZone(ZURICH).toInstant();
        List<PickupSignup> list = new java.util.ArrayList<>();
        int i = 0;
        for (UUID user : users) {
            PickupSignup signup = new PickupSignup();
            signup.setUserId(user);
            signup.setPickupAt(at);
            signup.setCreatedAt(at.minusSeconds(3600L - i++));
            list.add(signup);
        }
        when(signups.findInRange(List.of(STORE), DAY.atStartOfDay(ZURICH).toInstant(),
                DAY.plusDays(1).atStartOfDay(ZURICH).toInstant())).thenReturn(list);
    }

    @Test
    void withoutAnybodyElseSignedUpThereIsNoChat() {
        signedUp(ME);
        assertThatThrownBy(() -> service.open(STORE, DAY, "Abholung")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.NO_SIGNUPS));
        verify(chats, never()).createGroup(any(), anyCollection(), anyString());
    }

    @Test
    void theFirstOpeningCreatesTheNamedChatWithEverybodySignedUp() {
        signedUp(SARAH, MARCO);
        when(chats.createGroup(eq(ME), anyCollection(), eq("Abholung S-01, Do 26.11."))).thenReturn(UUID.randomUUID());
        var opened = service.open(STORE, DAY, "  Abholung S-01, Do 26.11. ");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> members = ArgumentCaptor.forClass(Collection.class);
        verify(chats).createGroup(eq(ME), members.capture(), eq("Abholung S-01, Do 26.11."));
        assertThat(members.getValue()).containsExactly(SARAH, MARCO);
        assertThat(opened.created()).isTrue();
        assertThat(opened.addedMembers()).isEqualTo(3);
        ArgumentCaptor<PickupDayConversation> mapping = ArgumentCaptor.forClass(PickupDayConversation.class);
        verify(mappings).save(mapping.capture());
        assertThat(mapping.getValue().getPickupDate()).isEqualTo(DAY);
        assertThat(mapping.getValue().getStoreId()).isEqualTo(STORE);
    }

    @Test
    void openingItAgainKeepsTheChatAndItsNameAndOnlyAddsWhoIsMissing() {
        signedUp(SARAH);
        PickupDayConversation existing = new PickupDayConversation();
        existing.setConversationId(UUID.randomUUID());
        when(mappings.findByStoreIdAndPickupDate(STORE, DAY)).thenReturn(Optional.of(existing));
        when(chats.addMissingMembers(eq(existing.getConversationId()), anyCollection())).thenReturn(1);
        var opened = service.open(STORE, DAY, "Anderer Name");
        assertThat(opened.created()).isFalse();
        assertThat(opened.conversationId()).isEqualTo(existing.getConversationId());
        assertThat(opened.name()).isEqualTo("Abholung S-01, Do 26.11.");
        assertThat(opened.addedMembers()).isEqualTo(1);
        verify(chats).addMissingMembers(existing.getConversationId(), new java.util.LinkedHashSet<>(List.of(SARAH, ME)));
        verify(chats, never()).createGroup(any(), anyCollection(), anyString());
    }

    @Test
    void outsidersNotSignedUpThatDayAreRefused() {
        signedUp(SARAH);
        when(rights.teamStatus(ME, STORE)).thenReturn(UserTeamStatus.WAITING_LIST);
        assertThatThrownBy(() -> service.open(STORE, DAY, "x")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
