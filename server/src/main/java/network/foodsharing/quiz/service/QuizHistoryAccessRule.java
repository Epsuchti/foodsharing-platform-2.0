package network.foodsharing.quiz.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.quiz.repository.QuizRepository;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** QUIZ history (editor changes): the editors of the quiz (ORGA always). */
@Component
public class QuizHistoryAccessRule implements HistoryAccessRule {

    private final QuizRepository quizzes;
    private final QuizEditorService editor;

    public QuizHistoryAccessRule(QuizRepository quizzes, QuizEditorService editor) {
        this.quizzes = quizzes;
        this.editor = editor;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.QUIZ;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return quizzes.findById(subjectId).map(quiz -> editor.mayEdit(actor, quiz.getKind())).orElse(false);
    }
}
