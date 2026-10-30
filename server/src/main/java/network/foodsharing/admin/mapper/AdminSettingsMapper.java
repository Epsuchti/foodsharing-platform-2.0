package network.foodsharing.admin.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.admin.service.DonationSettingsService;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ConfigurationEntry;
import network.foodsharing.api.model.DonationSettings;
import network.foodsharing.api.model.EmailBlocklistEntry;
import network.foodsharing.api.model.FeatureToggleKey;
import network.foodsharing.api.model.FeatureToggleState;
import network.foodsharing.api.model.IpBlockContext;
import network.foodsharing.api.model.IpBlockEntry;
import network.foodsharing.api.model.PageMeta;
import network.foodsharing.api.model.UploadAdminItem;
import network.foodsharing.api.model.UploadAdminPage;
import network.foodsharing.api.model.UploadAdminSummary;
import network.foodsharing.api.model.UploadModuleCount;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.auth.domain.IpBlock;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Admin settings entities → API models (C10, C11, C12, C14). */
@Component
public class AdminSettingsMapper {

    private final UserService users;
    private final UserMapper userMapper;

    public AdminSettingsMapper(UserService users, UserMapper userMapper) {
        this.users = users;
        this.userMapper = userMapper;
    }

    public FeatureToggleState toToggle(FeatureToggleService.ToggleState state) {
        return new FeatureToggleState().key(FeatureToggleKey.fromValue(state.toggle().name()))
                .enabled(state.enabled()).defaultEnabled(state.toggle().defaultEnabled())
                .description(state.description()).updatedAt(TimeMapper.toOffset(state.updatedAt()));
    }

    public List<ConfigurationEntry> toConfiguration(List<ConfigurationService.Entry> entries) {
        Map<UUID, UserSummary> people = people(entries.stream().map(ConfigurationService.Entry::updatedBy).toList());
        return entries.stream().map(e -> new ConfigurationEntry().key(e.key().key())
                .type(network.foodsharing.api.model.ConfigurationType.fromValue(e.key().type().name())).value(e.value())
                .defaultValue(e.key().defaultValue()).description(e.description())
                .updatedAt(TimeMapper.toOffset(e.updatedAt()))
                .updatedBy(e.updatedBy() == null ? null : people.get(e.updatedBy()))).toList();
    }

    public List<EmailBlocklistEntry> toBlocklist(List<network.foodsharing.auth.domain.EmailBlocklistEntry> list) {
        Map<UUID, UserSummary> people = people(list.stream()
                .map(network.foodsharing.auth.domain.EmailBlocklistEntry::getCreatedBy).toList());
        return list.stream().map(e -> new EmailBlocklistEntry().id(e.getId()).pattern(e.getPattern())
                .reason(e.getReason()).createdAt(TimeMapper.toOffset(e.getCreatedAt()))
                .createdBy(e.getCreatedBy() == null ? null : people.get(e.getCreatedBy()))).toList();
    }

    public List<IpBlockEntry> toIpBlocks(List<IpBlock> list) {
        Map<UUID, UserSummary> people = people(list.stream().map(IpBlock::getCreatedBy).toList());
        return list.stream().map(b -> new IpBlockEntry().id(b.getId()).ip(b.getCidr())
                .context(IpBlockContext.fromValue(b.getContext().name()))
                .createdAt(TimeMapper.toOffset(b.getCreatedAt())).expiresAt(TimeMapper.toOffset(b.getUntilAt()))
                .createdBy(b.getCreatedBy() == null ? null : people.get(b.getCreatedBy()))).toList();
    }

    public DonationSettings toDonation(DonationSettingsService.Donation d) {
        return new DonationSettings().showCampaignCard(d.showCampaignCard()).showModal(d.showModal())
                .modalHoursLoggedIn(d.modalHoursLoggedIn()).modalHoursLoggedOut(d.modalHoursLoggedOut())
                .infoUrl(d.infoUrl()).iframeOneTimeUrl(d.iframeOneTimeUrl()).iframeCampaignUrl(d.iframeCampaignUrl())
                .iframeFriendshipCircleUrl(d.iframeFriendshipCircleUrl())
                .iframeSelfServiceUrl(d.iframeSelfServiceUrl()).campaignPageSlugs(d.campaignPageSlugs());
    }

    public DonationSettingsService.Donation fromDonation(DonationSettings in) {
        return new DonationSettingsService.Donation(Boolean.TRUE.equals(in.getShowCampaignCard()),
                Boolean.TRUE.equals(in.getShowModal()), in.getModalHoursLoggedIn(), in.getModalHoursLoggedOut(),
                in.getInfoUrl(), in.getIframeOneTimeUrl(), in.getIframeCampaignUrl(),
                in.getIframeFriendshipCircleUrl(), in.getIframeSelfServiceUrl(), in.getCampaignPageSlugs());
    }

    public UploadAdminPage toUploadPage(Page<Upload> page) {
        Map<UUID, UserSummary> people = people(page.getContent().stream().map(Upload::getOwnerUserId).toList());
        List<UploadAdminItem> items = page.getContent().stream().map(u -> new UploadAdminItem().id(u.getId())
                .fileName(u.getFileName()).mimeType(u.getMimeType()).size(u.getSize())
                .owner(u.getOwnerUserId() == null ? null : people.get(u.getOwnerUserId()))
                .usageModule(u.getUsageModule())
                .usageOwnerId(u.getUsageOwnerId())
                .createdAt(TimeMapper.toOffset(u.getCreatedAt())).expiresAt(TimeMapper.toOffset(u.getExpiresAt())))
                .toList();
        PageMeta meta = PageMapper.toPageMeta(page);
        return new UploadAdminPage().items(items).page(meta);
    }

    public UploadAdminSummary toUploadSummary(UploadService.Summary summary) {
        return new UploadAdminSummary().count(summary.count()).bytes(summary.bytes())
                .unclaimedCount(summary.unclaimedCount())
                .byModule(summary.byModule().stream().map(m -> new UploadModuleCount().module(m.module())
                        .count(m.count()).bytes(m.bytes())).toList());
    }

    private Map<UUID, UserSummary> people(Collection<UUID> ids) {
        var unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return users.findAll(unique).stream().collect(Collectors.toMap(User::getId, userMapper::toSummary,
                (a, b) -> a));
    }
}
