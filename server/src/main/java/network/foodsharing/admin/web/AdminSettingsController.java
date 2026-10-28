package network.foodsharing.admin.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.admin.mapper.AdminSettingsMapper;
import network.foodsharing.admin.service.BlocklistAdminService;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.admin.service.DonationSettingsService;
import network.foodsharing.admin.service.FeatureToggleService;
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
import network.foodsharing.auth.domain.IpBlockContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** ORGA settings (C11 feature toggles, C14 configuration, C12 email blocklist and IP blocks, C8 donations). */
@RestController
public class AdminSettingsController implements AdminSettingsApi {

    private final FeatureToggleService toggles;
    private final ConfigurationService configuration;
    private final BlocklistAdminService blocklists;
    private final DonationSettingsService donations;
    private final AdminSettingsMapper mapper;

    public AdminSettingsController(FeatureToggleService toggles, ConfigurationService configuration,
                                   BlocklistAdminService blocklists, DonationSettingsService donations,
                                   AdminSettingsMapper mapper) {
        this.toggles = toggles;
        this.configuration = configuration;
        this.blocklists = blocklists;
        this.donations = donations;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<FeatureToggleState>> listFeatureToggles() {
        return ResponseEntity.ok(toggles.list().stream().map(mapper::toToggle).toList());
    }

    @Override
    public ResponseEntity<FeatureToggleState> updateFeatureToggle(String key, FeatureToggleUpdate update) {
        return ResponseEntity.ok(mapper.toToggle(toggles.set(key, Boolean.TRUE.equals(update.getEnabled()))));
    }

    @Override
    public ResponseEntity<List<ConfigurationEntry>> listConfiguration() {
        return ResponseEntity.ok(mapper.toConfiguration(configuration.entries()));
    }

    @Override
    public ResponseEntity<ConfigurationEntry> updateConfiguration(String key, ConfigurationUpdate update) {
        return ResponseEntity.ok(mapper.toConfiguration(List.of(configuration.update(key, update.getValue())))
                .getFirst());
    }

    @Override
    public ResponseEntity<List<EmailBlocklistEntry>> listEmailBlocklist() {
        return ResponseEntity.ok(mapper.toBlocklist(blocklists.blocklist()));
    }

    @Override
    public ResponseEntity<EmailBlocklistEntry> addEmailBlocklistEntry(EmailBlocklistCreate create) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toBlocklist(List.of(
                blocklists.addBlocklistEntry(create.getPattern(), create.getReason()))).getFirst());
    }

    @Override
    public ResponseEntity<EmailBlocklistEntry> updateEmailBlocklistEntry(UUID entryId, EmailBlocklistUpdate update) {
        return ResponseEntity.ok(mapper.toBlocklist(List.of(blocklists.updateBlocklistEntry(entryId,
                update.getPattern(), update.getReason()))).getFirst());
    }

    @Override
    public ResponseEntity<Void> deleteEmailBlocklistEntry(UUID entryId) {
        blocklists.removeBlocklistEntry(entryId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<IpBlockEntry>> listIpBlocks() {
        return ResponseEntity.ok(mapper.toIpBlocks(blocklists.ipBlocks()));
    }

    @Override
    public ResponseEntity<IpBlockEntry> addIpBlock(IpBlockCreate create) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toIpBlocks(List.of(blocklists.addIpBlock(
                create.getIp(), IpBlockContext.valueOf(create.getContext().getValue()),
                create.getDurationMinutes()))).getFirst());
    }

    @Override
    public ResponseEntity<Void> deleteIpBlock(UUID blockId) {
        blocklists.removeIpBlock(blockId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<DonationSettings> getDonationSettings() {
        return ResponseEntity.ok(mapper.toDonation(donations.get()));
    }

    @Override
    public ResponseEntity<DonationSettings> updateDonationSettings(DonationSettings settings) {
        return ResponseEntity.ok(mapper.toDonation(donations.update(mapper.fromDonation(settings))));
    }
}
