package network.foodsharing.dev.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Captured Telegram texts have no title: the dev notification body is the text itself, never "null\n…". */
class CapturedTextTest {

    @Test
    void missingTitleOrBodyIsLeftOut() {
        assertThat(DevController.capturedText(null, "Testnachricht von foodsharing für Zürich."))
                .isEqualTo("Testnachricht von foodsharing für Zürich.");
        assertThat(DevController.capturedText("Neue Nachricht", "Hallo")).isEqualTo("Neue Nachricht\nHallo");
        assertThat(DevController.capturedText("Neue Nachricht", " ")).isEqualTo("Neue Nachricht");
        assertThat(DevController.capturedText(null, null)).isEmpty();
    }
}
