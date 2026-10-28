package network.foodsharing.mailbox.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.mailbox.domain.MailAttachment;
import network.foodsharing.mailbox.domain.MailMessage;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxAction;
import network.foodsharing.mailbox.domain.MailboxMember;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import network.foodsharing.mailbox.repository.MailAttachmentRepository;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.mailbox.repository.MailboxMemberRepository;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mailbox administration (MB1, MB6; A35): region/group and personal mailboxes, email-name rules, members, deletion.
 */
@Service
public class MailboxService {

    /** A35 email-name rule (lower case after normalising). */
    static final Pattern EMAIL_NAME = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,62}$");

    private final MailboxRepository mailboxes;
    private final MailboxMemberRepository members;
    private final MailMessageRepository mails;
    private final MailAttachmentRepository attachments;
    private final MailboxAccessService access;
    private final MailAttachmentStore attachmentStore;
    private final RegionService regions;
    private final UserService users;
    private final PermissionService permissions;
    private final HistoryService history;
    private final AppProperties properties;

    public MailboxService(MailboxRepository mailboxes, MailboxMemberRepository members, MailMessageRepository mails,
                          MailAttachmentRepository attachments, MailboxAccessService access,
                          MailAttachmentStore attachmentStore, RegionService regions, UserService users,
                          PermissionService permissions, HistoryService history, AppProperties properties) {
        this.mailboxes = mailboxes;
        this.members = members;
        this.mails = mails;
        this.attachments = attachments;
        this.access = access;
        this.attachmentStore = attachmentStore;
        this.regions = regions;
        this.users = users;
        this.permissions = permissions;
        this.history = history;
        this.properties = properties;
    }

    /** {@code <emailName>@<domain>}; without a configured domain the bare email name. */
    public String address(Mailbox mailbox) {
        String domain = properties.getMailbox().getDomain();
        return domain == null || domain.isBlank() ? mailbox.getEmailName() : mailbox.getEmailName() + "@" + domain;
    }

    /**
     * Validates and normalises an email name (lower case, A35 pattern, not reserved, unique across all mailboxes).
     *
     * @throws ApiException 400 EMAIL_NAME_INVALID, 409 EMAIL_NAME_RESERVED / EMAIL_NAME_TAKEN
     */
    @Transactional(readOnly = true)
    public String validateEmailName(String raw, UUID exceptMailboxId) {
        String name = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (!EMAIL_NAME.matcher(name).matches() || name.contains("..") || name.endsWith(".") || name.endsWith("-")) {
            throw ApiException.badRequest(ApiErrorCode.EMAIL_NAME_INVALID);
        }
        boolean reserved = properties.getMailbox().getReservedNames().stream()
                .anyMatch(r -> r.strip().equalsIgnoreCase(name));
        if (reserved) {
            throw ApiException.conflict(ApiErrorCode.EMAIL_NAME_RESERVED);
        }
        mailboxes.findByEmailNameIgnoreCase(name).filter(m -> !m.getId().equals(exceptMailboxId)).ifPresent(m -> {
            throw ApiException.conflict(ApiErrorCode.EMAIL_NAME_TAKEN);
        });
        return name;
    }

    /**
     * MB1: a mailbox for a region or working group.
     *
     * @throws ApiException 403 ACCESS_DENIED, 404 REGION_NOT_FOUND, 409 MAILBOX_ALREADY_EXISTS (+ email-name codes)
     */
    @Transactional
    public Mailbox createForRegion(SessionPrincipal actor, UUID regionId, String emailName, String name) {
        Region region = regions.require(regionId);
        permissions.requireMayAdministrateRegion(actor, regionId);
        if (mailboxes.findByOwnerTypeAndOwnerId(MailboxOwnerType.REGION, regionId).isPresent()) {
            throw ApiException.conflict(ApiErrorCode.MAILBOX_ALREADY_EXISTS);
        }
        String normalised = validateEmailName(emailName, null);
        Mailbox mailbox = create(MailboxOwnerType.REGION, regionId, normalised,
                name == null || name.isBlank() ? region.getName() : name.strip());
        record(MailboxAction.MAILBOX_CREATED, mailbox).metadata(Map.of("emailName", normalised)).buildAndRecord();
        return mailbox;
    }

    /**
     * MB6: a personal mailbox for an ambassador (or ORGA).
     *
     * @throws ApiException 403 ACCESS_DENIED / MAILBOX_NOT_ALLOWED (target role), 404 USER_NOT_FOUND,
     *                      409 MAILBOX_ALREADY_EXISTS (+ email-name codes)
     */
    @Transactional
    public Mailbox createForUser(SessionPrincipal actor, UUID userId, String emailName, String name) {
        User user = users.require(userId);
        if (!permissions.isOrga(actor) && !permissions.mayAdministrateUser(actor, userId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (!PermissionService.isAtLeast(user.getRole(), Role.AMBASSADOR)) {
            throw ApiException.forbidden(ApiErrorCode.MAILBOX_NOT_ALLOWED);
        }
        if (user.getMailboxId() != null
                || mailboxes.findByOwnerTypeAndOwnerId(MailboxOwnerType.USER, userId).isPresent()) {
            throw ApiException.conflict(ApiErrorCode.MAILBOX_ALREADY_EXISTS);
        }
        String normalised = validateEmailName(emailName, null);
        Mailbox mailbox = create(MailboxOwnerType.USER, userId, normalised,
                name == null || name.isBlank() ? user.getDisplayName() : name.strip());
        users.setMailboxId(userId, mailbox.getId());
        record(MailboxAction.MAILBOX_CREATED, mailbox).secondarySubject(userId)
                .metadata(Map.of("emailName", normalised)).buildAndRecord();
        return mailbox;
    }

    private Mailbox create(MailboxOwnerType type, UUID ownerId, String emailName, String name) {
        Mailbox mailbox = new Mailbox();
        mailbox.setOwnerType(type);
        mailbox.setOwnerId(ownerId);
        mailbox.setEmailName(emailName);
        mailbox.setName(name.length() > 200 ? name.substring(0, 200) : name);
        return mailboxes.save(mailbox);
    }

    /**
     * Renames the mailbox and/or changes its email name (managers).
     *
     * @throws ApiException 403 ACCESS_DENIED, 404 MAILBOX_NOT_FOUND (+ email-name codes)
     */
    @Transactional
    public Mailbox update(SessionPrincipal actor, UUID mailboxId, String emailName, String name) {
        Mailbox mailbox = access.requireManageable(actor, mailboxId);
        if (emailName != null && !emailName.strip().equalsIgnoreCase(mailbox.getEmailName())) {
            String normalised = validateEmailName(emailName, mailboxId);
            record(MailboxAction.EMAIL_NAME_CHANGED, mailbox).change("emailName", mailbox.getEmailName(), normalised)
                    .buildAndRecord();
            mailbox.setEmailName(normalised);
        }
        if (name != null && !name.isBlank() && !name.strip().equals(mailbox.getName())) {
            record(MailboxAction.FIELD_CHANGED, mailbox).change("name", mailbox.getName(), name.strip())
                    .buildAndRecord();
            mailbox.setName(name.strip());
        }
        return mailbox;
    }

    /**
     * ORGA deletes a mailbox; with mails only when {@code force} (mails and their attachments are removed).
     *
     * @throws ApiException 403 ACCESS_DENIED, 404 MAILBOX_NOT_FOUND, 409 MAILBOX_NOT_EMPTY
     */
    @Transactional
    public void delete(SessionPrincipal actor, UUID mailboxId, boolean force) {
        permissions.requireOrga(actor);
        Mailbox mailbox = access.require(mailboxId);
        long count = mails.countByMailboxId(mailboxId);
        if (count > 0 && !force) {
            throw ApiException.conflict(ApiErrorCode.MAILBOX_NOT_EMPTY);
        }
        for (MailMessage mail : mails.findByMailboxId(mailboxId)) {
            List<MailAttachment> files = attachments.findByMailIdOrderByCreatedAtAsc(mail.getId());
            attachmentStore.deleteAll(files);
            mails.delete(mail);
        }
        members.deleteAll(members.findByMailboxId(mailboxId));
        if (mailbox.getOwnerType() == MailboxOwnerType.USER) {
            users.setMailboxId(mailbox.getOwnerId(), null);
        }
        record(MailboxAction.MAILBOX_DELETED, mailbox)
                .metadata(Map.of("emailName", mailbox.getEmailName(), "mails", count)).buildAndRecord();
        mailboxes.delete(mailbox);
    }

    /** Configured members (readers besides the ambassadors); readers and managers may list them. */
    @Transactional(readOnly = true)
    public List<User> members(SessionPrincipal actor, UUID mailboxId) {
        Mailbox mailbox = access.require(mailboxId);
        if (!access.mayRead(actor, mailbox) && !access.mayManage(actor, mailbox)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return users.findAll(members.findByMailboxId(mailboxId).stream().map(MailboxMember::getUserId).toList());
    }

    /**
     * Replaces the member list (managers). Members must be active and may have a mailbox (role at least
     * STORE_MANAGER, upstream {@code mayHaveMailbox}).
     *
     * @throws ApiException 400 VALIDATION_FAILED, 403 ACCESS_DENIED, 404 MAILBOX_NOT_FOUND / USER_NOT_FOUND
     */
    @Transactional
    public List<User> setMembers(SessionPrincipal actor, UUID mailboxId, List<UUID> userIds) {
        Mailbox mailbox = access.requireManageable(actor, mailboxId);
        Set<UUID> wanted = new LinkedHashSet<>(userIds == null ? List.of() : userIds);
        List<User> found = users.findActive(wanted);
        if (found.size() != wanted.size()) {
            throw ApiException.notFound(ApiErrorCode.USER_NOT_FOUND);
        }
        if (found.stream().anyMatch(u -> !MailboxAccessService.mayHaveMailbox(u.getRole()))) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        List<MailboxMember> current = members.findByMailboxId(mailboxId);
        Set<UUID> existing = current.stream().map(MailboxMember::getUserId).collect(Collectors.toSet());
        for (MailboxMember member : current) {
            if (!wanted.contains(member.getUserId())) {
                members.delete(member);
                record(MailboxAction.MEMBER_REMOVED, mailbox).secondarySubject(member.getUserId()).buildAndRecord();
            }
        }
        for (UUID userId : wanted) {
            if (!existing.contains(userId)) {
                MailboxMember member = new MailboxMember();
                member.setMailboxId(mailboxId);
                member.setUserId(userId);
                members.save(member);
                record(MailboxAction.MEMBER_ADDED, mailbox).secondarySubject(userId).buildAndRecord();
            }
        }
        return users.findAll(wanted);
    }

    private Recorder record(MailboxAction action, Mailbox mailbox) {
        HistoryEvent.Builder builder = history.event(action, mailbox.getId());
        if (mailbox.getOwnerType() == MailboxOwnerType.REGION) {
            builder.region(mailbox.getOwnerId());
        }
        return new Recorder(builder);
    }

    /** Small fluent wrapper so every call site ends with an explicit record. */
    private final class Recorder {
        private final HistoryEvent.Builder builder;

        Recorder(HistoryEvent.Builder builder) {
            this.builder = builder;
        }

        Recorder metadata(Map<String, ?> values) {
            builder.metadata(values);
            return this;
        }

        Recorder secondarySubject(UUID id) {
            builder.secondarySubject(id);
            return this;
        }

        Recorder change(String field, Object oldValue, Object newValue) {
            builder.change(field, oldValue, newValue);
            return this;
        }

        void buildAndRecord() {
            history.record(builder.build());
        }
    }
}
