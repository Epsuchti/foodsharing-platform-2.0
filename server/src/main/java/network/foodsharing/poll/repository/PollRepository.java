package network.foodsharing.poll.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.poll.domain.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollRepository extends JpaRepository<Poll, UUID> {

    List<Poll> findByRegionIdOrderByStartsAtDesc(UUID regionId);

    List<Poll> findByRegionIdInAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByEndsAt(
            Collection<UUID> regionIds, Instant startsBefore, Instant endsAfter);

    /** Serialises votes on one poll (A36: aggregates updated under the poll row lock). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Poll p where p.id = :id")
    Optional<Poll> findForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Poll p set p.voteCount = p.voteCount + 1 where p.id = :id")
    int incrementVoteCount(@Param("id") UUID id);

    /** Started, not cancelled, start not yet announced. */
    @Query("""
            select p from Poll p where p.startNotifiedAt is null and p.cancelledAt is null
              and p.startsAt <= :now and p.endsAt > :now""")
    List<Poll> startedNotAnnounced(@Param("now") Instant now);

    /** Running, ending before {@code endsBefore}, not cancelled, end not yet announced. */
    @Query("""
            select p from Poll p where p.endNotifiedAt is null and p.cancelledAt is null
              and p.startsAt <= :now and p.endsAt > :now and p.endsAt <= :endsBefore""")
    List<Poll> endingNotAnnounced(@Param("now") Instant now, @Param("endsBefore") Instant endsBefore);
}
