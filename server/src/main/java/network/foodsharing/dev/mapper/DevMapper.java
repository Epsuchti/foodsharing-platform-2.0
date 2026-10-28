package network.foodsharing.dev.mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.CapturedMail;
import network.foodsharing.api.model.DevClockState;
import network.foodsharing.api.model.DevNotification;
import network.foodsharing.api.model.DevNotificationChannel;
import network.foodsharing.api.model.DevUser;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.dev.service.DevNotificationService.DevBell;
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
        model.setFrom(mail.from());
        model.setAttachmentNames(mail.attachmentNames());
        return model;
    }

    public DevClockState toClockState(Instant now, boolean fixed) {
        DevClockState state = new DevClockState();
        state.setNow(TimeMapper.toOffset(now));
        state.setFixed(fixed);
        return state;
    }

    /** A bell as BELL entry, plus a MAIL entry when it was also mailed. */
    public List<DevNotification> toDevNotifications(
            DevBell devBell) {
        var bell = devBell.bell();
        var result = new ArrayList<DevNotification>();
        result.add(devNotification(bell.getId(), devBell, DevNotificationChannel.BELL));
        if (bell.getMailOutboxId() != null) {
            result.add(devNotification(bell.getMailOutboxId(), devBell,
                    DevNotificationChannel.MAIL));
        }
        return result;
    }

    private static DevNotification devNotification(
            UUID id, DevBell devBell,
            DevNotificationChannel channel) {
        var bell = devBell.bell();
        var view = new DevNotification();
        view.setId(id);
        view.setUserId(bell.getUserId());
        view.setType(bell.getType().name());
        view.setChannel(channel);
        view.setParams(new LinkedHashMap<>(devBell.params()));
        view.setLink(bell.getLink());
        view.setIdentifier(bell.getIdentifier());
        view.setCreatedAt(TimeMapper.toOffset(bell.getCreatedAt()));
        return view;
    }
}
