package network.foodsharing.report.service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.GroupMailboxAddress;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.report.domain.MediationRequest;
import network.foodsharing.report.domain.ReportAction;
import network.foodsharing.report.repository.MediationRequestRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mediation (G2): the region option {@code ENABLE_MEDIATION_BUTTON}, the team (MEDIATION group admins and members of
 * the region or its nearest ancestor, else the region's ambassadors) and requests to it (bell + mail).
 */
@Service
public class MediationService {

    static final String MAIL_REQUEST = "report/mediation-request";

    /** Mediation page data. */
    public record Info(boolean enabled, List<User> team, String mailboxAddress) {
    }

    private final RegionService regions;
    private final RegionOptionsService options;
    private final ReportPermissionService access;
    private final MediationRequestRepository requests;
    private final UserService users;
    private final CurrentActorService actors;
    private final NotificationService notifications;
    private final MailOutboxService mails;
    private final HistoryService history;
    private final AppProperties properties;
    private final ObjectProvider<GroupMailboxAddress> mailboxAddress;

    public MediationService(RegionService regions, RegionOptionsService options, ReportPermissionService access,
                            MediationRequestRepository requests, UserService users, CurrentActorService actors,
                            NotificationService notifications, MailOutboxService mails, HistoryService history,
                            AppProperties properties, ObjectProvider<GroupMailboxAddress> mailboxAddress) {
        this.regions = regions;
        this.options = options;
        this.access = access;
        this.requests = requests;
        this.users = users;
        this.actors = actors;
        this.notifications = notifications;
        this.mails = mails;
        this.history = history;
        this.properties = properties;
        this.mailboxAddress = mailboxAddress;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED (not a member) */
    @Transactional(readOnly = true)
    public Info info(UUID regionId) {
        regions.require(regionId);
        SessionPrincipal actor = actors.requirePrincipal();
        if (!access.isMember(actor, regionId) && !access.isOrga(actor)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        boolean enabled = options.read(regionId).enableMediationButton();
        if (!enabled) {
            return new Info(false, List.of(), null);
        }
        Optional<Region> group = access.mediationGroup(regionId);
        List<User> team = users.findActive(team(regionId)).stream()
                .sorted(java.util.Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
        String address = group.flatMap(g -> Optional.ofNullable(mailboxAddress.getIfAvailable())
                .flatMap(lookup -> lookup.of(g.getId()))).orElse(null);
        return new Info(true, team, address);
    }

    /**
     * Sends a mediation request to the team ({@code MEDIATION_REQUEST} bell and mail).
     *
     * @throws ApiException 404 REGION_NOT_FOUND / USER_NOT_FOUND, 403 ACCESS_DENIED / MEDIATION_DISABLED,
     *                      400 VALIDATION_FAILED
     */
    @Transactional
    public MediationRequest request(UUID regionId, String message, UUID counterpartUserId) {
        Region region = regions.require(regionId);
        SessionPrincipal actor = actors.requirePrincipal();
        ApiErrorCode denial = ReportRules.mediationDenial(options.read(regionId).enableMediationButton(),
                access.isMember(actor, regionId), access.isOrga(actor));
        if (denial != null) {
            throw ApiException.forbidden(denial);
        }
        String text = message == null ? "" : message.strip();
        if (text.length() < 10 || text.length() > 4000) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "message");
        }
        User requester = users.requireActive(actor.userId());
        User counterpart = counterpartUserId == null ? null : users.findActive(counterpartUserId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        MediationRequest request = new MediationRequest();
        request.setRegionId(regionId);
        request.setRequesterId(requester.getId());
        request.setCounterpartUserId(counterpart == null ? null : counterpart.getId());
        request.setMessage(text);
        MediationRequest saved = requests.save(request);
        var event = history.event(ReportAction.MEDIATION_REQUESTED, saved.getId())
                .secondarySubject(requester.getId()).region(regionId);
        if (counterpart != null) {
            event.metadata("counterpart", counterpart.getId().toString());
        }
        history.record(event.build());
        Set<UUID> team = team(regionId);
        team.remove(requester.getId());
        if (team.isEmpty()) {
            return saved;
        }
        String link = "/profile/" + requester.getId();
        notifications.notify(NotificationRequest.to(team, NotificationType.MEDIATION_REQUEST)
                .param("name", requester.getDisplayName()).param("region", region.getName()).link(link)
                .identifier("MEDIATION_REQUEST-" + saved.getId()).build());
        for (User member : users.findActive(team)) {
            if (member.getEmail() == null) {
                continue;
            }
            Map<String, Object> variables = new HashMap<>();
            variables.put("displayName", member.getDisplayName());
            variables.put("requesterName", requester.getDisplayName());
            variables.put("regionName", region.getName());
            variables.put("counterpartName", counterpart == null ? null : counterpart.getDisplayName());
            variables.put("message", text);
            variables.put("link", properties.getBaseUrl() + link);
            mails.enqueue(new MailDraft(member.getEmail(), MAIL_REQUEST, member.getPreferredLanguage(), variables,
                    member.getId(), NotificationType.MEDIATION_REQUEST.category().name(),
                    "MEDIATION_REQUEST-" + saved.getId()));
        }
        return saved;
    }

    /** MEDIATION group admins + members (region or nearest ancestor), else the ambassadors (nearest that has any). */
    Set<UUID> team(UUID regionId) {
        Optional<Region> group = access.mediationGroup(regionId);
        Set<UUID> team = new LinkedHashSet<>();
        if (group.isPresent()) {
            team.addAll(regions.adminIds(group.get().getId()));
            team.addAll(regions.activeMemberIds(group.get().getId()));
            return team;
        }
        team.addAll(regions.adminIds(regionId));
        if (team.isEmpty()) {
            List<Region> chain = regions.ancestors(regionId);
            for (int i = chain.size() - 1; i >= 0 && team.isEmpty(); i--) {
                team.addAll(regions.adminIds(chain.get(i).getId()));
            }
        }
        return team;
    }
}
