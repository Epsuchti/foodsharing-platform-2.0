package network.foodsharing.history.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.HistoryApi;
import network.foodsharing.api.model.HistoryActionInfo;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.HistorySubjectType;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryActionRegistry;
import network.foodsharing.history.service.HistoryCsvExporter;
import network.foodsharing.history.service.HistoryQueryService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HistoryController implements HistoryApi {

    private final HistoryQueryService queryService;
    private final HistoryActionRegistry actionRegistry;
    private final HistoryCsvExporter csvExporter;
    private final HistoryMapper mapper;

    public HistoryController(HistoryQueryService queryService, HistoryActionRegistry actionRegistry,
                             HistoryCsvExporter csvExporter, HistoryMapper mapper) {
        this.queryService = queryService;
        this.actionRegistry = actionRegistry;
        this.csvExporter = csvExporter;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<HistoryEntryPage> listHistory(HistorySubjectType subjectType, UUID subjectId, UUID actorId,
                                                        List<String> actions, OffsetDateTime from, OffsetDateTime to,
                                                        Integer page, Integer size) {
        HistoryFilter filter = filter(subjectType, subjectId, actorId, actions, from, to, page, size);
        return ResponseEntity.ok(mapper.toPage(queryService.list(filter)));
    }

    @Override
    public ResponseEntity<HistoryEntryPage> listMyActivity(List<String> actions, OffsetDateTime from, OffsetDateTime to,
                                                           Integer page, Integer size) {
        HistoryFilter filter = filter(null, null, null, actions, from, to, page, size);
        return ResponseEntity.ok(mapper.toPage(queryService.myActivity(filter)));
    }

    @Override
    public ResponseEntity<List<HistoryActionInfo>> listHistoryActions(HistorySubjectType subjectType) {
        var actions = subjectType == null ? actionRegistry.all()
                : actionRegistry.forSubjectType(HistoryMapper.toSubjectType(subjectType));
        return ResponseEntity.ok(mapper.toActionInfos(actions));
    }

    @Override
    public ResponseEntity<String> exportHistoryCsv(HistorySubjectType subjectType, UUID subjectId, UUID actorId,
                                                   List<String> actions, OffsetDateTime from, OffsetDateTime to) {
        List<HistoryEntry> rows = queryService.export(filter(subjectType, subjectId, actorId, actions, from, to, 0, 1));
        String csv = csvExporter.toCsv(rows, mapper.actors(rows));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("history.csv").build().toString())
                .body(csv);
    }

    private static HistoryFilter filter(HistorySubjectType subjectType, UUID subjectId, UUID actorId,
                                        List<String> actions, OffsetDateTime from, OffsetDateTime to,
                                        Integer page, Integer size) {
        return new HistoryFilter(HistoryMapper.toSubjectType(subjectType), subjectId, actorId, null, actions,
                TimeMapper.toInstant(from), TimeMapper.toInstant(to),
                page == null ? 0 : page, size == null ? 20 : size);
    }
}
