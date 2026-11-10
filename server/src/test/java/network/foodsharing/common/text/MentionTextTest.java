package network.foodsharing.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Mention tokens as plain text (review 2026-11-10 F4). */
class MentionTextTest {

    @Test
    void tokensBecomeAtNames() {
        assertThat(MentionText.plain("Merci! @[Jonas Weber](user:00000001-0001-4000-8000-000000000007) übernimmt."))
                .isEqualTo("Merci! @Jonas Weber übernimmt.");
        assertThat(MentionText.plain("@[A$1 \\\\ B](user:00000001-0001-4000-8000-00000000000A) und @[C](user:"
                + "00000001-0001-4000-8000-000000000003)")).isEqualTo("@A$1 \\\\ B und @C");
    }

    @Test
    void otherTextStaysAsItIs() {
        assertThat(MentionText.plain(null)).isNull();
        assertThat(MentionText.plain("@Anna [x](user:123) @[kein](link)")).isEqualTo("@Anna [x](user:123) @[kein](link)");
    }
}
