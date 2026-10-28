package network.foodsharing.content.service;

import java.util.ArrayList;
import java.util.List;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Petition banner (C8) and donation page (C7): views on the {@code petition.*} / {@code donation.*} configuration keys
 * plus the donation campaign content pages. Donations are not a module (A38).
 */
@Service
public class CampaignService {

    /** Used when {@code donation.campaign-page-slugs} is empty. */
    static final List<String> DEFAULT_CAMPAIGN_SLUGS = List.of("donation-campaign-1", "donation-campaign-2");

    public record Petition(String href, long signatures, int daysLeft) {
    }

    public record Donation(boolean showCampaignCard, boolean showModal, int modalHoursLoggedIn,
                           int modalHoursLoggedOut, String infoUrl, String iframeOneTimeUrl, String iframeCampaignUrl,
                           String iframeFriendshipCircleUrl, String iframeSelfServiceUrl,
                           List<ContentService.LocalizedPage> campaignPages) {
    }

    private final ConfigurationService configuration;
    private final FeatureToggleService toggles;
    private final ContentService content;

    public CampaignService(ConfigurationService configuration, FeatureToggleService toggles, ContentService content) {
        this.configuration = configuration;
        this.toggles = toggles;
        this.content = content;
    }

    /** @throws ApiException 404 FEATURE_DISABLED when PETITION_BANNER is off */
    @Transactional(readOnly = true)
    public Petition petition() {
        toggles.requireEnabled(FeatureToggle.PETITION_BANNER);
        return new Petition(configuration.url(ConfigurationKey.PETITION_URL),
                Math.max(0, configuration.integer(ConfigurationKey.PETITION_SIGNATURES)),
                Math.max(0, configuration.integer(ConfigurationKey.PETITION_DAYS_LEFT)));
    }

    /** The modal is shown only when the DONATION_MODAL toggle and {@code donation.show-modal} are both on. */
    @Transactional(readOnly = true)
    public Donation donation(String language) {
        List<String> slugs = configuration.list(ConfigurationKey.DONATION_CAMPAIGN_PAGE_SLUGS);
        List<ContentService.LocalizedPage> pages = new ArrayList<>();
        for (String slug : slugs.isEmpty() ? DEFAULT_CAMPAIGN_SLUGS : slugs) {
            content.findPublished(slug, language).ifPresent(pages::add);
        }
        return new Donation(configuration.bool(ConfigurationKey.DONATION_SHOW_CAMPAIGN_CARD),
                toggles.isEnabled(FeatureToggle.DONATION_MODAL) && configuration.bool(ConfigurationKey.DONATION_SHOW_MODAL),
                configuration.integer(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_IN),
                configuration.integer(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_OUT),
                blankToNull(configuration.url(ConfigurationKey.DONATION_INFO_URL)),
                blankToNull(configuration.url(ConfigurationKey.DONATION_IFRAME_ONE_TIME_URL)),
                blankToNull(configuration.url(ConfigurationKey.DONATION_IFRAME_CAMPAIGN_URL)),
                blankToNull(configuration.url(ConfigurationKey.DONATION_IFRAME_FRIENDSHIP_CIRCLE_URL)),
                blankToNull(configuration.url(ConfigurationKey.DONATION_IFRAME_SELF_SERVICE_URL)),
                pages);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
