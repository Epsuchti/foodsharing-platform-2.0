package network.foodsharing.mailbox.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.mailbox.domain.MailMessage;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxMember;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.mailbox.repository.MailboxMemberRepository;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may read and manage which mailbox (A35, upstream {@code MailboxPermissions}). Readers of a region mailbox are
 * the direct ambassadors (admins) of the region/group plus the configured members; readers of a personal mailbox
 * are its owner plus members. Managers: {@code mayAdministrateRegion} for region mailboxes, ORGA or
 * {@code mayAdministrateUser} for personal ones. ORGA does not read foreign mailboxes.
 */
@Service
public class MailboxAccessService {

    private final MailboxRepository mailboxes;
    private final MailboxMemberRepository members;
    private final MailMessageRepository mails;
    private final RegionService regions;
    private final PermissionService permissions;

    public MailboxAccessService(MailboxRepository mailboxes, MailboxMemberRepository members,
                                MailMessageRepository mails, RegionService regions, PermissionService permissions) {
        this.mailboxes = mailboxes;
        this.members = members;
        this.mails = mails;
        this.regions = regions;
        this.permissions = permissions;
    }

    /** Upstream {@code mayHaveMailbox}: role at least STORE_MANAGER. */
    public static boolean mayHaveMailbox(Role role) {
        return role != null && PermissionService.isAtLeast(role, Role.STORE_MANAGER);
    }

    @Transactional(readOnly = true)
    public Set<UUID> readers(Mailbox mailbox) {
        Set<UUID> readers = new LinkedHashSet<>();
        if (mailbox.getOwnerType() == MailboxOwnerType.REGION) {
            readers.addAll(regions.adminIds(mailbox.getOwnerId()));
        } else {
            readers.add(mailbox.getOwnerId());
        }
        members.findByMailboxId(mailbox.getId()).stream().map(MailboxMember::getUserId).forEach(readers::add);
        return readers;
    }

    public boolean mayRead(SessionPrincipal actor, Mailbox mailbox) {
        if (actor == null) {
            return false;
        }
        if (mailbox.getOwnerType() == MailboxOwnerType.USER && mailbox.getOwnerId().equals(actor.userId())) {
            return true;
        }
        if (mailbox.getOwnerType() == MailboxOwnerType.REGION
                && permissions.isAmbassador(actor, mailbox.getOwnerId())) {
            return true;
        }
        return members.existsByMailboxIdAndUserId(mailbox.getId(), actor.userId());
    }

    public boolean mayManage(SessionPrincipal actor, Mailbox mailbox) {
        if (mailbox.getOwnerType() == MailboxOwnerType.REGION) {
            return permissions.mayAdministrateRegion(actor, mailbox.getOwnerId());
        }
        return permissions.isOrga(actor) || permissions.mayAdministrateUser(actor, mailbox.getOwnerId());
    }

    /** @throws ApiException 404 MAILBOX_NOT_FOUND */
    @Transactional(readOnly = true)
    public Mailbox require(UUID mailboxId) {
        return mailboxes.findById(mailboxId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.MAILBOX_NOT_FOUND));
    }

    /** @throws ApiException 404 MAILBOX_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public Mailbox requireReadable(SessionPrincipal actor, UUID mailboxId) {
        Mailbox mailbox = require(mailboxId);
        if (!mayRead(actor, mailbox)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return mailbox;
    }

    /** @throws ApiException 404 MAILBOX_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public Mailbox requireManageable(SessionPrincipal actor, UUID mailboxId) {
        Mailbox mailbox = require(mailboxId);
        if (!mayManage(actor, mailbox)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return mailbox;
    }

    /**
     * The mail and its mailbox when the actor reads that mailbox.
     *
     * @throws ApiException 404 MAILBOX_MAIL_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional(readOnly = true)
    public MailMessage requireReadableMail(SessionPrincipal actor, UUID mailId) {
        MailMessage mail = mails.findById(mailId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.MAILBOX_MAIL_NOT_FOUND));
        requireReadable(actor, mail.getMailboxId());
        return mail;
    }

    /** Mailboxes the actor reads: own personal, ambassador regions, member of; sorted personal first, then by name. */
    @Transactional(readOnly = true)
    public List<Mailbox> visibleMailboxes(SessionPrincipal actor) {
        Map<UUID, Mailbox> visible = new LinkedHashMap<>();
        mailboxes.findByOwnerTypeAndOwnerId(MailboxOwnerType.USER, actor.userId())
                .ifPresent(m -> visible.put(m.getId(), m));
        Set<UUID> adminOf = regions.adminRegionIds(actor.userId());
        if (!adminOf.isEmpty()) {
            mailboxes.findByOwnerTypeAndOwnerIdIn(MailboxOwnerType.REGION, adminOf)
                    .forEach(m -> visible.put(m.getId(), m));
        }
        mailboxes.findAllById(members.mailboxIdsOf(actor.userId())).forEach(m -> visible.put(m.getId(), m));
        List<Mailbox> result = new ArrayList<>(visible.values());
        result.sort(Comparator.comparing((Mailbox m) -> m.getOwnerType() == MailboxOwnerType.USER ? 0 : 1)
                .thenComparing(Mailbox::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}
