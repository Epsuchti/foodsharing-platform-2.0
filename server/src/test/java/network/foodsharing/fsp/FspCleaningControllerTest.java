package network.foodsharing.fsp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.schedule.SlotSchedule;
import network.foodsharing.fsp.domain.CleaningEligibilityReason;
import network.foodsharing.fsp.mapper.FspCleaningMapper;
import network.foodsharing.fsp.service.FspCleaningRegionService;
import network.foodsharing.fsp.service.FspCleaningScheduleService;
import network.foodsharing.fsp.service.FspCleaningSignupService;
import network.foodsharing.fsp.service.FspCleaningTeamService;
import network.foodsharing.fsp.web.FspCleaningController;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.WithSeedUser;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** FS2: every error documented in paths/fsp-cleaning.yaml reaches the client with its status and code. */
@WebMvcTest(FspCleaningController.class)
@Import({SecurityTestConfig.class, FspCleaningMapper.class})
@ActiveProfiles("test")
class FspCleaningControllerTest {

    static final UUID FSP = UUID.randomUUID();
    static final UUID REGION = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final String AT = "2026-11-07T09:00:00Z";
    static final Instant SHIFT = Instant.parse(AT);
    static final String BASE = "/api/food-share-points/" + FSP + "/cleaning";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private network.foodsharing.fsp.service.FspCleaningQueries queries;

    @MockitoBean
    private FspCleaningScheduleService schedule;

    @MockitoBean
    private FspCleaningSignupService signups;

    @MockitoBean
    private FspCleaningTeamService team;

    @MockitoBean
    private FspCleaningRegionService regions;

    @MockitoBean
    private UserService users;

    @MockitoBean
    private UserMapper userMapper;

    static ResultActions expect(ResultActions actions, int status, ApiErrorCode code) throws Exception {
        return actions.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code.getValue()));
    }

    @Test
    void anonymousCallsNeedAuthentication() throws Exception {
        expect(mvc.perform(get(BASE + "/shifts")), 401, ApiErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    @WithSeedUser("foodsaver")
    void readErrors() throws Exception {
        when(schedule.list(eq(FSP), any(), any())).thenThrow(ApiException.notFound(ApiErrorCode.FSP_NOT_FOUND));
        expect(mvc.perform(get(BASE + "/shifts")), 404, ApiErrorCode.FSP_NOT_FOUND);
        when(schedule.list(eq(FSP), any(), any())).thenThrow(ApiException.conflict(ApiErrorCode.CLEANING_NOT_ENABLED));
        expect(mvc.perform(get(BASE + "/shifts")), 409, ApiErrorCode.CLEANING_NOT_ENABLED);
        when(schedule.list(eq(FSP), any(), any())).thenThrow(ApiException.badRequest(ApiErrorCode.RANGE_TOO_LARGE));
        expect(mvc.perform(get(BASE + "/shifts")), 400, ApiErrorCode.RANGE_TOO_LARGE);
        when(schedule.regular(FSP)).thenThrow(ApiException.conflict(ApiErrorCode.CLEANING_NOT_ENABLED));
        expect(mvc.perform(get(BASE + "/regular-shifts")), 409, ApiErrorCode.CLEANING_NOT_ENABLED);
        when(team.team(FSP)).thenThrow(ApiException.notFound(ApiErrorCode.FSP_NOT_FOUND));
        expect(mvc.perform(get(BASE + "/team")), 404, ApiErrorCode.FSP_NOT_FOUND);
    }

    @Test
    @WithSeedUser("foodsaver")
    void shiftsAreListedWithEligibility() throws Exception {
        when(schedule.list(eq(FSP), any(), any())).thenReturn(List.of(new FspCleaningScheduleService.ShiftView(
                new SlotSchedule.Slot<>(SHIFT, 2, "Kühlschrank", true, List.of()), true,
                List.of(CleaningEligibilityReason.SHIFT_IN_PAST))));
        mvc.perform(get(BASE + "/shifts")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slotCount").value(2))
                .andExpect(jsonPath("$[0].isRegular").value(true))
                .andExpect(jsonPath("$[0].eligibility.eligible").value(false))
                .andExpect(jsonPath("$[0].eligibility.reasons[0]").value("PICKUP_IN_PAST"));
    }

    @Test
    @WithSeedUser("foodsaver")
    void scheduleEditErrors() throws Exception {
        for (ApiException error : List.of(ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED),
                ApiException.badRequest(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE),
                ApiException.badRequest(ApiErrorCode.DESCRIPTION_TOO_LONG),
                ApiException.badRequest(ApiErrorCode.DUPLICATE_PICKUP_DAY_TIME),
                ApiException.forbidden(ApiErrorCode.ACCESS_DENIED), ApiException.notFound(ApiErrorCode.FSP_NOT_FOUND),
                ApiException.conflict(ApiErrorCode.CLEANING_NOT_ENABLED))) {
            when(schedule.replaceRegular(eq(FSP), anyList())).thenThrow(error);
            expect(mvc.perform(put(BASE + "/regular-shifts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("[]")), error.getStatus().value(), error.getCode());
        }
        for (ApiException error : List.of(ApiException.badRequest(ApiErrorCode.PICKUP_IN_PAST),
                ApiException.badRequest(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE),
                ApiException.badRequest(ApiErrorCode.DESCRIPTION_TOO_LONG),
                ApiException.forbidden(ApiErrorCode.ACCESS_DENIED),
                ApiException.conflict(ApiErrorCode.MORE_OCCUPIED_SLOTS))) {
            when(schedule.edit(eq(FSP), eq(SHIFT), anyInt(), any())).thenThrow(error);
            expect(mvc.perform(patch(BASE + "/shifts/" + AT).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"slotCount\":1}")), error.getStatus().value(), error.getCode());
        }
    }

    @Test
    @WithSeedUser("foodsaver")
    void signupErrors() throws Exception {
        when(signups.signUp(FSP, SHIFT)).thenThrow(ApiException.conflict(ApiErrorCode.CLEANING_NOT_ELIGIBLE)
                .withArg("reason", "NO_FREE_SLOT"));
        expect(mvc.perform(post(BASE + "/shifts/" + AT + "/signups").with(csrf())), 409,
                ApiErrorCode.CLEANING_NOT_ELIGIBLE).andExpect(jsonPath("$.args.reason").value("NO_FREE_SLOT"));
        doThrow(ApiException.badRequest(ApiErrorCode.PICKUP_IN_PAST)).when(signups).signOut(FSP, SHIFT, USER);
        expect(mvc.perform(delete(BASE + "/shifts/" + AT + "/signups/" + USER).with(csrf())), 400,
                ApiErrorCode.PICKUP_IN_PAST);
        doThrow(ApiException.notFound(ApiErrorCode.SIGNUP_NOT_FOUND)).when(signups).signOut(FSP, SHIFT, USER);
        expect(mvc.perform(delete(BASE + "/shifts/" + AT + "/signups/" + USER).with(csrf())), 404,
                ApiErrorCode.SIGNUP_NOT_FOUND);
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(signups).signOut(FSP, SHIFT, USER);
        expect(mvc.perform(delete(BASE + "/shifts/" + AT + "/signups/" + USER).with(csrf())), 403,
                ApiErrorCode.ACCESS_DENIED);
        doThrow(ApiException.conflict(ApiErrorCode.ALREADY_CONFIRMED)).when(signups).confirm(FSP, SHIFT, USER);
        expect(mvc.perform(post(BASE + "/shifts/" + AT + "/signups/" + USER + "/confirm").with(csrf())), 409,
                ApiErrorCode.ALREADY_CONFIRMED);
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(signups).confirm(FSP, SHIFT, USER);
        expect(mvc.perform(post(BASE + "/shifts/" + AT + "/signups/" + USER + "/confirm").with(csrf())), 403,
                ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    @WithSeedUser("foodsaver")
    void regionErrors() throws Exception {
        String url = "/api/regions/" + REGION + "/cleaning-food-share-points";
        when(regions.get(REGION)).thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        expect(mvc.perform(get(url)), 403, ApiErrorCode.ACCESS_DENIED);
        doThrow(ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND)).when(regions).get(REGION);
        expect(mvc.perform(get(url)), 404, ApiErrorCode.REGION_NOT_FOUND);
        when(regions.replace(eq(REGION), anyList())).thenThrow(ApiException.badRequest(
                ApiErrorCode.FSP_NOT_CLEANING_CANDIDATE).withArg("fspId", FSP.toString()));
        expect(mvc.perform(put(url).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fspIds\":[\"" + FSP + "\"]}")), 400, ApiErrorCode.FSP_NOT_CLEANING_CANDIDATE)
                .andExpect(jsonPath("$.args.fspId").value(FSP.toString()));
    }
}
