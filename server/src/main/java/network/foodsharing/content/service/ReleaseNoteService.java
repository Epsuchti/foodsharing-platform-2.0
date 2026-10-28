package network.foodsharing.content.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.ContentAction;
import network.foodsharing.content.domain.ReleaseNote;
import network.foodsharing.content.domain.ReleaseNoteI18n;
import network.foodsharing.content.repository.ReleaseNoteI18nRepository;
import network.foodsharing.content.repository.ReleaseNoteRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Release notes / what is new (C4). Public read (requested language → de → any, A14); ORGA writes. The main
 * title/body are German; {@code translations} add or replace other languages.
 */
@Service
public class ReleaseNoteService {

    public static final String BASE_LANGUAGE = "de";

    public record Text(String title, String body) {
    }

    public record NoteInput(String version, LocalDate releasedAt, String title, String body,
                            Map<String, Text> translations) {
    }

    public record LocalizedNote(ReleaseNote note, String title, String body, String language) {
    }

    private final ReleaseNoteRepository notes;
    private final ReleaseNoteI18nRepository texts;
    private final CurrentActorService currentActor;
    private final HistoryService history;

    public ReleaseNoteService(ReleaseNoteRepository notes, ReleaseNoteI18nRepository texts,
                              CurrentActorService currentActor, HistoryService history) {
        this.notes = notes;
        this.texts = texts;
        this.currentActor = currentActor;
        this.history = history;
    }

    /** Newest first in the requested language (fallback de → any). */
    @Transactional(readOnly = true)
    public List<LocalizedNote> list(String language) {
        List<ReleaseNote> rows = notes.findAllByOrderByReleasedAtDescReleaseVersionDesc();
        Map<UUID, Map<String, ReleaseNoteI18n>> byNote = byNote(rows);
        return rows.stream().map(note -> {
            ReleaseNoteI18n text = ContentService.pick(byNote.getOrDefault(note.getId(), Map.of()), language);
            return text == null ? null : new LocalizedNote(note, text.getTitle(), text.getBody(), text.getLanguage());
        }).filter(java.util.Objects::nonNull).toList();
    }

    /** All notes for the editor (German texts). @throws ApiException 403 unless ORGA */
    @Transactional(readOnly = true)
    public List<LocalizedNote> listForEdit() {
        requireOrga();
        return list(BASE_LANGUAGE);
    }

    /** @throws ApiException 403 unless ORGA, 400 VALIDATION_FAILED when the version exists */
    @Transactional
    public LocalizedNote create(NoteInput input) {
        requireOrga();
        String version = input.version().strip();
        if (notes.existsByReleaseVersion(version)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "version");
        }
        ReleaseNote note = new ReleaseNote();
        note.setReleaseVersion(version);
        note.setReleasedAt(input.releasedAt());
        ReleaseNote saved = notes.saveAndFlush(note);
        writeTexts(saved.getId(), input);
        history.record(history.event(ContentAction.RELEASE_NOTE_CREATED, saved.getId())
                .metadata("version", version).build());
        return localized(saved);
    }

    /** Null fields stay unchanged. @throws ApiException 403, 404 RELEASE_NOTE_NOT_FOUND, 400 VALIDATION_FAILED */
    @Transactional
    public LocalizedNote update(UUID noteId, NoteInput input) {
        requireOrga();
        ReleaseNote note = require(noteId);
        if (input.version() != null && !input.version().strip().equals(note.getReleaseVersion())) {
            if (notes.existsByReleaseVersion(input.version().strip())) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "version");
            }
            note.setReleaseVersion(input.version().strip());
        }
        if (input.releasedAt() != null) {
            note.setReleasedAt(input.releasedAt());
        }
        writeTexts(note.getId(), input);
        history.record(history.event(ContentAction.RELEASE_NOTE_UPDATED, note.getId())
                .metadata("version", note.getReleaseVersion()).build());
        return localized(note);
    }

    /** @throws ApiException 403, 404 RELEASE_NOTE_NOT_FOUND */
    @Transactional
    public void delete(UUID noteId) {
        requireOrga();
        ReleaseNote note = require(noteId);
        texts.deleteAll(texts.findByOwnerId(noteId));
        notes.delete(note);
        history.record(history.event(ContentAction.RELEASE_NOTE_DELETED, noteId)
                .metadata("version", note.getReleaseVersion()).build());
    }

    private void writeTexts(UUID noteId, NoteInput input) {
        Map<String, Text> wanted = new LinkedHashMap<>();
        if (input.title() != null || input.body() != null) {
            wanted.put(BASE_LANGUAGE, new Text(input.title(), input.body()));
        }
        if (input.translations() != null) {
            input.translations().forEach((language, text) -> {
                if (!BASE_LANGUAGE.equals(language) || !wanted.containsKey(BASE_LANGUAGE)) {
                    wanted.put(language, text);
                }
            });
        }
        Map<String, ReleaseNoteI18n> existing = texts.findByOwnerId(noteId).stream()
                .collect(Collectors.toMap(ReleaseNoteI18n::getLanguage, Function.identity()));
        wanted.forEach((language, text) -> {
            ReleaseNoteI18n row = existing.computeIfAbsent(language, l -> {
                ReleaseNoteI18n created = new ReleaseNoteI18n();
                created.setOwnerId(noteId);
                created.setLanguage(l);
                return created;
            });
            if (text.title() != null) {
                row.setTitle(text.title().strip());
            }
            if (text.body() != null) {
                row.setBody(text.body());
            }
            if (row.getTitle() == null || row.getBody() == null) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "translations");
            }
            texts.save(row);
        });
    }

    private LocalizedNote localized(ReleaseNote note) {
        ReleaseNoteI18n text = ContentService.pick(texts.findByOwnerId(note.getId()).stream()
                .collect(Collectors.toMap(ReleaseNoteI18n::getLanguage, Function.identity())), BASE_LANGUAGE);
        return new LocalizedNote(note, text == null ? "" : text.getTitle(), text == null ? "" : text.getBody(),
                text == null ? BASE_LANGUAGE : text.getLanguage());
    }

    private Map<UUID, Map<String, ReleaseNoteI18n>> byNote(List<ReleaseNote> rows) {
        return texts.findByOwnerIdIn(rows.stream().map(ReleaseNote::getId).toList()).stream()
                .collect(Collectors.groupingBy(ReleaseNoteI18n::getOwnerId,
                        Collectors.toMap(ReleaseNoteI18n::getLanguage, Function.identity())));
    }

    private ReleaseNote require(UUID noteId) {
        return notes.findById(noteId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RELEASE_NOTE_NOT_FOUND));
    }

    private void requireOrga() {
        if (!currentActor.isOrga()) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }
}
