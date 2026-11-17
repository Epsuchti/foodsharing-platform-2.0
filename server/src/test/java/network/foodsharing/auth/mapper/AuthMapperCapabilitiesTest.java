package network.foodsharing.auth.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import network.foodsharing.api.model.NavCapability;
import network.foodsharing.api.model.Role;
import network.foodsharing.security.NavCapabilities;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Decision 162 (was SessionCapabilitiesIT): the menu capabilities the modules' enrichers add reach
 * {@code SessionInfo.capabilities}, every constant is a value of the contract enum, and one failing enricher does not
 * take the others' capabilities with it. Who gets which capability: RegionCapabilitiesTest, ContentCapabilitiesTest,
 * NewsletterCapabilityTest, QuizEditorCapabilityTest.
 */
class AuthMapperCapabilitiesTest {

    @Test
    void everyCapabilityConstantIsAContractValue() throws IllegalAccessException {
        List<String> constants = Arrays.stream(NavCapabilities.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .map(AuthMapperCapabilitiesTest::value).toList();
        assertThat(constants).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(NavCapability.values()).map(NavCapability::getValue).toList());
    }

    @Test
    void enrichedCapabilitiesReachTheSessionInfoEvenWhenAnotherEnricherFails() {
        SessionInfoEnricher regions = (principal, builder) -> builder.capability(NavCapabilities.VERIFY)
                .capability(NavCapabilities.MANAGE_MEMBERS);
        SessionInfoEnricher broken = (principal, builder) -> {
            throw new IllegalStateException("module down");
        };
        SessionInfoEnricher content = (principal, builder) -> builder.capability(NavCapabilities.BLOG_MANAGE)
                .capability(NavCapabilities.VERIFY);
        @SuppressWarnings("unchecked")
        ObjectProvider<SessionInfoEnricher> enrichers = mock(ObjectProvider.class);
        when(enrichers.orderedStream()).thenAnswer(invocation -> Stream.of(regions, broken, content));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("bot@local");
        user.setDisplayName("Reto");
        user.setRole(Role.AMBASSADOR);
        SessionPrincipal principal = new SessionPrincipal(user.getId(), UUID.randomUUID(), Role.AMBASSADOR,
                user.getEmail());

        assertThat(new AuthMapper(enrichers).sessionInfo(principal, user, List.of()).getCapabilities())
                .containsExactly(NavCapability.VERIFY, NavCapability.MANAGE_MEMBERS, NavCapability.BLOG_MANAGE);
    }

    @Test
    void withoutEnrichedCapabilitiesTheListIsEmpty() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SessionInfoEnricher> enrichers = mock(ObjectProvider.class);
        when(enrichers.orderedStream()).thenAnswer(invocation -> Stream.empty());
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.FOODSAVER);
        SessionPrincipal principal = new SessionPrincipal(user.getId(), UUID.randomUUID(), Role.FOODSAVER, "x@local");
        assertThat(new AuthMapper(enrichers).sessionInfo(principal, user, List.of()).getCapabilities()).isEmpty();
    }

    private static String value(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
