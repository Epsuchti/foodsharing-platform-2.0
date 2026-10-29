package network.foodsharing.report.mapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import network.foodsharing.api.model.MediationInfo;
import network.foodsharing.api.model.Report;
import network.foodsharing.api.model.ReportPermissions;
import network.foodsharing.api.model.ReportReason;
import network.foodsharing.api.model.ReportStatus;
import network.foodsharing.api.model.ReportStoreRef;
import network.foodsharing.api.model.ReportSummary;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.api.model.WallEntry;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.report.service.MediationService;
import network.foodsharing.report.service.ReportService;
import network.foodsharing.report.service.ReportService.PostView;
import network.foodsharing.store.domain.Store;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/** Report entities → API models (G1, G2). */
@Component
public class ReportMapper {

    private final UserService users;
    private final UserMapper userMapper;
    private final ReportService reports;

    public ReportMapper(UserService users, UserMapper userMapper, ReportService reports) {
        this.users = users;
        this.userMapper = userMapper;
        this.reports = reports;
    }

    public List<ReportSummary> toSummaries(List<network.foodsharing.report.domain.Report> list) {
        Map<UUID, UserSummary> people = people(list.stream()
                .flatMap(r -> Stream.of(r.getReportedUserId(), r.getReporterId())).toList());
        Map<UUID, ReportStoreRef> stores = new HashMap<>();
        list.stream().map(network.foodsharing.report.domain.Report::getStoreId).filter(Objects::nonNull).distinct()
                .forEach(id -> reports.store(id).ifPresent(s -> stores.put(id, storeRef(s))));
        return list.stream().map(r -> new ReportSummary().id(r.getId()).regionId(r.getRegionId())
                .reason(ReportReason.fromValue(r.getReason().name())).reasonOther(r.getReasonText())
                .reportedUser(people.get(r.getReportedUserId())).reporter(people.get(r.getReporterId()))
                .store(r.getStoreId() == null ? null : stores.get(r.getStoreId()))
                .forumThreadId(r.getForumThreadId()).status(ReportStatus.fromValue(r.getStatus().name()))
                .reminderAt(TimeMapper.toOffset(r.getReminderAt())).createdAt(TimeMapper.toOffset(r.getCreatedAt())))
                .toList();
    }

    public Report toReport(ReportService.ReportView view, List<PostView> wall) {
        network.foodsharing.report.domain.Report r = view.report();
        Map<UUID, UserSummary> people = people(Stream.concat(Stream.of(r.getReportedUserId(), r.getReporterId()),
                wall.stream().map(p -> p.post().getAuthorId())).toList());
        return new Report().id(r.getId()).regionId(r.getRegionId())
                .reason(ReportReason.fromValue(r.getReason().name())).reasonOther(r.getReasonText())
                .reportedUser(people.get(r.getReportedUserId())).reporter(people.get(r.getReporterId()))
                .store(view.store() == null ? null : storeRef(view.store())).forumThreadId(r.getForumThreadId())
                .status(ReportStatus.fromValue(r.getStatus().name())).reminderAt(TimeMapper.toOffset(r.getReminderAt()))
                .createdAt(TimeMapper.toOffset(r.getCreatedAt())).message(r.getMessage())
                .consequence(r.getConsequence())
                .wall(wall.stream().filter(p -> people.containsKey(p.post().getAuthorId()))
                        .map(p -> toWallEntry(p, people.get(p.post().getAuthorId()))).toList())
                .permissions(new ReportPermissions().mayEdit(view.mayEdit()).mayDelete(view.mayDelete())
                        .mayPost(view.mayPost()));
    }

    public List<WallEntry> toWallEntries(List<PostView> views) {
        Map<UUID, UserSummary> people = people(views.stream().map(v -> v.post().getAuthorId()).toList());
        return views.stream().filter(v -> people.containsKey(v.post().getAuthorId()))
                .map(v -> toWallEntry(v, people.get(v.post().getAuthorId()))).toList();
    }

    public WallEntry toWallEntry(PostView view, UserSummary author) {
        return new WallEntry().id(view.post().getId()).author(author).body(view.post().getBody())
                .createdAt(TimeMapper.toOffset(view.post().getCreatedAt())).mayDelete(view.mayDelete());
    }

    public MediationInfo toMediationInfo(MediationService.Info info) {
        return new MediationInfo().enabled(info.enabled())
                .team(info.team().stream().map(userMapper::toSummary).toList())
                .mailboxAddress(info.mailboxAddress());
    }

    public network.foodsharing.api.model.ReportOptions toReportOptions(ReportService.ReportOptionsView view) {
        return new network.foodsharing.api.model.ReportOptions().regionId(view.regionId())
                .reasons(view.reasons().stream().map(r -> ReportReason.fromValue(r.name())).toList())
                .otherAllowed(view.otherAllowed()).buttonEnabled(view.denial() == null).denialCode(view.denial());
    }

    private static ReportStoreRef storeRef(Store store) {
        return new ReportStoreRef().id(store.getId()).name(store.getName());
    }

    private Map<UUID, UserSummary> people(Collection<UUID> ids) {
        Set<UUID> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return users.findAll(unique).stream().collect(Collectors.toMap(User::getId, userMapper::toSummary,
                (a, b) -> a));
    }
}
