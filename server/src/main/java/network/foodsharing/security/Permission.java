package network.foodsharing.security;

/**
 * Region-scoped feature permissions (plan §9 layer 3). Granted per region from milestone 2; reserved in M1.
 */
public enum Permission {
    CAN_GIVE_INTRO_PICKUPS,
    CAN_MANAGE_USERS,
    CAN_MANAGE_SURVEYS
}
