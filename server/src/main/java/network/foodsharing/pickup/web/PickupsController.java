package network.foodsharing.pickup.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.PickupsApi;
import network.foodsharing.api.model.AgendaDay;
import network.foodsharing.api.model.OptionalMessage;
import network.foodsharing.api.model.Pickup;
import network.foodsharing.api.model.PickupEdit;
import network.foodsharing.api.model.PickupEligibility;
import network.foodsharing.api.model.PickupHistoryPage;
import network.foodsharing.api.model.PickupOption;
import network.foodsharing.api.model.ReasonBody;
import network.foodsharing.api.model.RegularPickup;
import network.foodsharing.api.model.SignupResult;
import network.foodsharing.api.model.UserPickup;
import network.foodsharing.api.model.UserPickupPage;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.intropickup.mapper.IntroPickupMapper;
import network.foodsharing.pickup.mapper.PickupMapper;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.pickup.service.PickupSignupService;
import network.foodsharing.pickup.service.PickupUserService;
import network.foodsharing.pickup.service.RegularPickupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pickup API: schedules, sign-ups, confirmation (B4); user views, history, options, agenda (B5).
 */
@RestController
public class PickupsController implements PickupsApi {

    private final PickupScheduleService schedule;
    private final PickupSignupService signups;
    private final RegularPickupService regular;
    private final PickupMapper mapper;
    private final PickupUserService users;
    private final IntroPickupMapper introMapper;
    private final Clock clock;

    public PickupsController(PickupScheduleService schedule, PickupSignupService signups,
                             RegularPickupService regular, PickupUserService users, PickupMapper mapper,
                             IntroPickupMapper introMapper, Clock clock) {
        this.users = users;
        this.introMapper = introMapper;
        this.schedule = schedule;
        this.signups = signups;
        this.regular = regular;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<PickupEligibility> checkEligibility(UUID storeId, OffsetDateTime pickupAt) {
        return ResponseEntity.ok(mapper.toModel(schedule.eligibility(storeId, PickupMapper.instant(pickupAt))));
    }

    @Override
    public ResponseEntity<Void> confirmSignup(UUID userId, UUID storeId, OffsetDateTime pickupAt) {
        signups.confirm(storeId, PickupMapper.instant(pickupAt), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteAllFuturePickups(UUID userId, ReasonBody reasonBody) {
        users.deleteAllFuture(userId, reasonBody == null ? null : reasonBody.getReason());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Pickup> editPickup(UUID storeId, OffsetDateTime pickupAt, PickupEdit pickupEdit) {
        var slot = signups.edit(storeId, PickupMapper.instant(pickupAt), pickupEdit.getSlotCount() == null ? -1
                : pickupEdit.getSlotCount(), pickupEdit.getDescription());
        return ResponseEntity.ok(mapper.toModel(slot, slot.isAvailable(clock.instant())));
    }

    @Override
    public ResponseEntity<AgendaDay> getAgenda(UUID userId, LocalDate date) {
        var view = users.agenda(userId, date);
        AgendaDay day = new AgendaDay();
        day.setDate(view.date());
        day.setPickups(mapper.toUserPickups(view.pickups()));
        day.setIntroPickups(view.introPickups().stream().map(introMapper::toMyBooking).toList());
        return ResponseEntity.ok(day);
    }

    @Override
    public ResponseEntity<List<RegularPickup>> getRegularPickups(UUID storeId) {
        return ResponseEntity.ok(mapper.toRegularModels(regular.list(storeId)));
    }

    @Override
    public ResponseEntity<PickupHistoryPage> getStorePickupHistory(UUID storeId, OffsetDateTime from, OffsetDateTime to, Integer page, Integer size) {
        var result = users.storeHistory(storeId, PickupMapper.instant(from), PickupMapper.instant(to), page, size);
        PickupHistoryPage body = new PickupHistoryPage();
        body.setItems(mapper.toPastModels(result.getContent()));
        body.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<List<UserPickup>> listMyPickups() {
        return ResponseEntity.ok(mapper.toUserPickups(users.myPickups()));
    }

    @Override
    public ResponseEntity<UserPickupPage> listPastPickups(UUID userId, Integer page, Integer size) {
        var result = users.pastPickups(userId, page, size);
        UserPickupPage body = new UserPickupPage();
        body.setItems(mapper.toUserPickups(result.getContent()));
        body.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<List<PickupOption>> listPickupOptions() {
        return ResponseEntity.ok(mapper.toOptions(users.options()));
    }

    @Override
    public ResponseEntity<List<Pickup>> listPickups(UUID storeId, OffsetDateTime from, OffsetDateTime to) {
        return ResponseEntity.ok(mapper.toModels(schedule.listPickups(storeId, PickupMapper.instant(from),
                PickupMapper.instant(to))));
    }

    @Override
    public ResponseEntity<List<RegularPickup>> replaceRegularPickups(UUID storeId, List<RegularPickup> regularPickup) {
        return ResponseEntity.ok(mapper.toRegularModels(regular.replace(storeId, regularPickup == null ? List.of()
                : regularPickup.stream().map(PickupMapper::toEntry).toList())));
    }

    @Override
    public ResponseEntity<Void> signOut(UUID userId, UUID storeId, OffsetDateTime pickupAt, OptionalMessage optionalMessage) {
        boolean send = optionalMessage == null || !Boolean.FALSE.equals(optionalMessage.getSendMessage());
        signups.signOut(storeId, PickupMapper.instant(pickupAt), userId,
                optionalMessage == null ? null : optionalMessage.getMessage(), send);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SignupResult> signUp(UUID storeId, OffsetDateTime pickupAt) {
        SignupResult result = new SignupResult();
        result.setConfirmed(signups.signUp(storeId, PickupMapper.instant(pickupAt)));
        return ResponseEntity.ok(result);
    }
}
