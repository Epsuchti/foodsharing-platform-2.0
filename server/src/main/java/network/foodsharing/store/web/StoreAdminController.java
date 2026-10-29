package network.foodsharing.store.web;

import java.util.UUID;
import network.foodsharing.api.StoreAdminApi;
import network.foodsharing.api.model.CooperationStatus;
import network.foodsharing.api.model.StoreImportRow;
import network.foodsharing.api.model.StoreImportStatus;
import network.foodsharing.api.model.StoresCsvImport;
import network.foodsharing.api.model.StoresImportPreview;
import network.foodsharing.api.model.TeamSearchStatus;
import network.foodsharing.store.service.StoresCsvImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Store coordination of a region: CSV import of stores (D16.2). */
@RestController
public class StoreAdminController implements StoreAdminApi {

    private final StoresCsvImportService importService;

    public StoreAdminController(StoresCsvImportService importService) {
        this.importService = importService;
    }

    @Override
    public ResponseEntity<StoresImportPreview> importStoresCsv(UUID regionId, StoresCsvImport body) {
        StoresCsvImportService.Preview preview = importService.importCsv(regionId, body.getCsv(),
                Boolean.TRUE.equals(body.getDryRun()));
        StoresImportPreview model = new StoresImportPreview();
        model.setRows(preview.rows().stream().map(StoreAdminController::toRow).toList());
        model.setCreated(preview.created());
        model.setUpdated(preview.updated());
        model.setSkipped(preview.skipped());
        model.setErrors(preview.errors());
        model.setDryRun(preview.dryRun());
        return ResponseEntity.ok(model);
    }

    private static StoreImportRow toRow(StoresCsvImportService.Row row) {
        StoreImportRow model = new StoreImportRow();
        model.setLine(row.line());
        model.setName(row.name());
        model.setCategory(emptyToNull(row.category()));
        model.setChain(emptyToNull(row.chain()));
        model.setStreet(emptyToNull(row.street()));
        model.setPostcode(emptyToNull(row.postcode()));
        model.setCity(emptyToNull(row.city()));
        model.setLatitude(row.latitude());
        model.setLongitude(row.longitude());
        model.setCooperationStatus(row.cooperationStatus() == null ? null
                : CooperationStatus.valueOf(row.cooperationStatus().name()));
        model.setTeamStatus(row.teamStatus() == null ? null : TeamSearchStatus.valueOf(row.teamStatus().name()));
        model.setManagerFoodsaverNumbers(row.managerNumbers());
        model.setStoreId(row.storeId());
        model.setStatus(StoreImportStatus.valueOf(row.status().name()));
        model.setErrors(row.errors());
        model.setWarnings(row.warnings());
        return model;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
