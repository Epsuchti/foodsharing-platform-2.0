package network.foodsharing.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import network.foodsharing.api.PublicApi;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.AppInfo;
import network.foodsharing.api.model.ClientErrorReport;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.LoginMethods;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.AppVersionResolver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicController implements PublicApi {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    private final AppProperties properties;
    private final AppVersionResolver versionResolver;
    private final Clock clock;
    private final SlidingWindowRateLimiter clientErrorLimiter;

    public PublicController(AppProperties properties, AppVersionResolver versionResolver, Clock clock) {
        this.properties = properties;
        this.versionResolver = versionResolver;
        this.clock = clock;
        this.clientErrorLimiter = new SlidingWindowRateLimiter("client-errors", clock,
                properties.getClientErrors().getMaxPerMinutePerIp(), Duration.ofMinutes(1));
    }

    @Override
    public ResponseEntity<AppInfo> getAppInfo() {
        AppProperties.Auth auth = properties.getAuth();
        LoginMethods loginMethods = new LoginMethods();
        loginMethods.setGoogle(auth.getGoogle().isEnabled());
        loginMethods.setMagicLink(auth.getMagicLink().isEnabled());
        loginMethods.setPasskeys(auth.getPasskeys().isEnabled());
        AppInfo info = new AppInfo();
        info.setVersion(versionResolver.resolve());
        info.setDevMode(properties.getDev().isEnabled());
        info.setLanguages(Arrays.asList(Language.values()));
        info.setLoginMethods(loginMethods);
        info.setServerTime(TimeMapper.toOffset(clock.instant()));
        return ResponseEntity.ok(info);
    }

    @Override
    public ResponseEntity<Void> reportClientError(ClientErrorReport report) {
        HttpServletRequest request = HttpExchange.request();
        if (!clientErrorLimiter.tryAcquire(HttpExchange.clientIp(request))) {
            throw ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_REQUESTS, clientErrorLimiter.window());
        }
        log.warn("Client error on route {} (client trace {}): {} | UA: {} | stack: {}",
                StringUtils.abbreviate(report.getRoute(), 500),
                report.getTraceId(),
                StringUtils.abbreviate(report.getMessage(), 2000),
                StringUtils.abbreviate(report.getUserAgent(), 500),
                StringUtils.abbreviate(report.getStack(), 4000));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
