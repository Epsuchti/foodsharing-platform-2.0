package network.foodsharing.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import network.foodsharing.messaging.domain.MessageEditRules.Verdict;
import org.junit.jupiter.api.Test;

/** Resume item 16: edit/delete window (inclusive, configurable), system messages, other people's messages, moderators. */
class MessageEditRulesTest {

    private static final Instant SENT = Instant.parse("2026-11-01T09:00:00Z");
    private static final Duration DAY = Duration.ofHours(24);
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static ConversationMessage message(UUID senderId) {
        ConversationMessage message = new ConversationMessage();
        message.setSenderId(senderId);
        message.setBody("Hallo");
        message.setSentAt(SENT);
        return message;
    }

    @Test
    void senderMayEditUpToExactlyTheEndOfTheWindow() {
        ConversationMessage message = message(SENDER);
        assertThat(MessageEditRules.edit(SENDER, message, SENT, DAY)).isEqualTo(Verdict.ALLOWED);
        assertThat(MessageEditRules.edit(SENDER, message, SENT.plus(DAY), DAY)).isEqualTo(Verdict.ALLOWED);
        assertThat(MessageEditRules.edit(SENDER, message, SENT.plus(DAY).plusSeconds(1), DAY))
                .isEqualTo(Verdict.WINDOW_EXPIRED);
        assertThat(MessageEditRules.mayEdit(SENDER, message, SENT.plus(DAY).plusSeconds(1), DAY)).isFalse();
    }

    @Test
    void theWindowIsConfigurable() {
        ConversationMessage message = message(SENDER);
        Duration twoHours = Duration.ofHours(2);
        assertThat(MessageEditRules.mayEdit(SENDER, message, SENT.plus(twoHours), twoHours)).isTrue();
        assertThat(MessageEditRules.mayEdit(SENDER, message, SENT.plus(Duration.ofHours(3)), twoHours)).isFalse();
        assertThat(MessageEditRules.mayDelete(SENDER, message, SENT.plus(Duration.ofHours(3)), twoHours, false))
                .isFalse();
        assertThat(MessageEditRules.mayEdit(SENDER, message, SENT.plus(Duration.ofHours(30)), Duration.ofHours(48)))
                .isTrue();
    }

    @Test
    void systemMessagesCanNeverBeEditedOrDeleted() {
        ConversationMessage system = message(null);
        assertThat(MessageEditRules.edit(SENDER, system, SENT, DAY)).isEqualTo(Verdict.NOT_EDITABLE);
        assertThat(MessageEditRules.delete(SENDER, system, SENT, DAY, true)).isEqualTo(Verdict.NOT_EDITABLE);
        assertThat(MessageEditRules.mayDelete(SENDER, system, SENT, DAY, true)).isFalse();
    }

    @Test
    void othersMayNeitherEditNorDeleteAndModeratorsNeverEdit() {
        ConversationMessage message = message(SENDER);
        assertThat(MessageEditRules.edit(OTHER, message, SENT, DAY)).isEqualTo(Verdict.NOT_EDITABLE);
        assertThat(MessageEditRules.delete(OTHER, message, SENT, DAY, false)).isEqualTo(Verdict.NOT_EDITABLE);
        // a moderator is still not allowed to edit
        assertThat(MessageEditRules.mayEdit(OTHER, message, SENT, DAY)).isFalse();
    }

    @Test
    void moderatorsDeleteAnyTimeWithAReason() {
        ConversationMessage message = message(SENDER);
        Instant late = SENT.plus(Duration.ofDays(30));
        assertThat(MessageEditRules.delete(OTHER, message, SENT, DAY, true)).isEqualTo(Verdict.ALLOWED_AS_MODERATOR);
        assertThat(MessageEditRules.delete(OTHER, message, late, DAY, true)).isEqualTo(Verdict.ALLOWED_AS_MODERATOR);
        assertThat(MessageEditRules.requiresReason(OTHER, message, late, DAY, true)).isTrue();
        assertThat(MessageEditRules.mayDelete(OTHER, message, late, DAY, true)).isTrue();
    }

    @Test
    void theReasonIsRequiredOnlyWhenTheSenderRuleDoesNotAllowIt() {
        ConversationMessage message = message(SENDER);
        // own message within the window: no reason, also for a moderator
        assertThat(MessageEditRules.delete(SENDER, message, SENT.plus(DAY), DAY, false)).isEqualTo(Verdict.ALLOWED);
        assertThat(MessageEditRules.requiresReason(SENDER, message, SENT.plus(DAY), DAY, true)).isFalse();
        // own message after the window: expired for a member, moderation (with reason) for a moderator
        Instant after = SENT.plus(DAY).plusSeconds(1);
        assertThat(MessageEditRules.delete(SENDER, message, after, DAY, false)).isEqualTo(Verdict.WINDOW_EXPIRED);
        assertThat(MessageEditRules.requiresReason(SENDER, message, after, DAY, true)).isTrue();
        assertThat(MessageEditRules.requiresReason(SENDER, message, after, DAY, false)).isFalse();
    }

    @Test
    void deletedMessagesCanNeitherBeEditedNorDeletedAgain() {
        ConversationMessage message = message(SENDER);
        message.setDeletedAt(SENT.plusSeconds(60));
        message.setDeletedBy(SENDER);
        message.setBody("");
        assertThat(MessageEditRules.edit(SENDER, message, SENT, DAY)).isEqualTo(Verdict.DELETED);
        assertThat(MessageEditRules.delete(OTHER, message, SENT, DAY, true)).isEqualTo(Verdict.DELETED);
        assertThat(message.isDeletedByModerator()).isFalse();
        message.setDeletedBy(OTHER);
        assertThat(message.isDeletedByModerator()).isTrue();
    }
}
