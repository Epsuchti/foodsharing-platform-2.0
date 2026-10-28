package network.foodsharing.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import network.foodsharing.messaging.service.ConversationSyncJob;
import network.foodsharing.messaging.service.RegionConversationSync;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** A33 on the seed: region chat follows leave/toggle, the conversation-sync job repairs missed events. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:conversation-sync-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@ActiveProfiles("e2e")
class ConversationSyncIT {

    static final UUID KOCHGRUPPE = UUID.fromString("00000011-0001-4000-8000-000000000033");
    static final String REGION_CHAT = "00000041-0001-4000-8000-000000000006";
    static final String TEAM_CHAT = "00000041-0001-4000-8000-000000000004";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConversationSyncJob job;

    @Autowired
    private RegionConversationSync regions;

    @Autowired
    private MembershipService memberships;

    private List<UUID> members(String conversationId) {
        return jdbc.queryForList("select user_id from conversation_member where conversation_id = ?", UUID.class,
                UUID.fromString(conversationId));
    }

    @Test
    void jobRestoresMissingMembersOfStoreAndRegionChats() {
        UUID bieb = SeedUsers.BIEB.id();
        assertThat(members(TEAM_CHAT)).contains(bieb);
        assertThat(members(REGION_CHAT)).contains(SeedUsers.JUMPER.id());
        jdbc.update("delete from conversation_member where conversation_id = ? and user_id = ?",
                UUID.fromString(TEAM_CHAT), bieb);
        jdbc.update("delete from conversation_member where conversation_id = ? and user_id = ?",
                UUID.fromString(REGION_CHAT), SeedUsers.JUMPER.id());
        job.runNow();
        assertThat(members(TEAM_CHAT)).contains(bieb);
        assertThat(members(REGION_CHAT)).contains(SeedUsers.JUMPER.id());
        assertThat(job.jobName()).isEqualTo("conversation-sync");
    }

    @Test
    void regionChatFollowsLeavingAndTheSwitch() {
        UUID foodsaver = SeedUsers.FOODSAVER.id();
        assertThat(members(REGION_CHAT)).contains(foodsaver);
        memberships.leave(foodsaver, KOCHGRUPPE);
        assertThat(members(REGION_CHAT)).doesNotContain(foodsaver);

        jdbc.update("update region set conversation_enabled = false where id = ?", KOCHGRUPPE);
        regions.syncRegion(KOCHGRUPPE);
        assertThat(members(REGION_CHAT)).isEmpty();
        jdbc.update("update region set conversation_enabled = true where id = ?", KOCHGRUPPE);
        regions.syncRegion(KOCHGRUPPE);
        assertThat(members(REGION_CHAT)).containsExactlyInAnyOrderElementsOf(memberships.membersOf(KOCHGRUPPE))
                .isNotEmpty();
    }
}
