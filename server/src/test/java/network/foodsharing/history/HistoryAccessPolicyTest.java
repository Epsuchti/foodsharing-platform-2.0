package network.foodsharing.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import network.foodsharing.history.service.HistoryAccessPolicy;
import network.foodsharing.history.service.SystemHistoryAccessRule;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.user.service.UserHistoryAccessRule;
import org.junit.jupiter.api.Test;

class HistoryAccessPolicyTest {

    private final HistoryAccessPolicy policy = new HistoryAccessPolicy(
            List.of(new UserHistoryAccessRule(new PermissionService()), new SystemHistoryAccessRule()), new PermissionService());

    private final SessionPrincipal foodsaver = TestAuth.principal(SeedUsers.FOODSAVER);
    private final SessionPrincipal ambassador = TestAuth.principal(SeedUsers.WELCOME);
    private final SessionPrincipal orga = TestAuth.principal(SeedUsers.ORGA);

    @Test
    void usersReadTheirOwnUserHistoryOnly() {
        assertThat(policy.canRead(foodsaver, SubjectType.USER, SeedUsers.FOODSAVER.id())).isTrue();
        assertThat(policy.canRead(foodsaver, SubjectType.USER, SeedUsers.NEWBIE.id())).isFalse();
        // M1: ambassadors are not yet allowed to read other users' history (home region rule comes in M2)
        assertThat(policy.canRead(ambassador, SubjectType.USER, SeedUsers.FOODSAVER.id())).isFalse();
    }

    @Test
    void orgaReadsEverything() {
        for (SubjectType type : SubjectType.values()) {
            assertThat(policy.canRead(orga, type, UUID.randomUUID())).as(type.name()).isTrue();
        }
    }

    @Test
    void systemHistoryIsOrgaOnly() {
        assertThat(policy.canRead(foodsaver, SubjectType.SYSTEM, UUID.randomUUID())).isFalse();
        assertThat(policy.canRead(ambassador, SubjectType.SYSTEM, UUID.randomUUID())).isFalse();
        assertThat(policy.canRead(orga, SubjectType.SYSTEM, UUID.randomUUID())).isTrue();
    }

    @Test
    void subjectTypesWithoutARuleAreOrgaOnly() {
        assertThat(policy.canRead(foodsaver, SubjectType.STORE, SeedUsers.FOODSAVER.id())).isFalse();
        assertThat(policy.canRead(ambassador, SubjectType.REGION, UUID.randomUUID())).isFalse();
    }

    @Test
    void missingActorOrSubjectIsNeverReadable() {
        assertThat(policy.canRead(null, SubjectType.USER, SeedUsers.FOODSAVER.id())).isFalse();
        assertThat(policy.canRead(orga, null, UUID.randomUUID())).isFalse();
        assertThat(policy.canRead(orga, SubjectType.USER, null)).isFalse();
    }

    @Test
    void recordedValuesFollowTheRuleExceptForOrga() {
        HistoryAccessRule hidesValues = new HistoryAccessRule() {
            @Override
            public SubjectType subjectType() {
                return SubjectType.CONVERSATION;
            }

            @Override
            public boolean canRead(SessionPrincipal actor, UUID subjectId) {
                return true;
            }

            @Override
            public boolean mayReadValues(SessionPrincipal actor, UUID subjectId) {
                return false;
            }
        };
        HistoryAccessPolicy withValues = new HistoryAccessPolicy(
                List.of(new UserHistoryAccessRule(new PermissionService()), hidesValues), new PermissionService());
        UUID conversation = UUID.randomUUID();
        assertThat(withValues.canRead(foodsaver, SubjectType.CONVERSATION, conversation)).isTrue();
        assertThat(withValues.mayReadValues(foodsaver, SubjectType.CONVERSATION, conversation)).isFalse();
        assertThat(withValues.mayReadValues(null, SubjectType.CONVERSATION, conversation)).isFalse();
        assertThat(withValues.mayReadValues(orga, SubjectType.CONVERSATION, conversation)).isTrue();
        // the default: whoever may read the subject also sees the values
        assertThat(withValues.mayReadValues(foodsaver, SubjectType.USER, SeedUsers.FOODSAVER.id())).isTrue();
        assertThat(withValues.mayReadValues(foodsaver, SubjectType.STORE, UUID.randomUUID())).isTrue();
    }

    @Test
    void twoRulesForOneSubjectTypeAreAStartupError() {
        assertThatThrownBy(() -> new HistoryAccessPolicy(
                List.of(new UserHistoryAccessRule(new PermissionService()), new UserHistoryAccessRule(new PermissionService())), new PermissionService()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("USER");
    }
}
