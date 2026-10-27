package network.foodsharing.verification.domain;

/** Who generated a passport: the user (download) or an ambassador (batch). */
public enum PassportKind {
    SELF,
    AMBASSADOR
}
