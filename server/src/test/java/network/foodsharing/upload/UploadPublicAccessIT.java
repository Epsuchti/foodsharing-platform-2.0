package network.foodsharing.upload;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.upload.service.ImageVariantsTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A41: image uploads get variants; uploads claimed by a public usage are readable without login. Decision 158: ETag,
 * conditional GET (304) and Cache-Control per visibility.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:upload-public-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class UploadPublicAccessIT {

    @Autowired
    private MockMvc mvc;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    @Test
    void photoVariantsArePublicOtherUploadsNeedLogin() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        String body = mvc.perform(multipart("/api/uploads").file(new MockMultipartFile("file", "me.png", "image/png",
                        ImageVariantsTest.image(800, 600, "png"))).with(csrf()).cookie(guide))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.width").value(800))
                .andExpect(jsonPath("$.height").value(600))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/uploads/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/uploads/" + id).cookie(login(SeedUsers.BIEB))).andExpect(status().isForbidden());
        String privateTag = mvc.perform(get("/api/uploads/" + id).cookie(guide)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(get("/api/uploads/" + id).cookie(guide).header("If-None-Match", privateTag))
                .andExpect(status().isNotModified());

        mvc.perform(put("/api/users/me/photo").with(csrf()).cookie(guide).contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\":\"" + id + "\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/uploads/" + id).param("size", "sm")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));
        String etag = mvc.perform(get("/api/uploads/" + id)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "public, max-age=604800, immutable"))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(etag).isEqualTo(privateTag).matches("\"[0-9a-f]{64}\"");
        mvc.perform(get("/api/uploads/" + id).header("If-None-Match", etag)).andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andExpect(content().bytes(new byte[0]));
        String smTag = mvc.perform(get("/api/uploads/" + id).param("size", "sm")).andReturn().getResponse()
                .getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(smTag).isNotEqualTo(etag); // the variant's own bytes
        mvc.perform(get("/api/uploads/" + id).param("size", "sm").header("If-None-Match", etag))
                .andExpect(status().isOk());
        mvc.perform(get("/api/uploads/" + id).param("size", "xl")).andExpect(status().isBadRequest());
    }
}
