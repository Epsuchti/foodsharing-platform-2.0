package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * D17.2 security review, OWASP A01 (IDOR spot checks): a logged-in foodsaver who knows the id of someone else's
 * object gets 403/404 and the object is unchanged. Ids are looked up in the seed so the checks follow the data.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:idor-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class IdorSpotCheckIT {

    private static final SeedUsers ME = SeedUsers.FOODSAVER;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie session;

    @BeforeEach
    void login() throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + ME.id() + "\"}")).andReturn();
        session = Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.cookie(session).with(csrf())).andReturn().getResponse().getStatus();
    }

    private UUID one(String sql, Object... args) {
        return jdbc.queryForObject(sql + " limit 1", UUID.class, args);
    }

    @Test
    void foreignConversationIsNotReadable() throws Exception {
        UUID conversation = one("select c.id from conversation c where not exists (select 1 from conversation_member m"
                + " where m.conversation_id = c.id and m.user_id = ?) order by c.id", ME.id());
        assertThat(status(get("/api/conversations/" + conversation + "/messages"))).isIn(403, 404);
        assertThat(status(get("/api/conversations/" + conversation))).isIn(403, 404);
    }

    @Test
    void foreignBellCannotBeDeleted() throws Exception {
        UUID bell = one("select id from bell where user_id <> ? and deleted_at is null order by id", ME.id());
        assertThat(status(delete("/api/bells/" + bell))).isIn(403, 404);
        assertThat(jdbc.queryForObject("select count(*) from bell where id = ? and deleted_at is null", Integer.class,
                bell)).isEqualTo(1);
    }

    @Test
    void foreignStorePickupsAndForeignPickupHistoryAreRefused() throws Exception {
        UUID store = one("select s.id from store s where s.deleted_at is null and not exists (select 1 from "
                + "store_membership m where m.store_id = s.id and m.user_id = ?) order by s.id", ME.id());
        assertThat(status(get("/api/stores/" + store + "/pickups"))).isIn(403, 404);
        assertThat(status(get("/api/users/" + SeedUsers.BIEB.id() + "/pickups/past"))).isIn(403, 404);
    }

    @Test
    void foreignMailboxIsNotReadable() throws Exception {
        UUID mailbox = one("select b.id from mailbox b where not exists (select 1 from mailbox_member m where "
                + "m.mailbox_id = b.id and m.user_id = ?) and not (b.owner_type = 'USER' and b.owner_id = ?) order by b.id",
                ME.id(), ME.id());
        assertThat(status(get("/api/mailboxes/" + mailbox + "/folders/INBOX/mails"))).isIn(403, 404);
    }

    @Test
    void foreignUploadCannotBeDeleted() throws Exception {
        UUID upload = one("select id from upload where owner_user_id is not null and owner_user_id <> ? order by id",
                ME.id());
        assertThat(status(delete("/api/uploads/" + upload))).isIn(403, 404, 409);
        assertThat(jdbc.queryForObject("select count(*) from upload where id = ?", Integer.class, upload)).isEqualTo(1);
    }
}
