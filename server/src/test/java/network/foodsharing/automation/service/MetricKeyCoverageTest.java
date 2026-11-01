package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import network.foodsharing.api.model.MetricKey;
import network.foodsharing.automation.domain.CancellationStatus;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import network.foodsharing.automation.domain.PolicyRule;
import network.foodsharing.automation.domain.RequestDecision;
import org.junit.jupiter.api.Test;

/**
 * The contract enum {@code MetricKey} equals the keys {@link MetricArchiveService} can produce (AU9). A new
 * decision, rule or status needs a contract value (and five labels, checked by {@code check-i18n.mjs}).
 */
class MetricKeyCoverageTest {

    /** Every key {@link MetricArchiveService#count} and {@link MetricArchiveService#series} can write. */
    static Set<String> producibleKeys() {
        Set<String> keys = new TreeSet<>();
        for (PickupPolicyDecision decision : PickupPolicyDecision.values()) {
            if (decision != PickupPolicyDecision.DEFERRED) { // DEFERRED writes no audit
                keys.add("SLOT_APPROVAL." + decision.name());
            }
        }
        for (PolicyRule rule : PolicyRule.values()) {
            keys.add("SLOT_DECLINE_RULE." + rule.name());
            keys.add("SLOT_SKIP_RULE." + rule.name());
        }
        for (RequestDecision decision : RequestDecision.values()) {
            keys.add("REQUEST." + decision.name());
        }
        for (CancellationStatus status : CancellationStatus.values()) {
            keys.add("CANCELLATION." + status.name());
        }
        keys.add(MetricArchiveService.ADVERT_SENT);
        keys.add(MetricArchiveService.ADVERT_FILLED);
        keys.add(MetricArchiveService.EXTRA_SLOT_ADDED);
        keys.add(MetricArchiveService.INTRO_PICKUP_DONE);
        return keys;
    }

    @Test
    void everyProducibleKeyIsAMetricKey() {
        for (String key : producibleKeys()) {
            assertThatCode(() -> MetricKey.fromValue(key)).as(key).doesNotThrowAnyException();
        }
    }

    @Test
    void metricKeyHoldsOnlyProducibleKeys() {
        Set<String> contract = Arrays.stream(MetricKey.values()).map(MetricKey::getValue)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(contract).isEqualTo(producibleKeys());
    }
}
