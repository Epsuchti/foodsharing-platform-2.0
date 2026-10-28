package network.foodsharing.upload.service;

import java.util.UUID;

/** Who uses an upload: the claiming module and the id of its owning object (e.g. mailbox + mail message id). */
public record UploadUsage(String module, UUID ownerId) {
}
