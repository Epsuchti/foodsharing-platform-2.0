package network.foodsharing.quiz.domain;

/** The quizzes (upstream QuizID; the French foodsaver quiz is the fr translation of FOODSAVER; EINAB_GUIDE is new, Q4). */
public enum QuizKind {
    FOODSAVER,
    STORE_MANAGER,
    AMBASSADOR,
    HYGIENE,
    FOODSHARER,
    SAVING_FOOD,
    REPORTS,
    /** Q4: the EinAb guide quiz; a pass makes the user a candidate for the guide release (no role change). */
    EINAB_GUIDE;
}
