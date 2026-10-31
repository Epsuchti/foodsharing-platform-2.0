package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.ContentAction;
import network.foodsharing.content.domain.ReleaseNote;
import network.foodsharing.content.domain.ReleaseNoteI18n;
import network.foodsharing.content.repository.ReleaseNoteI18nRepository;
import network.foodsharing.content.repository.ReleaseNoteRepository;
import network.foodsharing.content.service.ReleaseNoteService;
import network.foodsharing.content.service.ReleaseNoteService.NoteInput;
import network.foodsharing.content.service.ReleaseNoteService.Text;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** C4 editor: translations are listed for ORGA and can be removed on update (never the German base). */
class ReleaseNoteServiceTest {

    ReleaseNoteRepository notes = mock(ReleaseNoteRepository.class);
    ReleaseNoteI18nRepository texts = mock(ReleaseNoteI18nRepository.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    ReleaseNoteService service = new ReleaseNoteService(notes, texts, actors, history);

    List<ReleaseNoteI18n> rows = new ArrayList<>();
    ReleaseNote note = new ReleaseNote();

    private ReleaseNoteI18n row(String language, String title) {
        ReleaseNoteI18n row = new ReleaseNoteI18n();
        row.setOwnerId(note.getId());
        row.setLanguage(language);
        row.setTitle(title);
        row.setBody(title + " body");
        return row;
    }

    @BeforeEach
    void setUp() {
        note.setId(UUID.randomUUID());
        note.setReleaseVersion("2026.11");
        note.setReleasedAt(LocalDate.of(2026, 11, 1));
        rows.addAll(List.of(row("de", "Neu"), row("fr", "Nouveau"), row("it", "Nuovo")));
        when(actors.isOrga()).thenReturn(true);
        when(notes.findById(note.getId())).thenReturn(Optional.of(note));
        when(notes.findAllByOrderByReleasedAtDescReleaseVersionDescIdDesc()).thenReturn(List.of(note));
        when(texts.findByOwnerId(note.getId())).thenAnswer(i -> List.copyOf(rows));
        when(texts.findByOwnerIdIn(anyCollection())).thenAnswer(i -> List.copyOf(rows));
        when(texts.save(any(ReleaseNoteI18n.class))).thenAnswer(i -> {
            ReleaseNoteI18n saved = i.getArgument(0);
            if (!rows.contains(saved)) {
                rows.add(saved);
            }
            return saved;
        });
        doAnswer(i -> {
            rows.removeAll((Collection<?>) i.getArgument(0));
            return null;
        }).when(texts).deleteAll(any());
    }

    @Test
    void theEditorListHasTheGermanTextAndEveryTranslation() {
        var listed = service.listForEdit();
        assertThat(listed).singleElement().satisfies(n -> {
            assertThat(n.title()).isEqualTo("Neu");
            assertThat(n.translations()).containsOnlyKeys("fr", "it");
            assertThat(n.translations().get("fr")).isEqualTo(new Text("Nouveau", "Nouveau body"));
        });
        // the public list stays localized without translations
        assertThat(service.list("fr")).singleElement().satisfies(n -> {
            assertThat(n.title()).isEqualTo("Nouveau");
            assertThat(n.translations()).isNull();
        });
        when(actors.isOrga()).thenReturn(false);
        assertThat(catchThrowableOfType(ApiException.class, service::listForEdit).getCode())
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void updateRemovesTranslationsAndRecordsIt() {
        var updated = service.update(note.getId(), new NoteInput(null, null, null, null,
                Map.of("en", new Text("New", "New body")), Set.of("it")));
        assertThat(updated.translations()).containsOnlyKeys("en", "fr");
        assertThat(rows).extracting(ReleaseNoteI18n::getLanguage).containsExactlyInAnyOrder("de", "fr", "en");
        verify(history).event(ContentAction.RELEASE_NOTE_UPDATED, note.getId());
        verify(history.event(ContentAction.RELEASE_NOTE_UPDATED, note.getId()).metadata("version", "2026.11"))
                .metadata("removedTranslations", "it");
    }

    @Test
    void theGermanBaseAndALanguageInBothListsAreRejected() {
        assertThat(catchThrowableOfType(ApiException.class, () -> service.update(note.getId(),
                new NoteInput(null, null, null, null, null, Set.of("de")))).getCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(catchThrowableOfType(ApiException.class, () -> service.update(note.getId(),
                new NoteInput(null, null, null, null, Map.of("fr", new Text("X", "Y")), Set.of("fr")))).getCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(rows).hasSize(3);
    }
}
