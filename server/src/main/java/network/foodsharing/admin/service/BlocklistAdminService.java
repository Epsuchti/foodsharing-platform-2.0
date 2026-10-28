package network.foodsharing.admin.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.domain.EmailBlocklistEntry;
import network.foodsharing.auth.domain.IpBlock;
import network.foodsharing.auth.domain.IpBlockContext;
import network.foodsharing.auth.service.EmailBlocklistService;
import network.foodsharing.auth.service.IpBlockService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email blocklist and IP block administration (C12, upstream EmailBlocklistAdmin): ORGA only, every change is
 * ADMIN history; the auth module keeps the tables and the matching rules.
 */
@Service
public class BlocklistAdminService {

    private final EmailBlocklistService blocklist;
    private final IpBlockService ipBlocks;
    private final CurrentActorService actors;
    private final HistoryService history;

    public BlocklistAdminService(EmailBlocklistService blocklist, IpBlockService ipBlocks, CurrentActorService actors,
                                 HistoryService history) {
        this.blocklist = blocklist;
        this.ipBlocks = ipBlocks;
        this.actors = actors;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<EmailBlocklistEntry> blocklist() {
        actors.requireRole(Role.ORGA);
        return blocklist.list();
    }

    @Transactional
    public EmailBlocklistEntry addBlocklistEntry(String pattern, String reason) {
        actors.requireRole(Role.ORGA);
        EmailBlocklistEntry entry = blocklist.add(pattern, reason);
        history.record(history.event(AdminAction.BLOCKLIST_ENTRY_ADDED, entry.getId())
                .metadata("pattern", entry.getPattern()).build());
        return entry;
    }

    @Transactional
    public EmailBlocklistEntry updateBlocklistEntry(UUID entryId, String pattern, String reason) {
        actors.requireRole(Role.ORGA);
        String old = blocklist.list().stream().filter(e -> e.getId().equals(entryId)).map(EmailBlocklistEntry::getPattern)
                .findFirst().orElse(null);
        EmailBlocklistEntry entry = blocklist.update(entryId, pattern, reason);
        history.record(history.event(AdminAction.BLOCKLIST_ENTRY_UPDATED, entry.getId())
                .change("pattern", old, entry.getPattern()).build());
        return entry;
    }

    @Transactional
    public void removeBlocklistEntry(UUID entryId) {
        actors.requireRole(Role.ORGA);
        EmailBlocklistEntry entry = blocklist.remove(entryId);
        history.record(history.event(AdminAction.BLOCKLIST_ENTRY_REMOVED, entryId)
                .metadata("pattern", entry.getPattern()).build());
    }

    @Transactional(readOnly = true)
    public List<IpBlock> ipBlocks() {
        actors.requireRole(Role.ORGA);
        return ipBlocks.list();
    }

    @Transactional
    public IpBlock addIpBlock(String ip, IpBlockContext context, Integer durationMinutes) {
        actors.requireRole(Role.ORGA);
        IpBlock block = ipBlocks.add(new IpBlockService.NewBlock(ip, context, durationMinutes));
        history.record(history.event(AdminAction.IP_BLOCK_ADDED, block.getId()).metadata("ip", block.getCidr())
                .metadata("context", block.getContext().name())
                .metadata("durationMinutes", durationMinutes == null ? "" : durationMinutes.toString()).build());
        return block;
    }

    @Transactional
    public void removeIpBlock(UUID blockId) {
        actors.requireRole(Role.ORGA);
        IpBlock block = ipBlocks.remove(blockId);
        history.record(history.event(AdminAction.IP_BLOCK_REMOVED, blockId).metadata("ip", block.getCidr()).build());
    }
}
