package network.foodsharing.event.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.ics.IcsWriter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.event.domain.Event;
import network.foodsharing.event.domain.EventType;
import network.foodsharing.event.service.EventViews.EventItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** iCalendar export of one event and of my accepted / maybe events (E3, {@link IcsWriter}). */
@Service
@Transactional(readOnly = true)
public class EventIcsService {

    static final String PRODUCT_ID = "-//foodsharing//Events//EN";

    private final EventService events;
    private final AppProperties properties;
    private final Clock clock;

    public EventIcsService(EventService events, AppProperties properties, Clock clock) {
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** @throws network.foodsharing.common.error.ApiException 404 EVENT_NOT_FOUND, 403 ACCESS_DENIED */
    public String eventIcs(UUID eventId) {
        return write(null, List.of(events.item(eventId)));
    }

    public String myEventsIcs() {
        return write("foodsharing", events.listMineAttending());
    }

    String write(String calendarName, List<EventItem> items) {
        return IcsWriter.calendar(PRODUCT_ID, calendarName, items.stream().map(this::toIcs).toList());
    }

    IcsWriter.IcsEvent toIcs(EventItem item) {
        Event event = item.event();
        String location = item.location() != null ? item.location().singleLine()
                : event.getType() == EventType.ONLINE ? event.getOnlineLink() : null;
        String url = trimSlash(properties.getBaseUrl()) + EventNotifier.link(event.getId());
        return new IcsWriter.IcsEvent("event-" + event.getId() + "@foodsharing.network", event.getStartsAt(),
                event.getEndsAt(), event.getName(), event.getDescription(), location, url,
                event.getUpdatedAt() != null ? event.getUpdatedAt() : clock.instant());
    }

    private static String trimSlash(String url) {
        return url == null ? "" : url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
