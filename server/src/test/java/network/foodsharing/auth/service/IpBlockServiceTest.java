package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.IpBlock;
import network.foodsharing.auth.repository.IpBlockRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.MutableClock;
import network.foodsharing.testsupport.FixedClockConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IpBlockServiceTest {

    @ParameterizedTest
    @CsvSource({
            "203.0.113.7, 203.0.113.7, true",
            "203.0.113.7, 203.0.113.8, false",
            "203.0.113.0/24, 203.0.113.200, true",
            "203.0.113.0/24, 203.0.114.1, false",
            "10.0.0.0/8, 10.255.1.2, true",
            "2001:db8::/32, 2001:db8:1::5, true",
            "2001:db8::/32, 2001:db9::1, false",
            "203.0.113.0/24, 2001:db8::1, false",
            "not-an-ip, 203.0.113.7, false"
    })
    void matchesAddressesAndRanges(String cidr, String ip, boolean blocked) {
        assertThat(IpBlockService.matches(cidr, ip)).isEqualTo(blocked);
    }

    @Test
    void onlyActiveBlocksCountAndBlockedIpsAreRejected() {
        IpBlockRepository repository = mock(IpBlockRepository.class);
        MutableClock clock = new FixedClockConfig().clock();
        IpBlock block = new IpBlock();
        block.setCidr("198.51.100.0/24");
        when(repository.findActive(FixedClockConfig.SEED_NOW)).thenReturn(List.of(block));
        IpBlockService service = new IpBlockService(repository, clock);

        assertThat(service.isBlocked("198.51.100.9")).isTrue();
        assertThat(service.isBlocked("192.0.2.1")).isFalse();
        assertThat(service.isBlocked(null)).isFalse();
        assertThatThrownBy(() -> service.requireNotBlocked("198.51.100.9"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ApiErrorCode.IP_BLOCKED);
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                });
    }

    @Test
    void blockExpiry() {
        IpBlock block = new IpBlock();
        assertThat(block.isActiveAt(FixedClockConfig.SEED_NOW)).isTrue();
        block.setUntilAt(FixedClockConfig.SEED_NOW);
        assertThat(block.isActiveAt(FixedClockConfig.SEED_NOW)).isFalse();
        assertThat(block.isActiveAt(FixedClockConfig.SEED_NOW.minusSeconds(1))).isTrue();
    }
}
