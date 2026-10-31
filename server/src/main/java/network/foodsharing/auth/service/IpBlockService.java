package network.foodsharing.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.IpBlock;
import network.foodsharing.auth.domain.IpBlockContext;
import network.foodsharing.auth.repository.IpBlockRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.NoHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
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

    /** Blocked in any context (magic links serve login and registration). */
    @Transactional(readOnly = true)
    public boolean isBlocked(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return repository.findActive(clock.instant()).stream().anyMatch(block -> matches(block, ip.strip()));
    }

    /** Blocked for {@code context}: blocks of that context or ALL (C12). */
    @Transactional(readOnly = true)
    public boolean isBlocked(String ip, IpBlockContext context) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return repository.findActive(clock.instant()).stream()
                .anyMatch(block -> block.getContext().covers(context) && matches(block, ip.strip()));
    }

    // ------------------------------------------------------------------ administration (C12, ORGA via admin)

    public record NewBlock(String ip, IpBlockContext context, Integer durationMinutes) {
    }

    /** All blocks, newest first (active and expired). */
    @Transactional(readOnly = true)
    public List<IpBlock> list() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    /** @throws ApiException 400 VALIDATION_FAILED (not an IP address or CIDR range) */
    @Transactional
    @NoHistory(reason = "admin.service.BlocklistAdminService records AdminAction.IP_BLOCK_ADDED")
    public IpBlock add(NewBlock input) {
        String ip = input.ip() == null ? "" : input.ip().strip();
        if (!validCidr(ip) || input.context() == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "ip");
        }
        IpBlock block = new IpBlock();
        block.setCidr(ip);
        block.setContext(input.context());
        if (input.durationMinutes() != null) {
            block.setUntilAt(clock.instant().plus(Duration.ofMinutes(input.durationMinutes())));
        }
        return repository.save(block);
    }

    /** @throws ApiException 404 IP_BLOCK_NOT_FOUND */
    @Transactional
    @NoHistory(reason = "admin.service.BlocklistAdminService records AdminAction.IP_BLOCK_REMOVED")
    public IpBlock remove(UUID blockId) {
        IpBlock block = repository.findById(blockId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.IP_BLOCK_NOT_FOUND));
        repository.delete(block);
        return block;
    }

    static boolean validCidr(String cidr) {
        if (cidr.isEmpty() || !cidr.matches("[0-9a-fA-F:./]+")) {
            return false;
        }
        try {
            new IpAddressMatcher(cidr);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
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
