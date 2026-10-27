package network.foodsharing.onboarding.mapper;

import java.util.HashMap;
import network.foodsharing.api.model.Explainer;
import network.foodsharing.api.model.ExplainerCounters;
import network.foodsharing.api.model.Journey;
import network.foodsharing.api.model.JourneyNextAction;
import network.foodsharing.api.model.JourneyProgress;
import network.foodsharing.api.model.JourneyStep;
import network.foodsharing.api.model.JourneyStepPreview;
import network.foodsharing.api.model.JourneyStepStatus;
import network.foodsharing.api.model.JourneyTrack;
import network.foodsharing.api.model.JourneyWaitingFor;
import network.foodsharing.onboarding.service.ExplainerService;
import network.foodsharing.onboarding.service.JourneyCalculator;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

/** Journey / explainer models ↔ service views. */
@Component
public class OnboardingMapper {

    private final UserMapper users;
    private final RegionMapper regions;

    public OnboardingMapper(UserMapper users, RegionMapper regions) {
        this.users = users;
        this.regions = regions;
    }

    public Journey toJourney(JourneyCalculator.JourneyView view) {
        Journey model = new Journey();
        model.setComplete(view.complete());
        model.setCurrentStep(view.currentStep());
        model.setSteps(view.steps().stream().map(this::toStep).toList());
        model.setExtraSteps(view.extraSteps().stream().map(this::toStep).toList());
        model.setRegion(view.region() == null ? null : regions.toRef(view.region()));
        model.setTracks(view.tracks().stream().map(track -> {
            JourneyTrack t = new JourneyTrack();
            t.setKey(track.key());
            t.setSteps(track.steps().stream().map(this::toStep).toList());
            return t;
        }).toList());
        return model;
    }

    JourneyStep toStep(JourneyCalculator.StepView view) {
        JourneyStep model = new JourneyStep();
        model.setKey(view.key());
        model.setStatus(JourneyStepStatus.valueOf(view.status().name()));
        model.setTitleKey(view.titleKey());
        model.setTitle(view.title());
        if (view.progress() != null) {
            JourneyProgress progress = new JourneyProgress();
            progress.setDone(view.progress().done());
            progress.setBooked(view.progress().booked());
            progress.setRequired(view.progress().required());
            progress.setFairteilerDone(view.progress().fairteilerDone());
            model.setProgress(progress);
        }
        if (view.nextAction() != null) {
            JourneyNextAction action = new JourneyNextAction();
            action.setLabelKey(view.nextAction().labelKey());
            action.setRoute(view.nextAction().route());
            model.setNextAction(action);
        }
        model.setEstimatedDuration(view.estimatedDuration());
        model.setBlockedReason(view.blockedReason());
        if (view.blockedArgs() != null) {
            model.setBlockedArgs(new HashMap<>(view.blockedArgs()));
        }
        if (view.waitingFor() != null) {
            JourneyWaitingFor waiting = new JourneyWaitingFor();
            waiting.setLabelKey(view.waitingFor().labelKey());
            waiting.setContacts(view.waitingFor().contacts().stream().map(users::toSummary).toList());
            waiting.setTypicalDays(view.waitingFor().typicalDays());
            model.setWaitingFor(waiting);
        }
        model.setOptional(view.optional());
        if (view.details() != null) {
            model.setDetails(new HashMap<>(view.details()));
        }
        return model;
    }

    public Explainer toExplainer(ExplainerService.ExplainerView view) {
        ExplainerCounters counters = new ExplainerCounters();
        counters.setFoodsavers((int) view.counters().foodsavers());
        counters.setRegions((int) view.counters().regions());
        counters.setKgSaved(view.counters().kgSaved());
        counters.setPickups((int) view.counters().pickups());
        counters.setStores((int) view.counters().stores());
        Explainer model = new Explainer();
        model.setCounters(counters);
        model.setRegion(view.region() == null ? null : regions.toPublic(view.region()));
        model.setJourneyPreview(view.journeyPreview().stream().map(p -> {
            JourneyStepPreview preview = new JourneyStepPreview();
            preview.setKey(network.foodsharing.api.model.JourneyStepKey.valueOf(p.key().name()));
            preview.setTitleKey(p.titleKey());
            preview.setDescriptionKey(p.descriptionKey());
            preview.setEstimatedDuration(p.estimatedDuration());
            return preview;
        }).toList());
        return model;
    }
}
