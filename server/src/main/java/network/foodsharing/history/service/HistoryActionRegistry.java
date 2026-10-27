package network.foodsharing.history.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.SubjectType;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * All {@link HistoryAction} enums of all modules, discovered on the classpath at startup (no central list to edit).
 */
@Component
public class HistoryActionRegistry {

    public static final String BASE_PACKAGE = "network.foodsharing";

    private final List<HistoryAction> actions;
    private final Map<String, HistoryAction> byKey = new LinkedHashMap<>();

    public HistoryActionRegistry() {
        this(scan(BASE_PACKAGE));
    }

    public HistoryActionRegistry(List<HistoryAction> actions) {
        List<HistoryAction> sorted = new ArrayList<>(actions);
        sorted.sort(Comparator.comparing((HistoryAction a) -> a.subjectType().name()).thenComparing(HistoryAction::code));
        this.actions = List.copyOf(sorted);
        for (HistoryAction action : this.actions) {
            byKey.putIfAbsent(key(action.subjectType(), action.code()), action);
        }
    }

    public List<HistoryAction> all() {
        return actions;
    }

    public List<HistoryAction> forSubjectType(SubjectType subjectType) {
        return actions.stream().filter(action -> action.subjectType() == subjectType).toList();
    }

    public Optional<HistoryAction> find(SubjectType subjectType, String code) {
        return Optional.ofNullable(byKey.get(key(subjectType, code)));
    }

    public static String labelKey(SubjectType subjectType, String code) {
        return "history.action." + subjectType.name() + "." + code;
    }

    /** Every enum implementing {@link HistoryAction} below {@code basePackage}. */
    public static List<HistoryAction> scan(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(HistoryAction.class));
        List<HistoryAction> found = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(basePackage)) {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), HistoryActionRegistry.class.getClassLoader());
            if (type.isEnum() && HistoryAction.class.isAssignableFrom(type)) {
                for (Object constant : type.getEnumConstants()) {
                    found.add((HistoryAction) constant);
                }
            }
        }
        return found;
    }

    private static String key(SubjectType subjectType, String code) {
        return subjectType.name() + ":" + code;
    }
}
