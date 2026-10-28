package network.foodsharing.basket.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.basket.domain.Basket;
import network.foodsharing.basket.domain.BasketRequestStatus;
import network.foodsharing.basket.repository.BasketRepository;
import network.foodsharing.basket.repository.BasketRequestEntryRepository;
import network.foodsharing.user.service.CommitmentSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Profile commitments (B2): baskets offered, and baskets shared (a request answered PICKED_UP). */
@Component
public class BasketCommitments implements CommitmentSource {

    private final BasketRepository baskets;
    private final BasketRequestEntryRepository requests;

    public BasketCommitments(BasketRepository baskets, BasketRequestEntryRepository requests) {
        this.baskets = baskets;
        this.requests = requests;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Commitment> commitments(UUID userId, Instant from, Instant to) {
        List<Commitment> result = new ArrayList<>();
        List<Basket> own = baskets.findByOwnerId(userId);
        own.stream().filter(b -> !b.getCreatedAt().isBefore(from) && b.getCreatedAt().isBefore(to))
                .forEach(b -> result.add(new Commitment(Kind.BASKET_OFFERED, b.getCreatedAt())));
        if (!own.isEmpty()) {
            requests.findByBasketIdInAndStatusAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                            own.stream().map(Basket::getId).toList(), BasketRequestStatus.PICKED_UP, from, to)
                    .forEach(r -> result.add(new Commitment(Kind.BASKET_SHARED, r.getAnsweredAt())));
        }
        return result;
    }
}
