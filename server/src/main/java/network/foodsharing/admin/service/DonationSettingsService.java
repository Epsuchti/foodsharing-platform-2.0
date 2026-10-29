package network.foodsharing.admin.service;

import java.util.List;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.api.model.Role;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Donation settings (C8 admin): a typed view on the {@code donation.*} configuration keys. */
@Service
public class DonationSettingsService {

    public record Donation(boolean showCampaignCard, boolean showModal, int modalHoursLoggedIn,
                           int modalHoursLoggedOut, String infoUrl, String iframeOneTimeUrl,
                           String iframeCampaignUrl, String iframeFriendshipCircleUrl, String iframeSelfServiceUrl,
                           List<String> campaignPageSlugs) {
    }

    private final ConfigurationService configuration;
    private final CurrentActorService actors;
    private final HistoryService history;
    private final DonationFrameSources frameSources;

    public DonationSettingsService(ConfigurationService configuration, CurrentActorService actors,
                                   HistoryService history, DonationFrameSources frameSources) {
        this.configuration = configuration;
        this.actors = actors;
        this.history = history;
        this.frameSources = frameSources;
    }

    /** Effective settings (also for public readers such as the donation modal). */
    @Transactional(readOnly = true)
    public Donation current() {
        return new Donation(configuration.bool(ConfigurationKey.DONATION_SHOW_CAMPAIGN_CARD),
                configuration.bool(ConfigurationKey.DONATION_SHOW_MODAL),
                configuration.integer(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_IN),
                configuration.integer(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_OUT),
                configuration.url(ConfigurationKey.DONATION_INFO_URL),
                configuration.url(ConfigurationKey.DONATION_IFRAME_ONE_TIME_URL),
                configuration.url(ConfigurationKey.DONATION_IFRAME_CAMPAIGN_URL),
                configuration.url(ConfigurationKey.DONATION_IFRAME_FRIENDSHIP_CIRCLE_URL),
                configuration.url(ConfigurationKey.DONATION_IFRAME_SELF_SERVICE_URL),
                configuration.list(ConfigurationKey.DONATION_CAMPAIGN_PAGE_SLUGS));
    }

    @Transactional(readOnly = true)
    public Donation get() {
        actors.requireRole(Role.ORGA);
        return current();
    }

    /** @throws network.foodsharing.common.error.ApiException 400 CONFIGURATION_VALUE_INVALID (args.key) */
    @Transactional
    public Donation update(Donation in) {
        actors.requireRole(Role.ORGA);
        configuration.set(ConfigurationKey.DONATION_SHOW_CAMPAIGN_CARD, String.valueOf(in.showCampaignCard()));
        configuration.set(ConfigurationKey.DONATION_SHOW_MODAL, String.valueOf(in.showModal()));
        configuration.set(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_IN, String.valueOf(in.modalHoursLoggedIn()));
        configuration.set(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_OUT, String.valueOf(in.modalHoursLoggedOut()));
        configuration.set(ConfigurationKey.DONATION_INFO_URL, nz(in.infoUrl()));
        configuration.set(ConfigurationKey.DONATION_IFRAME_ONE_TIME_URL, nz(in.iframeOneTimeUrl()));
        configuration.set(ConfigurationKey.DONATION_IFRAME_CAMPAIGN_URL, nz(in.iframeCampaignUrl()));
        configuration.set(ConfigurationKey.DONATION_IFRAME_FRIENDSHIP_CIRCLE_URL, nz(in.iframeFriendshipCircleUrl()));
        configuration.set(ConfigurationKey.DONATION_IFRAME_SELF_SERVICE_URL, nz(in.iframeSelfServiceUrl()));
        configuration.set(ConfigurationKey.DONATION_CAMPAIGN_PAGE_SLUGS, in.campaignPageSlugs() == null ? ""
                : String.join(",", in.campaignPageSlugs()));
        history.record(history.event(AdminAction.DONATION_SETTINGS_CHANGED, HistoryService.SYSTEM_SUBJECT_ID)
                .build());
        frameSources.invalidate();
        return current();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
