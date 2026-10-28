package network.foodsharing.basket.mapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.Basket;
import network.foodsharing.api.model.BasketContact;
import network.foodsharing.api.model.BasketGrocery;
import network.foodsharing.api.model.BasketKind;
import network.foodsharing.api.model.BasketLocationType;
import network.foodsharing.api.model.BasketPermissions;
import network.foodsharing.api.model.BasketRequest;
import network.foodsharing.api.model.BasketRequestStatus;
import network.foodsharing.api.model.BasketStatus;
import network.foodsharing.api.model.BasketSummary;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.basket.domain.BasketRequestEntry;
import network.foodsharing.basket.service.BasketQueryService.BasketView;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/** Basket domain → API models (B1). */
@Component
public class BasketMapper {

    private final UserService users;
    private final UserMapper userMapper;

    public BasketMapper(UserService users, UserMapper userMapper) {
        this.users = users;
        this.userMapper = userMapper;
    }

    public static String photoUrl(UUID uploadId) {
        return uploadId == null ? null : "/api/uploads/" + uploadId + "?size=md";
    }

    public List<BasketSummary> toSummaries(List<BasketView> views) {
        Map<UUID, UserSummary> people = people(views.stream().map(v -> v.basket().getOwnerId()).toList());
        return views.stream().map(view -> toSummary(view, people)).toList();
    }

    private static BasketSummary toSummary(BasketView view, Map<UUID, UserSummary> people) {
        var basket = view.basket();
        return new BasketSummary().id(basket.getId()).description(basket.getDescription())
                .photoUrl(photoUrl(basket.getPhotoUploadId())).lat(view.lat()).lon(view.lon())
                .distanceKm(view.distanceKm()).createdAt(TimeMapper.toOffset(basket.getCreatedAt()))
                .expiresAt(TimeMapper.toOffset(basket.getExpiresAt())).owner(people.get(basket.getOwnerId()))
                .status(BasketStatus.valueOf(basket.getStatus().name())).requestCount(basket.getRequestCount());
    }

    public Basket toBasket(BasketView view) {
        var basket = view.basket();
        Set<UUID> ids = new HashSet<>();
        ids.add(basket.getOwnerId());
        view.requests().forEach(r -> ids.add(r.getRequesterId()));
        if (view.myRequest() != null) {
            ids.add(view.myRequest().getRequesterId());
        }
        Map<UUID, UserSummary> people = people(ids);
        return new Basket().id(basket.getId()).description(basket.getDescription())
                .photoUrl(photoUrl(basket.getPhotoUploadId())).lat(view.lat()).lon(view.lon())
                .weightGrams(basket.getWeightGrams())
                .locationType(BasketLocationType.valueOf(basket.getLocationType().name()))
                .groceries(view.groceries().stream().map(g -> new BasketGrocery().id(g.id()).name(g.name())).toList())
                .kinds(view.kinds().stream().map(k -> BasketKind.valueOf(k.name())).toList())
                .contact(new BasketContact().message(basket.isContactMessage())
                        .phone(basket.isContactPhone() ? basket.getPhone() : null)
                        .mobile(basket.isContactMobile() ? basket.getMobile() : null))
                .status(BasketStatus.valueOf(basket.getStatus().name()))
                .createdAt(TimeMapper.toOffset(basket.getCreatedAt()))
                .expiresAt(TimeMapper.toOffset(basket.getExpiresAt())).owner(people.get(basket.getOwnerId()))
                .requests(view.owner() || !view.requests().isEmpty()
                        ? view.requests().stream().map(r -> toRequest(r, people)).toList() : null)
                .myRequest(view.myRequest() == null ? null : toRequest(view.myRequest(), people))
                .permissions(new BasketPermissions().mayEdit(view.mayEdit()).mayDelete(view.mayDelete())
                        .mayRequest(view.mayRequest()));
    }

    public BasketRequest toRequest(BasketRequestEntry request) {
        return toRequest(request, people(List.of(request.getRequesterId())));
    }

    private static BasketRequest toRequest(BasketRequestEntry request, Map<UUID, UserSummary> people) {
        return new BasketRequest().requester(people.get(request.getRequesterId()))
                .status(BasketRequestStatus.valueOf(request.getStatus().name()))
                .requestedAt(TimeMapper.toOffset(request.getRequestedAt()))
                .answeredAt(TimeMapper.toOffset(request.getAnsweredAt())).message(request.getMessage());
    }

    private Map<UUID, UserSummary> people(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAll(Set.copyOf(ids)).stream().collect(Collectors.toMap(User::getId, userMapper::toSummary,
                (a, b) -> a));
    }
}
