package network.foodsharing.poll.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.poll.domain.PollOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollOptionValueRepository extends JpaRepository<PollOptionValue, UUID> {

    List<PollOptionValue> findByPollIdOrderByOptionIndexAscValueDesc(UUID pollId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PollOptionValue v where v.pollId = :pollId")
    int deleteByPoll(@Param("pollId") UUID pollId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PollOptionValue v set v.votes = v.votes + 1
            where v.pollId = :pollId and v.optionIndex = :optionIndex and v.value = :value""")
    int increment(@Param("pollId") UUID pollId, @Param("optionIndex") int optionIndex, @Param("value") int value);
}
