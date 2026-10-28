package network.foodsharing.history;

/** What a history entry is about. Owned by the history module, extended per milestone (mirrors HistorySubjectType). */
public enum SubjectType {
    USER, REGION, GROUP, STORE, PICKUP, INTRO_PICKUP, CONVERSATION, THREAD, EVENT, POLL, SURVEY, BASKET, FSP, REPORT, SYSTEM, QUIZ, CONTENT, STORE_CHAIN, AUTOMATION, MAILBOX, UPLOAD
}
