package network.foodsharing.store.service;

import static network.foodsharing.store.service.StoreServiceTest.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.UserTeamStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Store log filters: date range, StoreLogAction codes only, maySeeStoreLog. */
class StoreLogServiceTest {

    static final UUID STORE = UUID.randomUUID();
    StoreService stores = mock(StoreService.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    HistoryQueryService query = mock(HistoryQueryService.class);
    StoreLogService service = new StoreLogService(stores, rights, mock(CurrentActorService.class), query);

    static StorePermissionService.Rights rights(boolean log) {
        return new StorePermissionService.Rights(true, true, true, false, log, log, false, false, true, false, false,
                false, log, false, false, false, false, false, false, false, false, UserTeamStatus.MEMBER);
    }

    {
        Store store = new Store();
        store.setId(STORE);
        when(stores.require(STORE)).thenReturn(store);
    }

    @Test
    void needsStoreLogRight() {
        when(rights.rights(any(), any())).thenReturn(rights(false));
        assertThat(codeOf(() -> service.list(STORE, null, null, null, 0, 20))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void filtersBySubjectRangeAndActions() {
        when(rights.rights(any(), any())).thenReturn(rights(true));
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        service.list(STORE, from, to, List.of("SIGN_UP_SLOT", " ", "SIGN_UP_SLOT", "DELETE_STORE"), 1, 50);
        ArgumentCaptor<HistoryFilter> filter = ArgumentCaptor.forClass(HistoryFilter.class);
        verify(query).list(filter.capture());
        assertThat(filter.getValue().subjectType()).isEqualTo(SubjectType.STORE);
        assertThat(filter.getValue().subjectId()).isEqualTo(STORE);
        assertThat(filter.getValue().actions()).containsExactly("SIGN_UP_SLOT", "DELETE_STORE");
        assertThat(filter.getValue().from()).isEqualTo(from);
        assertThat(filter.getValue().to()).isEqualTo(to);
        assertThat(filter.getValue().page()).isEqualTo(1);
    }

    @Test
    void unknownActionOrReversedRangeIsRejected() {
        when(rights.rights(any(), any())).thenReturn(rights(true));
        assertThat(codeOf(() -> service.list(STORE, null, null, List.of("USER_CREATED"), 0, 20)))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(codeOf(() -> service.list(STORE, Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"), null, 0, 20))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }
}
