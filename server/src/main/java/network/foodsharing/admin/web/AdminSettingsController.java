package network.foodsharing.admin.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.AdminSettingsApi;
import network.foodsharing.api.model.ConfigurationEntry;
import network.foodsharing.api.model.ConfigurationUpdate;
import network.foodsharing.api.model.DonationSettings;
import network.foodsharing.api.model.EmailBlocklistCreate;
import network.foodsharing.api.model.EmailBlocklistEntry;
import network.foodsharing.api.model.EmailBlocklistUpdate;
import network.foodsharing.api.model.FeatureToggleState;
import network.foodsharing.api.model.FeatureToggleUpdate;
import network.foodsharing.api.model.IpBlockCreate;
import network.foodsharing.api.model.IpBlockEntry;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class AdminSettingsController implements AdminSettingsApi {

    @Override
    public ResponseEntity<EmailBlocklistEntry> addEmailBlocklistEntry(EmailBlocklistCreate emailBlocklistCreate) {
        throw ApiException.notImplemented("addEmailBlocklistEntry");
    }

    @Override
    public ResponseEntity<IpBlockEntry> addIpBlock(IpBlockCreate ipBlockCreate) {
        throw ApiException.notImplemented("addIpBlock");
    }

    @Override
    public ResponseEntity<Void> deleteEmailBlocklistEntry(UUID entryId) {
        throw ApiException.notImplemented("deleteEmailBlocklistEntry");
    }

    @Override
    public ResponseEntity<Void> deleteIpBlock(UUID blockId) {
        throw ApiException.notImplemented("deleteIpBlock");
    }

    @Override
    public ResponseEntity<DonationSettings> getDonationSettings() {
        throw ApiException.notImplemented("getDonationSettings");
    }

    @Override
    public ResponseEntity<List<ConfigurationEntry>> listConfiguration() {
        throw ApiException.notImplemented("listConfiguration");
    }

    @Override
    public ResponseEntity<List<EmailBlocklistEntry>> listEmailBlocklist() {
        throw ApiException.notImplemented("listEmailBlocklist");
    }

    @Override
    public ResponseEntity<List<FeatureToggleState>> listFeatureToggles() {
        throw ApiException.notImplemented("listFeatureToggles");
    }

    @Override
    public ResponseEntity<List<IpBlockEntry>> listIpBlocks() {
        throw ApiException.notImplemented("listIpBlocks");
    }

    @Override
    public ResponseEntity<ConfigurationEntry> updateConfiguration(String key, ConfigurationUpdate configurationUpdate) {
        throw ApiException.notImplemented("updateConfiguration");
    }

    @Override
    public ResponseEntity<DonationSettings> updateDonationSettings(DonationSettings donationSettings) {
        throw ApiException.notImplemented("updateDonationSettings");
    }

    @Override
    public ResponseEntity<EmailBlocklistEntry> updateEmailBlocklistEntry(UUID entryId, EmailBlocklistUpdate emailBlocklistUpdate) {
        throw ApiException.notImplemented("updateEmailBlocklistEntry");
    }

    @Override
    public ResponseEntity<FeatureToggleState> updateFeatureToggle(String key, FeatureToggleUpdate featureToggleUpdate) {
        throw ApiException.notImplemented("updateFeatureToggle");
    }
}
