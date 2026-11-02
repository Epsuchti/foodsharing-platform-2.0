package network.foodsharing.fsp.web;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.FspCleaningApi;
import network.foodsharing.api.model.CleaningShift;
import network.foodsharing.api.model.CleaningShiftEdit;
import network.foodsharing.api.model.CleaningTeam;
import network.foodsharing.api.model.RegionCleaningFsps;
import network.foodsharing.api.model.RegionCleaningFspsUpdate;
import network.foodsharing.api.model.RegularPickup;
import network.foodsharing.api.model.SignupResult;
import network.foodsharing.api.model.UserCleaningShift;
import network.foodsharing.fsp.mapper.FspCleaningMapper;
import network.foodsharing.fsp.service.FspCleaningQueries;
import network.foodsharing.fsp.service.FspCleaningRegionService;
import network.foodsharing.fsp.service.FspCleaningScheduleService;
import network.foodsharing.fsp.service.FspCleaningSignupService;
import network.foodsharing.fsp.service.FspCleaningTeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Fairteiler cleaning shifts and the cleaning Fairteiler of a region (FS2). */
@RestController
public class FspCleaningController implements FspCleaningApi {

    private final FspCleaningQueries queries;
    private final FspCleaningScheduleService schedule;
    private final FspCleaningSignupService signups;
    private final FspCleaningTeamService team;
    private final FspCleaningRegionService regions;
    private final FspCleaningMapper mapper;
    private final Clock clock;

    public FspCleaningController(FspCleaningQueries queries, FspCleaningScheduleService schedule, FspCleaningSignupService signups,
                                 FspCleaningTeamService team, FspCleaningRegionService regions,
                                 FspCleaningMapper mapper, Clock clock) {
        this.queries = queries;
        this.schedule = schedule;
        this.signups = signups;
        this.team = team;
        this.regions = regions;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<List<UserCleaningShift>> listMyCleaningShifts() {
        return ResponseEntity.ok(queries.myShifts().stream().map(FspCleaningMapper::toUserShift).toList());
    }

    @Override
    public ResponseEntity<List<RegularPickup>> getRegularCleaningShifts(UUID fspId) {
        return ResponseEntity.ok(mapper.toRegularModels(schedule.regular(fspId)));
    }

    @Override
    public ResponseEntity<List<RegularPickup>> replaceRegularCleaningShifts(UUID fspId, List<RegularPickup> body) {
        return ResponseEntity.ok(mapper.toRegularModels(schedule.replaceRegular(fspId, body == null ? List.of()
                : body.stream().map(FspCleaningMapper::toEntry).toList())));
    }

    @Override
    public ResponseEntity<List<CleaningShift>> listCleaningShifts(UUID fspId, OffsetDateTime from,
                                                                  OffsetDateTime to) {
        return ResponseEntity.ok(mapper.toShifts(fspId, schedule.list(fspId, FspCleaningMapper.instant(from),
                FspCleaningMapper.instant(to))));
    }

    @Override
    public ResponseEntity<CleaningShift> editCleaningShift(UUID fspId, OffsetDateTime shiftAt,
                                                           CleaningShiftEdit body) {
        var slot = schedule.edit(fspId, FspCleaningMapper.instant(shiftAt),
                body.getSlotCount() == null ? -1 : body.getSlotCount(), body.getDescription());
        return ResponseEntity.ok(mapper.toShift(fspId, slot, slot.isAvailable(clock.instant())));
    }

    @Override
    public ResponseEntity<SignupResult> signUpForCleaning(UUID fspId, OffsetDateTime shiftAt) {
        return ResponseEntity.ok(new SignupResult().confirmed(signups.signUp(fspId,
                FspCleaningMapper.instant(shiftAt))));
    }

    @Override
    public ResponseEntity<Void> signOutOfCleaning(UUID fspId, OffsetDateTime shiftAt, UUID userId) {
        signups.signOut(fspId, FspCleaningMapper.instant(shiftAt), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> confirmCleaningSignup(UUID fspId, OffsetDateTime shiftAt, UUID userId) {
        signups.confirm(fspId, FspCleaningMapper.instant(shiftAt), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CleaningTeam> getCleaningTeam(UUID fspId) {
        return ResponseEntity.ok(mapper.toTeam(team.team(fspId)));
    }

    @Override
    public ResponseEntity<RegionCleaningFsps> getRegionCleaningFsps(UUID regionId) {
        return ResponseEntity.ok(mapper.toRegion(regions.get(regionId)));
    }

    @Override
    public ResponseEntity<RegionCleaningFsps> replaceRegionCleaningFsps(UUID regionId, RegionCleaningFspsUpdate body) {
        return ResponseEntity.ok(mapper.toRegion(regions.replace(regionId, body.getFspIds())));
    }
}
