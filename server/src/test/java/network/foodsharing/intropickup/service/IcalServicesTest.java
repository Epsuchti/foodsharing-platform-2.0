package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.intropickup.domain.GuideProfile;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.GuideProfileRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import org.junit.jupiter.api.Test;

/** iCal output (escaping, events) and the guide feed by token (EA15); the iCal import is gone (EA16, decision 169). */
class IcalServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    @Test
    void rendersEscapedEvents() {
        IntroPickupIcalService ical = new IntroPickupIcalService(null, null, null, null, null, null,
                Clock.fixed(NOW, ZoneOffset.UTC));
        IntroPickup pickup = new IntroPickup();
        pickup.setId(UUID.fromString("00000030-0001-4000-8000-000000000001"));
        pickup.setCategory(IntroPickupCategory.BAKERY);
        pickup.setStartAt(Instant.parse("2026-11-04T17:00:00Z"));
        pickup.setBusinessName("Babu's Bakery");
        pickup.setPrivateAddress("Löwenstrasse 1, 8001 Zürich");
        pickup.setPrivateInfo("Klingeln; Kisten mitbringen\nDanke");
        String feed = ical.render(List.of(pickup));
        assertThat(feed).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(feed).contains("UID:00000030-0001-4000-8000-000000000001@foodsharing.network\r\n");
        assertThat(feed).contains("DTSTART:20261104T170000Z\r\n").contains("DTEND:20261104T180000Z\r\n");
        assertThat(feed).contains("SUMMARY:Babu's Bakery (EinAb BAKERY)\r\n");
        assertThat(feed).contains("LOCATION:Löwenstrasse 1\\, 8001 Zürich\r\n");
        assertThat(feed).contains("DESCRIPTION:Klingeln\\; Kisten mitbringen\\nDanke\r\n");
        assertThat(IntroPickupIcalService.escape("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void theGuideFeedNeedsAKnownTokenAndListsOnlyEinAbsWithBookings() {
        GuideProfileRepository profiles = mock(GuideProfileRepository.class);
        IntroPickupRepository pickups = mock(IntroPickupRepository.class);
        IntroPickupSlotRepository slots = mock(IntroPickupSlotRepository.class);
        IntroPickupIcalService ical = new IntroPickupIcalService(pickups, slots, profiles, null, null, null,
                Clock.fixed(NOW, ZoneOffset.UTC));
        for (String token : new String[] {null, " ", "unknown-token", "unknown-token.ics"}) {
            assertThatThrownBy(() -> ical.guideFeed(token)).isInstanceOfSatisfying(ApiException.class, e ->
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
        }

        GuideProfile profile = new GuideProfile();
        profile.setUserId(UUID.randomUUID());
        when(profiles.findByIcalFeedToken("secret")).thenReturn(Optional.of(profile));
        IntroPickup booked = pickup("00000030-0001-4000-8000-000000000001", "Coop Stauffacher");
        IntroPickup empty = pickup("00000030-0001-4000-8000-000000000002", "Tiny Fish");
        when(pickups.findByGuide(profile.getUserId())).thenReturn(List.of(booked, empty));
        IntroPickupSlot slot = new IntroPickupSlot();
        slot.setIntroPickupId(booked.getId());
        slot.setStatus(SlotStatus.BOOKED);
        IntroPickupSlot free = new IntroPickupSlot();
        free.setIntroPickupId(empty.getId());
        free.setStatus(SlotStatus.AVAILABLE);
        when(slots.findByPickups(List.of(booked.getId(), empty.getId()))).thenReturn(List.of(slot, free));

        String feed = ical.guideFeed("secret.ics");
        assertThat(feed).contains("Coop Stauffacher").doesNotContain("Tiny Fish");
    }

    private static IntroPickup pickup(String id, String business) {
        IntroPickup pickup = new IntroPickup();
        pickup.setId(UUID.fromString(id));
        pickup.setCategory(IntroPickupCategory.SUPERMARKET);
        pickup.setStartAt(Instant.parse("2026-11-04T17:00:00Z"));
        pickup.setBusinessName(business);
        return pickup;
    }
}
