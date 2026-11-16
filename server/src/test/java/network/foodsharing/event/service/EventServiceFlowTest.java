package network.foodsharing.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.event.domain.Event;
import network.foodsharing.event.domain.EventAction;
import network.foodsharing.event.domain.EventInvitation;
import network.foodsharing.event.domain.EventType;
import network.foodsharing.event.domain.InvitationStatus;
import network.foodsharing.event.repository.EventInvitationRepository;
import network.foodsharing.event.repository.EventLocationRepository;
import network.foodsharing.event.repository.EventRepository;
import network.foodsharing.event.repository.EventWallPostRepository;
import network.foodsharing.event.service.EventPermissionService.EventContext;
import network.foodsharing.event.service.EventViews.EventDraft;
import network.foodsharing.event.service.EventViews.LocationDraft;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * E1–E3 service flows without a database (formerly EventFlowIT): the author attends, "invite all members" invites
 * every other member once, a change rings the participants but not the editor, a delete removes the bells, an RSVP
 * of INVITED is refused and the RSVP closes 24 h after the end, non-members list public events only, and every step
 * is recorded in the history.
 */
class EventServiceFlowTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID REGION = UUID.randomUUID();
    static final UUID AUTHOR = UUID.randomUUID();
    static final UUID MEMBER_A = UUID.randomUUID();
    static final UUID MEMBER_B = UUID.randomUUID();

    EventRepository events = mock(EventRepository.class);
    EventLocationRepository locations = mock(EventLocationRepository.class);
    EventInvitationRepository invitations = mock(EventInvitationRepository.class);
    EventWallPostRepository wallPosts = mock(EventWallPostRepository.class);
    EventPermissionService access = mock(EventPermissionService.class);
    EventNotifier notifier = mock(EventNotifier.class);
    RegionService regions = mock(RegionService.class);
    HistoryService history = mock(HistoryService.class);
    List<EventInvitation> saved = new ArrayList<>();
    EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(events, locations, invitations, wallPosts, access, notifier, regions,
                mock(GeocodingClient.class), mock(UserService.class), mock(CurrentActorService.class), history,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
        when(events.save(any())).thenAnswer(call -> {
            Event event = call.getArgument(0);
            if (event.getId() == null) {
                event.setId(UUID.randomUUID());
            }
            return event;
        });
        when(locations.save(any())).thenAnswer(call -> call.getArgument(0));
        when(invitations.save(any())).thenAnswer(call -> {
            EventInvitation invitation = call.getArgument(0);
            saved.remove(invitation);
            saved.add(invitation);
            return invitation;
        });
        when(invitations.findByEventIdOrderByCreatedAtAscIdAsc(any())).thenAnswer(call -> saved.stream()
                .filter(i -> i.getEventId().equals(call.getArgument(0))).toList());
        when(invitations.findByEventIdIn(any())).thenAnswer(call -> saved.stream()
                .filter(i -> ((Collection<?>) call.getArgument(0)).contains(i.getEventId())).toList());
    }

    static EventRules.Actor member(UUID id) {
        return new EventRules.Actor(id, false, true, false);
    }

    private List<EventAction> recordedActions() {
        ArgumentCaptor<HistoryEvent> captor = ArgumentCaptor.forClass(HistoryEvent.class);
        verify(history, org.mockito.Mockito.atLeastOnce()).record(captor.capture());
        return captor.getAllValues().stream().map(e -> (EventAction) e.action()).toList();
    }

    private static EventDraft draft(EventType type, Instant start, Instant end, LocationDraft location) {
        return new EventDraft(type, "Velotour", null, start, end, false, location, null);
    }

    @Test
    void createMakesTheAuthorAttendAndInvitesEveryOtherMemberOnce() {
        when(access.currentActor(REGION)).thenReturn(member(AUTHOR));
        when(regions.activeMemberIds(REGION)).thenReturn(List.of(AUTHOR, MEMBER_A, MEMBER_B));

        var details = service.create(REGION, draft(EventType.OFFLINE, NOW.plusSeconds(3600), NOW.plusSeconds(7200),
                new LocationDraft("Bürkliplatz", null, "8001", "Zürich", 47.36, 8.54)), true);

        assertThat(saved).extracting(EventInvitation::getUserId).containsExactly(AUTHOR, MEMBER_A, MEMBER_B);
        assertThat(saved.getFirst().getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(saved.subList(1, 3)).allMatch(i -> i.getStatus() == InvitationStatus.INVITED);
        ArgumentCaptor<Collection<UUID>> invited = ArgumentCaptor.captor();
        verify(notifier).invited(any(), invited.capture());
        assertThat(invited.getValue()).containsExactly(MEMBER_A, MEMBER_B);
        assertThat(details).isNotNull();
        assertThat(recordedActions()).containsExactly(EventAction.CREATED, EventAction.INVITATIONS_SENT);
    }

    @Test
    void nonMembersMayNotCreate() {
        when(access.currentActor(REGION)).thenReturn(new EventRules.Actor(AUTHOR, false, false, false));
        assertThatThrownBy(() -> service.create(REGION, draft(EventType.OTHER, NOW.plusSeconds(60),
                NOW.plusSeconds(120), null), false)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        verify(events, never()).save(any());
    }

    private Event stored(UUID author, Instant end) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setRegionId(REGION);
        event.setAuthorId(author);
        event.setType(EventType.OTHER);
        event.setName("Brunch");
        event.setStartsAt(end.minusSeconds(3600));
        event.setEndsAt(end);
        return event;
    }

    private void invitation(Event event, UUID user, InvitationStatus status) {
        EventInvitation invitation = new EventInvitation();
        invitation.setEventId(event.getId());
        invitation.setUserId(user);
        invitation.setStatus(status);
        saved.add(invitation);
    }

    @Test
    void aChangeRingsTheParticipantsButNotTheEditorAndNotTheInvited() {
        Event event = stored(AUTHOR, NOW.plusSeconds(7200));
        invitation(event, AUTHOR, InvitationStatus.ACCEPTED);
        invitation(event, MEMBER_A, InvitationStatus.ACCEPTED);
        invitation(event, MEMBER_B, InvitationStatus.INVITED);
        when(access.visibleEvent(event.getId())).thenReturn(new EventContext(event, member(AUTHOR)));

        service.update(event.getId(), new EventDraft(null, null, null, NOW.plusSeconds(1800), null, null, null, null));

        ArgumentCaptor<Collection<UUID>> rung = ArgumentCaptor.captor();
        verify(notifier).updated(eq(event), rung.capture());
        assertThat(rung.getValue()).containsExactly(MEMBER_A);
        assertThat(recordedActions()).containsExactly(EventAction.UPDATED);
    }

    @Test
    void onlyEditorsChangeOrDeleteAndADeleteRemovesTheBells() {
        Event event = stored(AUTHOR, NOW.plusSeconds(7200));
        when(access.visibleEvent(event.getId())).thenReturn(new EventContext(event, member(MEMBER_A)));
        assertThatThrownBy(() -> service.update(event.getId(), new EventDraft(null, "Neu", null, null, null, null,
                null, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.delete(event.getId())).isInstanceOf(ApiException.class);
        verify(notifier, never()).removed(any());

        when(access.visibleEvent(event.getId())).thenReturn(new EventContext(event,
                new EventRules.Actor(MEMBER_B, false, true, true)));
        service.delete(event.getId());
        assertThat(event.getDeletedAt()).isEqualTo(NOW);
        verify(notifier).removed(event.getId());
        assertThat(recordedActions()).containsExactly(EventAction.DELETED);
    }

    @Test
    void rsvpRefusesInvitedAndClosesADayAfterTheEnd() {
        Event open = stored(AUTHOR, NOW.plusSeconds(3600));
        invitation(open, MEMBER_A, InvitationStatus.INVITED);
        when(access.visibleEvent(open.getId())).thenReturn(new EventContext(open, member(MEMBER_A)));
        assertThatThrownBy(() -> service.respond(open.getId(), InvitationStatus.INVITED))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        when(invitations.findByEventIdAndUserId(open.getId(), MEMBER_A)).thenReturn(Optional.of(saved.getFirst()));
        service.respond(open.getId(), InvitationStatus.MAYBE);
        assertThat(saved.getFirst().getStatus()).isEqualTo(InvitationStatus.MAYBE);
        assertThat(recordedActions()).containsExactly(EventAction.RSVP_CHANGED);

        Event past = stored(AUTHOR, NOW.minusSeconds(24 * 3600 + 1));
        when(access.visibleEvent(past.getId())).thenReturn(new EventContext(past, member(MEMBER_A)));
        assertThatThrownBy(() -> service.respond(past.getId(), InvitationStatus.ACCEPTED))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.EVENT_RSVP_CLOSED));
    }

    @Test
    void nonMembersListPublicEventsOnlyAndPastEventsOnRequest() {
        when(access.currentActor(REGION)).thenReturn(new EventRules.Actor(MEMBER_A, false, false, false));
        when(events.listRegion(eq(REGION), anyBoolean(), any(), any())).thenReturn(List.of());
        service.listRegion(REGION, null, null, false);
        verify(events).listRegion(eq(REGION), eq(true), eq(NOW), any());

        when(access.currentActor(REGION)).thenReturn(member(MEMBER_A));
        service.listRegion(REGION, null, null, true);
        verify(events).listRegion(eq(REGION), eq(false), eq(Instant.EPOCH), any());
    }
}
