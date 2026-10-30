package network.foodsharing.user.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.common.seed.InMemoryState;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * GDPR data export (A21): a zip with {@code profile.json}, {@code settings.json}, {@code history.json} and one
 * {@code <section>.json} per {@link DataExportContributor} (e.g. {@code sessions.json}, {@code notifications.json})
 * plus the user's own uploads under {@code uploads/}; produced as a {@link ProgressTracker} job (download through
 * {@code /api/jobs/{id}/result}). One export per user every {@value #COOLDOWN_MINUTES} minutes (429); the dev seed
 * reset forgets the cooldowns (A59).
 */
@Service
public class DataExportService implements InMemoryState {

    static final long COOLDOWN_MINUTES = 10;
    static final int MAX_HISTORY = 5000;
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final UserRepository users;
    private final List<DataExportContributor> contributors;
    private final HistoryQueryService historyQuery;
    private final HistoryService history;
    private final UploadService uploads;
    private final CurrentActorService actors;
    private final ProgressTracker tracker;
    private final Clock clock;
    private final Map<UUID, Instant> lastExport = new ConcurrentHashMap<>();

    public DataExportService(UserRepository users, List<DataExportContributor> contributors,
                             HistoryQueryService historyQuery, HistoryService history, UploadService uploads,
                             CurrentActorService actors, ProgressTracker tracker, Clock clock) {
        this.users = users;
        this.contributors = contributors;
        this.historyQuery = historyQuery;
        this.history = history;
        this.uploads = uploads;
        this.actors = actors;
        this.tracker = tracker;
        this.clock = clock;
    }

    /** @return the job id. @throws ApiException 429 TOO_MANY_REQUESTS within the cooldown */
    @Transactional
    public UUID exportMine() {
        UUID me = actors.requireUserId();
        Instant now = clock.instant();
        Instant last = lastExport.get(me);
        if (last != null && last.plus(Duration.ofMinutes(COOLDOWN_MINUTES)).isAfter(now)) {
            throw ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_REQUESTS,
                    Duration.between(now, last.plus(Duration.ofMinutes(COOLDOWN_MINUTES))));
        }
        User user = users.findById(me).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        Map<String, Object> files = files(user);
        List<Upload> own = uploads.ownedBy(me);
        UUID jobId = tracker.start(me, files.size() + own.size());
        byte[] zip = zip(jobId, files, own);
        tracker.complete(jobId, new ProgressTracker.JobResult(zip, "application/zip", "foodsharing-data.zip"));
        lastExport.put(me, now);
        history.record(history.event(UserAction.DATA_EXPORTED, me).metadata("sections", files.size())
                .metadata("uploads", own.size()).build());
        return jobId;
    }

    /** Dev seed reset: every user may export again at once. */
    @Override
    public void resetInMemoryState() {
        lastExport.clear();
    }

    Map<String, Object> files(User user) {
        Map<String, Object> files = new LinkedHashMap<>();
        files.put("profile", profile(user));
        files.put("settings", settings(user));
        files.put("history", historyQuery.myActivity(new HistoryFilter(null, null, null, user.getId(), null, null,
                null, 0, MAX_HISTORY)).getContent().stream().map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("occurredAt", entry.getOccurredAt());
                    row.put("action", entry.getAction());
                    row.put("subjectType", entry.getSubjectType());
                    row.put("subjectId", entry.getSubjectId());
                    row.put("field", entry.getField());
                    row.put("oldValue", entry.getOldValue());
                    row.put("newValue", entry.getNewValue());
                    row.put("reason", entry.getReason());
                    return row;
                }).toList());
        for (DataExportContributor contributor : contributors) {
            files.put(contributor.section(), contributor.export(user.getId()));
        }
        return files;
    }

    private byte[] zip(UUID jobId, Map<String, Object> files, List<Upload> own) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int done = 0;
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, Object> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey() + ".json"));
                zip.write(JSON.writeValueAsBytes(file.getValue()));
                zip.closeEntry();
                tracker.progress(jobId, ++done);
            }
            for (Upload upload : own) {
                try (InputStream in = uploads.open(upload)) {
                    zip.putNextEntry(new ZipEntry("uploads/" + upload.getId() + "-" + upload.getFileName()));
                    in.transferTo(zip);
                    zip.closeEntry();
                } catch (ApiException missing) {
                    // bytes gone; the row is listed in profile.json
                }
                tracker.progress(jobId, ++done);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static Map<String, Object> profile(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("email", user.getEmail());
        data.put("displayName", user.getDisplayName());
        data.put("firstName", user.getFirstName());
        data.put("lastName", user.getLastName());
        data.put("gender", user.getGender());
        data.put("dateOfBirth", user.getDateOfBirth());
        data.put("mobilePhone", user.getMobilePhone());
        data.put("landlinePhone", user.getLandlinePhone());
        data.put("homepage", user.getHomepage());
        data.put("street", user.getStreet());
        data.put("postcode", user.getPostcode());
        data.put("city", user.getCity());
        data.put("country", user.getCountry());
        data.put("latitude", user.getLatitude());
        data.put("longitude", user.getLongitude());
        data.put("homeRegionId", user.getHomeRegionId());
        data.put("aboutMePublic", user.getAboutMePublic());
        data.put("aboutMeInternal", user.getAboutMeInternal());
        data.put("role", user.getRole());
        data.put("verified", user.isVerified());
        data.put("foodsaverNumber", user.getFoodsaverNumber());
        data.put("registeredAt", user.getRegisteredAt());
        data.put("lastLoginAt", user.getLastLoginAt());
        data.put("avatarUrl", user.getAvatarUrl());
        data.put("sleepStatus", user.getSleepStatus());
        data.put("sleepFrom", user.getSleepFrom());
        data.put("sleepUntil", user.getSleepUntil());
        data.put("sleepMessage", user.getSleepMessage());
        return data;
    }

    private static Map<String, Object> settings(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("language", user.getPreferredLanguage());
        data.put("theme", user.getTheme());
        data.put("newsletterOptIn", user.isNewsletterOptIn());
        data.put("infoMailMessage", user.isInfoMailMessage());
        data.put("mentionNotifications", user.isMentionNotifications());
        data.put("pickupRemindersEnabled", user.isPickupRemindersEnabled());
        data.put("contactVisibility", user.getContactVisibility());
        data.put("activityFilters", user.getActivityFilters());
        data.put("telegramLinked", user.getTelegramChatId() != null);
        return data;
    }
}
