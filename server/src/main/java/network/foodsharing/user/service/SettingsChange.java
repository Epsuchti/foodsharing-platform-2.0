package network.foodsharing.user.service;

import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Theme;

/** Settings the user changes; null = unchanged. */
public record SettingsChange(Language language, Theme theme) {
}
