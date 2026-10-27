package network.foodsharing.region.domain;

/** Region / working-group type (upstream UnitType). Stored by name. */
public enum UnitType {
    CONTINENT,
    COUNTRY,
    FEDERAL_STATE,
    REGION,
    CITY,
    BIG_CITY,
    DISTRICT,
    PART_OF_TOWN,
    WORKING_GROUP;

    /** Members join these types directly (upstream {@code UnitType::getAccessibleRegionTypes}). */
    public boolean isAccessible() {
        return this == PART_OF_TOWN || this == CITY || this == REGION || this == DISTRICT;
    }

    public boolean isGroup() {
        return this == WORKING_GROUP;
    }
}
