package network.foodsharing.automation.domain;

/** Rule codes in audit reasons (port of the reference evaluate(); DISTANCE/NO_ADDRESS: request policy). */
public enum PolicyRule {
    MANUAL_DATE, PUBLIC_HOLIDAY, HOLIDAY_CALENDAR_UNAVAILABLE, RULES_SKIPPED_NEAR_PICKUP, GAP, EXPERIENCE,
    NO_CLEANING_STORE, CLEANING_REQUIRED, CLEANING_REMINDER, CLEANING_NEW_USER, LINKED_STORE, ALL_RULES_PASSED,
    DISTANCE, NO_ADDRESS
}
