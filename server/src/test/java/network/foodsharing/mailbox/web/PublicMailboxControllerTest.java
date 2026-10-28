package network.foodsharing.mailbox.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.InboundMail;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.mailbox.service.IncomingMailProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** MB5 webhook: 404 on a wrong or unset secret, 202 once processed, 400 for unparseable MIME. */
class PublicMailboxControllerTest {

    private final IncomingMailProcessor processor = mock(IncomingMailProcessor.class);
    private final AppProperties properties = new AppProperties();
    private final PublicMailboxController controller = new PublicMailboxController(processor, properties);
    private final InboundMail mail = new InboundMail().rawMime("From: a@b.ch\n\nx".getBytes());

    private void expect(Runnable call, ApiErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void wrongOrUnsetSecretIsNotFound() {
        expect(() -> controller.receiveInboundMail("anything", mail), ApiErrorCode.RESOURCE_NOT_FOUND);
        properties.getMailbox().setWebhookSecret("s3cret");
        expect(() -> controller.receiveInboundMail("wrong", mail), ApiErrorCode.RESOURCE_NOT_FOUND);
        verify(processor, never()).process(any());
    }

    @Test
    void validDeliveriesAreAccepted() {
        properties.getMailbox().setWebhookSecret("s3cret");
        assertThat(controller.receiveInboundMail("s3cret", mail).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(processor).process(any());
        when(processor.process(any())).thenThrow(new IllegalArgumentException("bad"));
        expect(() -> controller.receiveInboundMail("s3cret", mail), ApiErrorCode.VALIDATION_FAILED);
    }
}
