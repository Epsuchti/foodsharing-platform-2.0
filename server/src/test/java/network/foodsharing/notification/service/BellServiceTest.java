package network.foodsharing.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.repository.BellRepository;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.SharedJpaSliceConfig;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** N3: own bells only, unread first, read/delete in bulk, 404 for foreign bells. */
@JpaSliceTest
@Import(SharedJpaSliceConfig.class)
class BellServiceTest {

    @Autowired
    private BellService bells;

    @Autowired
    private BellRepository repository;

    @Autowired
    private UserRepository users;

    private UUID me;
    private UUID other;

    @BeforeEach
    void setUp() {
        me = users.saveAndFlush(TestUsers.user("me@test.local", Role.FOODSAVER)).getId();
        other = users.saveAndFlush(TestUsers.user("other@test.local", Role.FOODSAVER)).getId();
    }

    private Bell bell(UUID userId) {
        return bells.create(userId, NotificationType.GENERIC, Map.of(), null, null, null, null);
    }

    @Test
    void pageListsUnreadFirstAndCountsOnlyOwnUnread() {
        Bell read = bell(me);
        Bell unread = bell(me);
        Bell foreign = bell(other);
        bells.markRead(me, List.of(read.getId(), foreign.getId()));

        assertThat(bells.unreadCount(me)).isEqualTo(1);
        assertThat(bells.unreadCount(other)).isEqualTo(1);
        assertThat(bells.page(me, false, 0, 20).getContent()).extracting(Bell::getId)
                .containsExactly(unread.getId(), read.getId());
        assertThat(bells.page(me, true, 0, 20).getContent()).extracting(Bell::getId).containsExactly(unread.getId());
    }

    @Test
    void deleteOnlyOwnAndDeleteReadOnly() {
        Bell read = bell(me);
        Bell unread = bell(me);
        Bell foreign = bell(other);
        assertThatThrownBy(() -> bells.delete(me, foreign.getId())).isInstanceOf(ApiException.class)
                .hasMessageContaining("BELL_NOT_FOUND");
        bells.markRead(me, List.of(read.getId()));
        assertThat(bells.deleteAll(me, true)).isEqualTo(1);
        assertThat(bells.page(me, false, 0, 20).getContent()).extracting(Bell::getId).containsExactly(unread.getId());
        bells.delete(me, unread.getId());
        assertThat(repository.findById(unread.getId())).get().extracting(Bell::getDeletedAt).isNotNull();
        assertThat(bells.markAllRead(other)).isEqualTo(1);
        assertThat(bells.unreadCount(other)).isZero();
    }
}
