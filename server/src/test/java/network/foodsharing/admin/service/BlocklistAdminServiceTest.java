package network.foodsharing.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.domain.EmailBlocklistEntry;
import network.foodsharing.auth.domain.IpBlockContext;
import network.foodsharing.auth.service.EmailBlocklistService;
import network.foodsharing.auth.service.IpBlockService;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.RateLimitBlockRegistry;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.junit.jupiter.api.Test;

/** C12 / D17.2: blocklist, IP blocks and automatic rate-limit blocks are ORGA only and recorded (was AdminSettingsIT). */
class BlocklistAdminServiceTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    EmailBlocklistService blocklist = mock(EmailBlocklistService.class);
    IpBlockService ipBlocks = mock(IpBlockService.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    RateLimitBlockRegistry registry = new RateLimitBlockRegistry(new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
    BlocklistAdminService service = new BlocklistAdminService(blocklist, ipBlocks, actors, history, registry);

    @Test
    void everyOperationIsOrgaOnlyAndTouchesNothingOtherwise() {
        when(actors.requireRole(Role.ORGA)).thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        RateLimitBlockRegistry.Block block = registry.create(RateLimitBlockRegistry.Scope.IP, "198.51.100.23");
        UUID id = UUID.randomUUID();
        List<Runnable> calls = List.of(service::blocklist, () -> service.addBlocklistEntry("trash.test", null),
                () -> service.updateBlocklistEntry(id, "x.test", null), () -> service.removeBlocklistEntry(id),
                service::ipBlocks, () -> service.addIpBlock("192.0.2.0/24", IpBlockContext.ALL, 60),
                () -> service.removeIpBlock(id), service::rateLimitBlocks, () -> service.liftRateLimitBlock(block.id()));
        for (Runnable call : calls) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        }
        verifyNoInteractions(blocklist, ipBlocks);
        assertThat(registry.list()).extracting(RateLimitBlockRegistry.Block::id).containsExactly(block.id());
        verify(history, never()).record(any());
    }

    @Test
    void addingABlocklistEntryIsRecorded() {
        EmailBlocklistEntry entry = new EmailBlocklistEntry();
        entry.setId(UUID.randomUUID());
        entry.setPattern("trash.test");
        when(blocklist.add("@Trash.Test", "Wegwerf")).thenReturn(entry);
        assertThat(service.addBlocklistEntry("@Trash.Test", "Wegwerf")).isSameAs(entry);
        verify(history).event(AdminAction.BLOCKLIST_ENTRY_ADDED, entry.getId());
    }

    @Test
    void liftingEndsTheBlockOnceAndRecordsItsScopeSubjectLevelAndEnd() {
        UUID user = UUID.randomUUID();
        RateLimitBlockRegistry.Block block = registry.create(RateLimitBlockRegistry.Scope.ACCOUNT, user.toString());
        service.liftRateLimitBlock(block.id());
        assertThat(service.rateLimitBlocks()).isEmpty();
        verify(history).event(AdminAction.RATE_LIMIT_BLOCK_LIFTED, block.id());
        assertThat(RateLimitBlockService.metadata(block)).containsEntry("scope", "ACCOUNT")
                .containsEntry("userId", user.toString()).containsEntry("level", 1).containsKey("until");
        Consumer<UUID> lift = service::liftRateLimitBlock;
        assertThatThrownBy(() -> lift.accept(block.id())).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.RATE_LIMIT_BLOCK_NOT_FOUND));
        assertThatThrownBy(() -> lift.accept(UUID.randomUUID())).isInstanceOf(ApiException.class);
    }

    @Test
    void anAutomaticBlockIsRecordedAsSystemWithItsIp() {
        RateLimitBlockService recorder = new RateLimitBlockService(registry, history);
        RateLimitBlockRegistry.Block block = recorder.create(RateLimitBlockRegistry.Scope.IP, "198.51.100.23");
        assertThat(block.level()).isEqualTo(1);
        assertThat(block.expiresAt()).isAfter(NOW);
        verify(history).event(AdminAction.RATE_LIMIT_BLOCKED, block.id());
        assertThat(RateLimitBlockService.metadata(block)).containsEntry("ip", "198.51.100.23").doesNotContainKey("userId");
    }
}
