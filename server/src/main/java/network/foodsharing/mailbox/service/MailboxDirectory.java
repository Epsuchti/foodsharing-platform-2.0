package network.foodsharing.mailbox.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.mailbox.domain.MailAttachment;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read helpers: the members' directory of region mailboxes (upstream {@code regions/mailboxes}) and attachment bytes. */
@Service
public class MailboxDirectory {

    /** A region/group mailbox address. */
    public record Entry(UUID regionId, String regionName, String address) {
    }

    private final MailboxRepository mailboxes;
    private final MailboxService mailboxService;
    private final RegionService regions;
    private final UploadService uploads;

    public MailboxDirectory(MailboxRepository mailboxes, MailboxService mailboxService, RegionService regions,
                            UploadService uploads) {
        this.mailboxes = mailboxes;
        this.mailboxService = mailboxService;
        this.regions = regions;
        this.uploads = uploads;
    }

    @Transactional(readOnly = true)
    public List<Entry> regionMailboxes() {
        List<Mailbox> all = mailboxes.findByOwnerType(MailboxOwnerType.REGION);
        Map<UUID, Region> byId = regions.findAll(all.stream().map(Mailbox::getOwnerId).toList()).stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));
        return all.stream().filter(m -> byId.containsKey(m.getOwnerId()))
                .map(m -> new Entry(m.getOwnerId(), byId.get(m.getOwnerId()).getName(), mailboxService.address(m)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Upload upload(MailAttachment attachment) {
        return uploads.find(attachment.getUploadId());
    }

    public InputStream open(Upload upload) {
        return uploads.open(upload);
    }
}
