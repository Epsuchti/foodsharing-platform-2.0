package network.foodsharing.history;

/**
 * An action that can be recorded. Every module declares its own enum ({@code AuthAction}, {@code UserAction}, …);
 * {@link #code()} is unique within {@link #subjectType()}.
 */
public interface HistoryAction {

    String code();

    SubjectType subjectType();

    RetentionPolicy retention();
}
