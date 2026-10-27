package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import network.foodsharing.common.error.ApiErrors;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void putsA12HexTraceIdIntoMdcHeaderAndRequestAttributeWhileTheChainRuns() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(TraceIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String header = response.getHeader(TraceIdFilter.HEADER);
        assertThat(header).matches("[0-9a-f]{12}");
        assertThat(mdcDuringChain.get()).isEqualTo(header);
        assertThat(request.getAttribute(TraceIdFilter.ATTRIBUTE)).isEqualTo(header);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
        // ApiError bodies and history entries read the same MDC key
        assertThat(ApiErrors.TRACE_ID_MDC_KEY).isEqualTo(TraceIdFilter.MDC_KEY);
    }

    @Test
    void removesTheTraceIdFromTheMdcEvenWhenTheChainFails() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response, failing)).hasMessage("boom");

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
        assertThat(response.getHeader(TraceIdFilter.HEADER)).matches("[0-9a-f]{12}");
    }

    @Test
    void everyRequestGetsItsOwnId() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), first, (req, res) -> { });
        filter.doFilter(new MockHttpServletRequest(), second, (req, res) -> { });

        assertThat(first.getHeader(TraceIdFilter.HEADER)).isNotEqualTo(second.getHeader(TraceIdFilter.HEADER));
        assertThat(TraceIdFilter.newTraceId()).matches("[0-9a-f]{12}");
    }

    @Test
    void runsBeforeEveryOtherFilter() {
        assertThat(TraceIdFilter.class.getAnnotation(Order.class).value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
