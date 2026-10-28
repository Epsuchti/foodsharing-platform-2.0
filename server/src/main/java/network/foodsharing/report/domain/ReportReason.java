package network.foodsharing.report.domain;

/** Report reasons (upstream report reason codes; VIOLATED_* = rule-book sections). */
public enum ReportReason {
    LATE,
    NO_SHOW,
    CANCELLATION,
    SELLS,
    VIOLATED_B1_0,
    VIOLATED_B1_1,
    VIOLATED_B1_2,
    VIOLATED_B1_3,
    VIOLATED_B1_4,
    VIOLATED_B2,
    VIOLATED_B3_0,
    VIOLATED_B3_1,
    VIOLATED_B4_0,
    VIOLATED_B4_1,
    VIOLATED_B5,
    VIOLATED_B6_0,
    VIOLATED_B6_1,
    VIOLATED_B7,
    VIOLATED_B8,
    VIOLATED_B9,
    VIOLATED_B10,
    VIOLATED_B11,
    VIOLATED_B12,
    VIOLATED_B13,
    OTHER
}
