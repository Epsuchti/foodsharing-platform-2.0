package network.foodsharing.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.history.service.HistoryAccessPolicy;
import network.foodsharing.history.service.HistoryActionRegistry;
import network.foodsharing.history.service.HistoryCsvExporter;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.history.service.SystemHistoryAccessRule;
import network.foodsharing.history.web.HistoryController;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.WithSeedUser;
import network.foodsharing.user.service.UserHistoryAccessRule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** Real query service, access policy and rules; only the repository is mocked. */
@WebMvcTest(HistoryController.class)
@Import({SecurityTestConfig.class, HistoryQueryService.class, HistoryAccessPolicy.class, UserHistoryAccessRule.class,
        SystemHistoryAccessRule.class, HistoryActionRegistry.class, HistoryCsvExporter.class, HistoryMapper.class})
@ActiveProfiles("test")
class HistoryControllerTest {

    private static final UUID ME = SeedUsers.FOODSAVER.id();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HistoryEntryRepository repository;

    @MockitoSpyBean
    private HistoryQueryService queryService;

    @Test
    void anonymousCallsNeedAuthentication() throws Exception {
        mvc.perform(get("/api/history").param("subjectType", "USER").param("subjectId", ME.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/history/export")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithSeedUser("foodsaver")
    void ownHistoryWithAllFiltersIsPassedToTheQuery() throws Exception {
        HistoryEntry entry = HistoryQueryServiceTest.entry(SubjectType.USER, ME, "LOGIN", null, FixedClockConfig.SEED_NOW);
        entry.setId(UUID.randomUUID());
        entry.setMetadata("{\"method\":\"GOOGLE\"}");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(entry), invocation.getArgument(1), 41));

        mvc.perform(get("/api/history")
                        .param("subjectType", "USER")
                        .param("subjectId", ME.toString())
                        .param("actions", "LOGIN", "LOGOUT")
                        .param("actorId", ME.toString())
                        .param("from", "2026-11-01T00:00:00Z")
                        .param("to", "2026-11-02T10:00:00+01:00")
                        .param("page", "2")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.items[0].subjectType").value("USER"))
                .andExpect(jsonPath("$.items[0].actor.type").value("SYSTEM"))
                .andExpect(jsonPath("$.items[0].metadata.method").value("GOOGLE"))
                .andExpect(jsonPath("$.page.page").value(2))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalItems").value(41))
                .andExpect(jsonPath("$.page.totalPages").value(3));

        ArgumentCaptor<HistoryFilter> filter = ArgumentCaptor.forClass(HistoryFilter.class);
        verify(queryService).list(filter.capture());
        assertThat(filter.getValue()).isEqualTo(new HistoryFilter(SubjectType.USER, ME, ME, null,
                List.of("LOGIN", "LOGOUT"), Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-02T09:00:00Z"), 2, 20));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getSort().getOrderFor("occurredAt").isDescending()).isTrue();
    }

    @Test
    @WithSeedUser("foodsaver")
    void otherUsersHistoryIsForbidden() throws Exception {
        mvc.perform(get("/api/history").param("subjectType", "USER").param("subjectId", SeedUsers.NEWBIE.id().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HISTORY_SUBJECT_NOT_READABLE"))
                .andExpect(jsonPath("$.retryable").value(false));
        mvc.perform(get("/api/history"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HISTORY_SUBJECT_NOT_READABLE"));
        mvc.perform(get("/api/history").param("subjectType", "SYSTEM")
                        .param("subjectId", new UUID(0, 0).toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HISTORY_SUBJECT_NOT_READABLE"));
        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @WithSeedUser("orga")
    void orgaReadsAnyHistory() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/history").param("subjectType", "USER").param("subjectId", SeedUsers.NEWBIE.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/history").param("actions", "LOGIN"))
                .andExpect(status().isOk());
    }

    @Test
    @WithSeedUser("foodsaver")
    void invalidParametersAreValidationFailed() throws Exception {
        mvc.perform(get("/api/history").param("subjectType", "USER").param("subjectId", ME.toString())
                        .param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/history").param("subjectType", "NOPE").param("subjectId", ME.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/history").param("subjectType", "USER").param("subjectId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithSeedUser("foodsaver")
    void myActivityUsesTheCurrentUser() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/history/me").param("actions", "LOGIN")).andExpect(status().isOk());

        ArgumentCaptor<HistoryFilter> filter = ArgumentCaptor.forClass(HistoryFilter.class);
        verify(queryService).myActivity(filter.capture());
        assertThat(filter.getValue().actions()).containsExactly("LOGIN");
    }

    @Test
    @WithSeedUser("newbie")
    void actionsCanBeListedPerSubjectType() throws Exception {
        mvc.perform(get("/api/history/actions").param("subjectType", "SYSTEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].subjectType").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("SYSTEM"))))
                .andExpect(jsonPath("$[?(@.action == 'REGISTRATION_BLOCKED')].labelKey")
                        .value("history.action.SYSTEM.REGISTRATION_BLOCKED"));
        mvc.perform(get("/api/history/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'FIELD_CHANGED')].subjectType")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("USER", "REGION", "GROUP", "STORE",
                                "STORE_CHAIN", "AUTOMATION", "MAILBOX", "EVENT", "BASKET", "FSP")));
    }

    @Test
    @WithSeedUser("welcome")
    void csvExportIsForbiddenBelowOrga() throws Exception {
        mvc.perform(get("/api/history/export"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @WithSeedUser("orga")
    void orgaExportsCsv() throws Exception {
        HistoryEntry entry = HistoryQueryServiceTest.entry(SubjectType.USER, ME, "FIELD_CHANGED", null,
                FixedClockConfig.SEED_NOW);
        entry.setField("displayName");
        entry.setOldValue("=cmd()");
        entry.setNewValue("Anna, \"the\" saver");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        String csv = mvc.perform(get("/api/history/export").param("subjectType", "USER").param("subjectId", ME.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"history.csv\""))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).startsWith("occurredAt,actorType,actorId,actorName,subjectType,subjectId,");
        assertThat(csv).contains("2026-11-02T09:00:00Z,SYSTEM,,,USER," + ME + ",,FIELD_CHANGED,displayName,'=cmd(),"
                + "\"Anna, \"\"the\"\" saver\"");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10_000);
    }
}
