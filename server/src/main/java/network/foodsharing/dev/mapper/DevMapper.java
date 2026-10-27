package network.foodsharing.dev.mapper;

import java.time.Instant;
import network.foodsharing.api.model.CapturedMail;
import network.foodsharing.api.model.DevClockState;
import network.foodsharing.api.model.DevUser;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.dev.service.DevService.DevUserView;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Dev API models. */
@Component
@Profile({"local", "e2e"})
public class DevMapper {

    /** Description key of users that are not in the seed catalog (e.g. created by a Google login in a test). */
    public static final String OTHER_USER_DESCRIPTION_KEY = "dev.users.other";

    public DevUser toDevUser(DevUserView view) {
        DevUser user = new DevUser();
        user.setId(view.user().getId());
        user.setEmail(view.user().getEmail());
        user.setDisplayName(view.user().getDisplayName());
        user.setRole(view.user().getRole());
        user.setDescriptionKey(view.entry().map(entry -> entry.descriptionKey()).orElse(OTHER_USER_DESCRIPTION_KEY));
        return user;
    }

    public CapturedMail toCapturedMail(network.foodsharing.notification.mail.CapturedMail mail) {
        CapturedMail model = new CapturedMail();
        model.setId(mail.id());
        model.setTo(mail.to());
        model.setSubject(mail.subject());
        model.setText(mail.text());
        model.setHtml(mail.html());
        model.setLinks(mail.links());
        model.setSentAt(TimeMapper.toOffset(mail.sentAt()));
        return model;
    }

    public DevClockState toClockState(Instant now, boolean fixed) {
        DevClockState state = new DevClockState();
        state.setNow(TimeMapper.toOffset(now));
        state.setFixed(fixed);
        return state;
    }
}
