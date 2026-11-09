package network.foodsharing.persona.mapper;

import network.foodsharing.api.model.Persona;
import network.foodsharing.config.PersonaPanelCondition;
import network.foodsharing.persona.service.PersonaPanelService.PersonaView;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Persona panel models (staging only). */
@Component
@Profile("staging")
@Conditional(PersonaPanelCondition.class)
public class PersonaMapper {

    public Persona toPersona(PersonaView view) {
        return new Persona()
                .id(view.user().getId())
                .email(view.user().getEmail())
                .displayName(view.user().getDisplayName())
                .role(view.user().getRole())
                .descriptionKey(view.entry().descriptionKey());
    }
}
