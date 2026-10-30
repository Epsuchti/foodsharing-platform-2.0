package network.foodsharing.activity.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.activity.domain.ActivityType;
import network.foodsharing.activity.service.ActivityFeedService.ActivityItem;
import network.foodsharing.activity.service.ActivityFeedService.ActivitySource;
import network.foodsharing.event.service.EventWallService;
import network.foodsharing.fsp.service.FspQueryService;
import network.foodsharing.store.service.StoreWallService;
import org.springframework.stereotype.Component;

/** Wall based feed sources (A19): store walls of my stores, event walls of accepted events, followed FSPs. */
final class WallActivitySources {

    private WallActivitySources() {
    }

    @Component
    static class StoreWall implements ActivitySource {

        private final StoreWallService walls;

        StoreWall(StoreWallService walls) {
            this.walls = walls;
        }

        @Override
        public ActivityType type() {
            return ActivityType.STORE_WALL;
        }

        @Override
        public List<ActivityItem> latest(UUID userId, Instant before, int limit) {
            return walls.latestOfMyStores(userId, before, limit).stream().map(p -> new ActivityItem(p.id(), type(),
                    p.createdAt(), p.storeName(), ActivityFeedService.excerpt(p.text()),
                    "/stores/" + p.storeId(), p.authorId(), p.storeId(), p.storeName())).toList();
        }
    }

    @Component
    static class EventWall implements ActivitySource {

        private final EventWallService walls;

        EventWall(EventWallService walls) {
            this.walls = walls;
        }

        @Override
        public ActivityType type() {
            return ActivityType.EVENT_WALL;
        }

        @Override
        public List<ActivityItem> latest(UUID userId, Instant before, int limit) {
            return walls.latestOfAcceptedEvents(userId, before, limit).stream().map(p -> new ActivityItem(p.id(), type(),
                    p.createdAt(), p.eventName(), ActivityFeedService.excerpt(p.body()),
                    "/events/" + p.eventId(), p.authorId(), p.eventId(), p.eventName())).toList();
        }
    }

    @Component
    static class FspWall implements ActivitySource {

        private final FspQueryService fsps;

        FspWall(FspQueryService fsps) {
            this.fsps = fsps;
        }

        @Override
        public ActivityType type() {
            return ActivityType.FSP_WALL;
        }

        @Override
        public List<ActivityItem> latest(UUID userId, Instant before, int limit) {
            return fsps.latestOfFollowed(userId, before, limit).stream().map(p -> new ActivityItem(p.id(), type(),
                    p.createdAt(), p.fspName(), ActivityFeedService.excerpt(p.body()),
                    "/food-share-points/" + p.fspId(), p.authorId(), p.fspId(), p.fspName())).toList();
        }
    }
}
