package network.foodsharing.event.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.event.domain.Event;
import network.foodsharing.event.domain.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /** Events of a region ending at or after {@code endsAfter} and starting before {@code startsBefore}. */
    @Query("""
            select e from Event e where e.regionId = :regionId and e.deletedAt is null
              and (:publicOnly = false or e.publicEvent = true)
              and e.endsAt >= :endsAfter and e.startsAt < :startsBefore
            order by e.startsAt, e.id""")
    List<Event> listRegion(@Param("regionId") UUID regionId, @Param("publicOnly") boolean publicOnly,
                           @Param("endsAfter") Instant endsAfter, @Param("startsBefore") Instant startsBefore);

    /** Events the user has an invitation for (any status, or only {@code statuses}). */
    @Query("""
            select e from Event e where e.deletedAt is null and e.endsAt >= :endsAfter
              and exists (select i.id from EventInvitation i where i.eventId = e.id and i.userId = :userId
                          and i.status in :statuses)
            order by e.startsAt, e.id""")
    List<Event> listInvited(@Param("userId") UUID userId, @Param("statuses") Collection<InvitationStatus> statuses,
                            @Param("endsAfter") Instant endsAfter);
}
