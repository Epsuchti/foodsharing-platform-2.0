package network.foodsharing.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.user.domain.Gender;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** A14: verified only, role ≤ own role, region membership (AMBASSADOR: own ambassador region), PDF content. */
class BusinessCardServiceTest {

    @Test
    void rules() {
        assertThat(BusinessCardService.check(true, Role.FOODSAVER, Role.FOODSAVER, true, false)).isEmpty();
        assertThat(BusinessCardService.check(false, Role.FOODSAVER, Role.FOODSAVER, true, false))
                .contains(ApiErrorCode.BUSINESS_CARD_NOT_ALLOWED);
        assertThat(BusinessCardService.check(true, Role.FOODSAVER, Role.STORE_MANAGER, true, false))
                .contains(ApiErrorCode.BUSINESS_CARD_NOT_ALLOWED);
        assertThat(BusinessCardService.check(true, Role.ORGA, Role.FOODSHARER, true, true))
                .contains(ApiErrorCode.BUSINESS_CARD_NOT_ALLOWED);
        assertThat(BusinessCardService.check(true, Role.STORE_MANAGER, Role.FOODSAVER, false, false))
                .contains(ApiErrorCode.NOT_REGION_MEMBER);
        assertThat(BusinessCardService.check(true, Role.AMBASSADOR, Role.AMBASSADOR, true, false))
                .contains(ApiErrorCode.BUSINESS_CARD_NOT_ALLOWED);
        assertThat(BusinessCardService.check(true, Role.AMBASSADOR, Role.AMBASSADOR, true, true)).isEmpty();
        assertThat(BusinessCardService.check(true, Role.ORGA, Role.STORE_MANAGER, true, false)).isEmpty();
        assertThat(BusinessCardService.genderKey(Gender.FEMALE)).isEqualTo("f");
        assertThat(BusinessCardService.genderKey(null)).isEqualTo("n");
    }

    @Test
    void pdfIs85By55MillimetresAndCarriesTheCardText() throws Exception {
        byte[] pdf = BusinessCardService.pdf("Sarah Müller", "Foodsaverin", "foodsharing Zürich", "sarah@local",
                "+41 79 123 45 67");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(240.9f,
                    org.assertj.core.data.Offset.offset(0.5f));
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Sarah Müller", "Foodsaverin", "foodsharing Zürich", "sarah@local",
                    "+41 79 123 45 67");
        }
    }
}
