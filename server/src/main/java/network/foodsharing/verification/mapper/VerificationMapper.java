package network.foodsharing.verification.mapper;

import static network.foodsharing.common.mapper.TimeMapper.toOffset;

import network.foodsharing.api.model.IdentityStatus;
import network.foodsharing.api.model.PassportRequestView;
import network.foodsharing.api.model.PassportStatus;
import network.foodsharing.api.model.SlotComment;
import network.foodsharing.api.model.UserAddress;
import network.foodsharing.api.model.VerificationCandidate;
import network.foodsharing.api.model.VerificationJourney;
import network.foodsharing.api.model.WalletAvailability;
import network.foodsharing.intropickup.service.IntroPickupProgressService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.verification.service.PassportService;
import network.foodsharing.verification.service.VerificationQueueService;
import network.foodsharing.verification.service.VerificationService;
import org.springframework.stereotype.Component;

/** Verification / passport models ↔ service views. */
@Component
public class VerificationMapper {

    private final UserMapper users;

    public VerificationMapper(UserMapper users) {
        this.users = users;
    }

    public IdentityStatus toIdentity(VerificationService.IdentityView view) {
        IdentityStatus model = new IdentityStatus();
        model.setConfirmed(view.confirmed());
        model.setConfirmedAt(toOffset(view.confirmedAt()));
        model.setConfirmedBy(view.confirmedBy() == null ? null : users.toSummary(view.confirmedBy()));
        return model;
    }

    public VerificationCandidate toCandidate(VerificationQueueService.Candidate candidate) {
        User user = candidate.user();
        VerificationCandidate model = new VerificationCandidate();
        model.setUser(users.toSummary(user));
        model.setPhone(user.getMobilePhone() != null ? user.getMobilePhone() : user.getLandlinePhone());
        model.setDateOfBirth(user.getDateOfBirth());
        UserAddress address = new UserAddress();
        address.setStreet(user.getStreet());
        address.setPostcode(user.getPostcode());
        address.setCity(user.getCity());
        address.setCountry(user.getCountry());
        model.setAddress(address);
        VerificationJourney journey = new VerificationJourney();
        journey.setIntroPickupsDone(candidate.progress().counted());
        journey.setIntroPickupsRequired(candidate.progress().target());
        journey.setFairteilerDone(candidate.progress().fairteilerDone());
        journey.setQuizPassedAt(toOffset(candidate.quizPassedAt()));
        journey.setAgreementAcceptedAt(toOffset(candidate.agreementAcceptedAt()));
        journey.setLearningDone(candidate.learningDone());
        model.setJourney(journey);
        model.setIdentityConfirmedAt(toOffset(user.getIdentityConfirmedAt()));
        model.setGuideComments(candidate.comments().stream().map(this::toComment).toList());
        model.setLastIntroPickupAt(toOffset(candidate.lastIntroPickupAt()));
        model.setRegisteredAt(toOffset(user.getRegisteredAt() != null ? user.getRegisteredAt() : user.getCreatedAt()));
        return model;
    }

    private SlotComment toComment(IntroPickupProgressService.GuideComment comment) {
        SlotComment model = new SlotComment();
        model.setId(comment.id());
        model.setAuthor(comment.author() == null ? null : users.toSummary(comment.author()));
        model.setText(comment.text());
        model.setAdminComment(comment.adminComment());
        model.setCreatedAt(toOffset(comment.createdAt()));
        return model;
    }

    public PassportStatus toStatus(PassportService.StatusView view) {
        PassportStatus model = new PassportStatus();
        model.setVerified(view.verified());
        model.setLastIssuedAt(toOffset(view.lastIssuedAt()));
        model.setValidFrom(view.validFrom());
        model.setValidUntil(view.validUntil());
        model.setValid(view.valid());
        model.setRequestPending(view.requestPending());
        model.setWalletApple(WalletAvailability.valueOf(view.apple().name()));
        model.setWalletGoogle(WalletAvailability.valueOf(view.google().name()));
        return model;
    }

    public PassportRequestView toRequest(PassportService.RequestView view) {
        PassportRequestView model = new PassportRequestView();
        model.setId(view.request().getId());
        model.setUser(view.user() == null ? null : users.toSummary(view.user()));
        model.setRequestedAt(toOffset(view.request().getRequestedAt()));
        model.setFulfilledAt(toOffset(view.request().getFulfilledAt()));
        model.setFulfilledBy(view.fulfilledBy() == null ? null : users.toSummary(view.fulfilledBy()));
        return model;
    }
}
