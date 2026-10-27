package network.foodsharing.auth.service;

import java.time.Clock;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.IpBlock;
import network.foodsharing.auth.repository.IpBlockRepository;
import network.foodsharing.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** IP blocks for abuse (parity: {@code fs_ipblock}): single addresses or CIDR ranges, IPv4 and IPv6, optional end. */
@Service
public class IpBlockService {

    private static final Logger log = LoggerFactory.getLogger(IpBlockService.class);

    private final IpBlockRepository repository;
    private final Clock clock;

    public IpBlockService(IpBlockRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return repository.findActive(clock.instant()).stream().anyMatch(block -> matches(block, ip.strip()));
    }

    /** @throws ApiException 403 IP_BLOCKED */
    @Transactional(readOnly = true)
    public void requireNotBlocked(String ip) {
        if (isBlocked(ip)) {
            throw ApiException.forbidden(ApiErrorCode.IP_BLOCKED);
        }
    }

    static boolean matches(String cidr, String ip) {
        try {
            return new IpAddressMatcher(cidr.strip()).matches(ip);
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring invalid IP block '{}' / address '{}': {}", cidr, ip, ex.getMessage());
            return false;
        }
    }

    private static boolean matches(IpBlock block, String ip) {
        return matches(block.getCidr(), ip);
    }
}
