package network.foodsharing.poll.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.poll.domain.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollOptionRepository extends JpaRepository<PollOption, UUID> {

    List<PollOption> findByPollIdOrderByOptionIndex(UUID pollId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PollOption o where o.pollId = :pollId")
    int deleteByPoll(@Param("pollId") UUID pollId);
}
