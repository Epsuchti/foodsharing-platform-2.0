package network.foodsharing.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.quiz.repository.QuizAnswerI18nRepository;
import network.foodsharing.quiz.repository.QuizAnswerRepository;
import network.foodsharing.quiz.repository.QuizI18nRepository;
import network.foodsharing.quiz.repository.QuizQuestionCommentRepository;
import network.foodsharing.quiz.repository.QuizQuestionI18nRepository;
import network.foodsharing.quiz.repository.QuizQuestionRepository;
import network.foodsharing.quiz.repository.QuizRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;

/** Decision 162: the menu item Quizze bearbeiten follows QuizEditorService.mayEdit for any quiz kind. */
class QuizEditorCapabilityTest {

    private final RegionService regions = mock(RegionService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final QuizEditorService editors = new QuizEditorService(mock(QuizRepository.class),
            mock(QuizI18nRepository.class), mock(QuizQuestionRepository.class), mock(QuizQuestionI18nRepository.class),
            mock(QuizAnswerRepository.class), mock(QuizAnswerI18nRepository.class),
            mock(QuizQuestionCommentRepository.class), regions, mock(UserService.class),
            mock(CurrentActorService.class), permissions, mock(HistoryService.class), Clock.systemUTC());

    private static SessionPrincipal principal(Role role) {
        return new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, "x@test.local");
    }

    private static Region unit(UnitType type, String quizKinds) {
        Region region = new Region();
        region.setId(UUID.randomUUID());
        region.setType(type);
        region.setQuizKinds(quizKinds);
        return region;
    }

    private boolean enrichedWithQuizEdit(SessionPrincipal principal) {
        SessionInfoBuilder builder = new SessionInfoBuilder().addUnit(new SessionInfoBuilder.Unit(UUID.randomUUID(), "unit", "unit", "CITY", null, true, true, false, null));
        new QuizIntegrations().quizSessionInfoEnricher(editors, permissions).enrich(principal, builder);
        return builder.capabilities().contains("QUIZ_EDIT");
    }

    @Test
    void adminOfAQuizGroupEditsQuizzes() {
        SessionPrincipal admin = principal(Role.AMBASSADOR);
        Region group = unit(UnitType.WORKING_GROUP, "FOODSAVER, HYGIENE");
        when(regions.adminRegionIds(admin.userId())).thenReturn(Set.of(group.getId()));
        when(regions.findAll(Set.of(group.getId()))).thenReturn(List.of(group));

        assertThat(enrichedWithQuizEdit(admin)).isTrue();
    }

    @Test
    void adminOfAWorkingGroupWithoutQuizKindDoesNot() {
        // Nina: admin of Betriebskoordination Zürich, which holds no quiz
        SessionPrincipal nina = principal(Role.STORE_MANAGER);
        Region group = unit(UnitType.WORKING_GROUP, null);
        when(regions.adminRegionIds(nina.userId())).thenReturn(Set.of(group.getId()));
        when(regions.findAll(Set.of(group.getId()))).thenReturn(List.of(group));

        assertThat(enrichedWithQuizEdit(nina)).isFalse();
    }

    @Test
    void orgaEditsWithoutAQuery() {
        SessionPrincipal orga = principal(Role.ORGA);
        when(permissions.isOrga(orga)).thenReturn(true);

        assertThat(enrichedWithQuizEdit(orga)).isTrue();
        verify(regions, never()).adminRegionIds(any());
    }

    @Test
    void onlyWorkingGroupsWithAKnownQuizKindCount() {
        assertThat(QuizEditorService.holdsQuizKind(unit(UnitType.WORKING_GROUP, "STORE_MANAGER"))).isTrue();
        assertThat(QuizEditorService.holdsQuizKind(unit(UnitType.WORKING_GROUP, " , UNKNOWN"))).isFalse();
        assertThat(QuizEditorService.holdsQuizKind(unit(UnitType.REGION, "FOODSAVER"))).isFalse();
    }
}
