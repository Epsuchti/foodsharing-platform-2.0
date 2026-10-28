package network.foodsharing.upload.service;

import java.util.UUID;

/**
 * SPI (A34): a module that claims uploads decides who may read them (e.g. mailbox: the mailbox readers of the mail).
 * One bean per {@link #module()}; without a rule only the owner may read a claimed upload.
 */
public interface UploadAccessRule {

    /** Module name used in {@link UploadUsage#module()}. */
    String module();

    boolean mayRead(UUID userId, UUID usageOwnerId);
}
