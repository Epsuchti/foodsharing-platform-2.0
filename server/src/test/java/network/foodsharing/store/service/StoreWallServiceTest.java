package network.foodsharing.store.service;

import static network.foodsharing.store.service.StoreServiceTest.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.StoreWallPost;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.domain.WallMilestone;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.store.repository.StoreWallPostRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A19 wall rules: see / post / delete per actor × post age × authorship. */
class StoreWallServiceTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID STORE = UUID.randomUUID();
    static final UUID ACTOR = UUID.randomUUID();

    StoreWallPostRepository posts = mock(StoreWallPostRepository.class);
    StoreRepository stores = mock(StoreRepository.class);
    StoreService storeService = mock(StoreService.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    PermissionService permissions = mock(PermissionService.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    StoreWallService service;
    Store store = new Store();

    @BeforeEach
    void setUp() {
        service = new StoreWallService(posts, stores, storeService, rights, permissions, mock(UserService.class),
                currentActor, history, new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        store.setId(STORE);
        when(storeService.require(STORE)).thenReturn(store);
        when(currentActor.requirePrincipal()).thenReturn(new SessionPrincipal(ACTOR, UUID.randomUUID(), Role.FOODSAVER,
                "a@b.c"));
        when(posts.save(any())).thenAnswer(i -> {
            StoreWallPost p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }

    static StorePermissionService.Rights rights(boolean wall, UserTeamStatus status, boolean coordinate) {
        return new StorePermissionService.Rights(true, wall, wall, false, false, false, wall, false, wall, false,
                false, false, false, coordinate, false, false, false, false, false,
                status == UserTeamStatus.COORDINATOR, coordinate, status);
    }

    static StoreWallPost post(UUID author, int ageDays) {
        StoreWallPost p = new StoreWallPost();
        p.setId(UUID.randomUUID());
        p.setStoreId(STORE);
        p.setAuthorId(author);
        p.setText("Hallo");
        p.setCreatedAt(NOW.minus(Duration.ofDays(ageDays)));
        return p;
    }

    /** actor, own post, age in days → may delete. */
    @ParameterizedTest
    @CsvSource({
            "ORGA, false, 1, true",
            "ORGA_NO_TEAM, false, 1, true",
            "MEMBER, true, 1, true",
            "MEMBER, true, 40, true",
            "MEMBER, false, 1, false",
            "MEMBER, false, 40, false",
            "COORDINATOR, false, 1, false",
            "COORDINATOR, false, 29, false",
            "COORDINATOR, false, 31, true",
            "AMBASSADOR_OF_REGION, false, 1, false",
            "AMBASSADOR_OF_REGION, false, 40, true",
            "AMBASSADOR_OF_REGION, true, 1, true",
            "AMBASSADOR_ELSEWHERE_IN_TEAM, false, 40, false",
            "AMBASSADOR_ELSEWHERE_IN_TEAM, true, 1, true",
            "AMBASSADOR_ELSEWHERE_NO_TEAM, true, 1, false",
            "JUMPER, true, 1, false"
    })
    void deleteMatrix(String actor, boolean own, int age, boolean allowed) {
        StorePermissionService.Rights r = switch (actor) {
            case "ORGA" -> rights(true, UserTeamStatus.MEMBER, true);
            case "ORGA_NO_TEAM" -> rights(true, UserTeamStatus.NO_MEMBER, true);
            case "MEMBER" -> rights(true, UserTeamStatus.MEMBER, false);
            case "COORDINATOR" -> rights(true, UserTeamStatus.COORDINATOR, false);
            case "AMBASSADOR_OF_REGION" -> rights(true, UserTeamStatus.NO_MEMBER, true);
            case "AMBASSADOR_ELSEWHERE_IN_TEAM" -> rights(true, UserTeamStatus.MEMBER, false);
            case "AMBASSADOR_ELSEWHERE_NO_TEAM" -> rights(false, UserTeamStatus.NO_MEMBER, false);
            case "JUMPER" -> rights(false, UserTeamStatus.WAITING_LIST, false);
            default -> throw new IllegalArgumentException(actor);
        };
        when(rights.rights(any(), any())).thenReturn(r);
        when(permissions.isOrga(any())).thenReturn(actor.startsWith("ORGA"));
        StoreWallPost post = post(own ? ACTOR : UUID.randomUUID(), age);
        when(posts.findByIdAndStoreIdAndDeletedAtIsNull(post.getId(), STORE)).thenReturn(Optional.of(post));
        ApiErrorCode code = codeOf(() -> service.delete(STORE, post.getId()));
        if (allowed) {
            assertThat(code).isNull();
            assertThat(post.getDeletedAt()).isEqualTo(NOW);
            assertThat(post.getDeletedBy()).isEqualTo(ACTOR);
            verify(history).event(StoreLogAction.DELETED_FROM_WALL, STORE);
        } else {
            assertThat(code).isEqualTo(ApiErrorCode.ACCESS_DENIED);
            assertThat(post.getDeletedAt()).isNull();
        }
    }

    @Test
    void deletingAMissingPostIsNotFound() {
        when(rights.rights(any(), any())).thenReturn(rights(true, UserTeamStatus.MEMBER, false));
        assertThat(codeOf(() -> service.delete(STORE, UUID.randomUUID()))).isEqualTo(ApiErrorCode.WALL_POST_NOT_FOUND);
    }

    @Test
    void postNeedsWallRightAndText() {
        when(rights.rights(any(), any())).thenReturn(rights(false, UserTeamStatus.WAITING_LIST, false));
        assertThat(codeOf(() -> service.post(STORE, "Hallo"))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(rights.rights(any(), any())).thenReturn(rights(true, UserTeamStatus.MEMBER, false));
        assertThat(codeOf(() -> service.post(STORE, "  "))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(codeOf(() -> service.post(STORE, "x".repeat(4001)))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        var view = service.post(STORE, " Hallo ");
        assertThat(view.post().getText()).isEqualTo("Hallo");
        assertThat(view.post().getMilestone()).isEqualTo(WallMilestone.NONE);
        verify(history).event(StoreLogAction.WALL_POST_ADDED, STORE);
    }

    @Test
    void listNeedsWallRight() {
        when(rights.rights(any(), any())).thenReturn(rights(false, UserTeamStatus.WAITING_LIST, false));
        assertThat(codeOf(() -> service.list(STORE, 0, 20))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void milestoneIsWrittenForLiveStoresOnly() {
        when(stores.findLive(STORE)).thenReturn(Optional.of(store));
        service.onStoreChanged(new StoreService.StoreChanged(STORE, WallMilestone.COOPERATION_STARTED,
                "COOPERATION_ESTABLISHED", ACTOR));
        verify(posts).save(any());
        when(stores.findLive(STORE)).thenReturn(Optional.empty());
        service.onStoreChanged(new StoreService.StoreChanged(STORE, WallMilestone.STATUS_CHANGED, "X", ACTOR));
        verify(posts).save(any());
    }

    @Test
    void storeDeleteSoftDeletesAllPosts() {
        StoreWallPost a = post(ACTOR, 1);
        StoreWallPost b = post(null, 100);
        when(posts.findByStoreIdAndDeletedAtIsNull(STORE)).thenReturn(List.of(a, b));
        assertThat(service.deleteAllOf(STORE, ACTOR)).isEqualTo(2);
        assertThat(b.getDeletedAt()).isEqualTo(NOW);
    }
}
