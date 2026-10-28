package network.foodsharing.poll.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.poll.domain.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollVoteRepository extends JpaRepository<PollVote, UUID> {

    boolean existsByPollIdAndUserId(UUID pollId, UUID userId);

    @Query("select v.pollId from PollVote v where v.userId = :userId and v.pollId in :pollIds")
    Set<UUID> votedPollIds(@Param("userId") UUID userId, @Param("pollIds") Collection<UUID> pollIds);

    @Query("select v.userId from PollVote v where v.pollId = :pollId")
    List<UUID> voterIds(@Param("pollId") UUID pollId);
}
