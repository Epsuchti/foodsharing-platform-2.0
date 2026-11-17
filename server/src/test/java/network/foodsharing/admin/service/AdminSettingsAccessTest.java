package network.foodsharing.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.admin.repository.ConfigurationSettingRepository;
import network.foodsharing.admin.repository.FeatureToggleSettingRepository;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import org.junit.jupiter.api.Test;

/**
 * C10/C11/C14: feature toggles, configuration and upload administration are ORGA only; uploads are paged within
 * bounds and an admin delete is recorded (was AdminSettingsIT).
 */
class AdminSettingsAccessTest {

    CurrentActorService actors = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    ConfigurationSettingRepository settings = mock(ConfigurationSettingRepository.class);
    FeatureToggleSettingRepository toggleRows = mock(FeatureToggleSettingRepository.class);
    UploadService uploads = mock(UploadService.class);
    ConfigurationService configuration = new ConfigurationService(settings, actors, history);
    FeatureToggleService toggles = new FeatureToggleService(toggleRows, actors, history, () -> 1L);
    UploadAdminService uploadAdmin = new UploadAdminService(uploads, actors, history);

    @Test
    void nonOrgaIsRefusedBeforeAnythingIsReadOrWritten() {
        when(actors.requireRole(Role.ORGA)).thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        List<Runnable> calls = List.of(configuration::entries,
                () -> configuration.update("support.email", "x@local.test"), toggles::list,
                () -> toggles.set("BASKETS", false), () -> uploadAdmin.list(null, null, null, null),
                uploadAdmin::summary, () -> uploadAdmin.delete(UUID.randomUUID()));
        for (Runnable call : calls) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        }
        verifyNoInteractions(uploads);
        verify(settings, never()).save(any());
        verify(toggleRows, never()).save(any());
        verify(history, never()).record(any());
    }

    @Test
    void uploadPagesAreBoundedAndAdminDeletesAreRecorded() {
        uploadAdmin.list(true, "USER_PHOTO", -3, 1000);
        verify(uploads).adminPage(true, "USER_PHOTO", 0, 200);
        uploadAdmin.list(null, null, null, 0);
        verify(uploads).adminPage(null, null, 0, 1);
        uploadAdmin.list(null, null, 2, null);
        verify(uploads).adminPage(null, null, 2, 50);

        Upload upload = new Upload();
        UUID id = UUID.randomUUID();
        upload.setFileName("avatar.png");
        when(uploads.find(id)).thenReturn(upload);
        uploadAdmin.delete(id);
        verify(uploads).delete(upload);
        verify(history).event(AdminAction.UPLOAD_DELETED_BY_ADMIN, id);

        UUID gone = UUID.randomUUID();
        when(uploads.find(gone)).thenThrow(ApiException.notFound(ApiErrorCode.UPLOAD_NOT_FOUND));
        assertThatThrownBy(() -> uploadAdmin.delete(gone)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.UPLOAD_NOT_FOUND));
    }

    @Test
    void aConfigurationSaveAnswersWithTheAuditValuesOfThatSave() {
        java.time.Instant saved = java.time.Instant.parse("2026-11-02T09:00:00Z");
        UUID orga = UUID.randomUUID();
        when(settings.saveAndFlush(any())).thenAnswer(invocation -> {
            network.foodsharing.admin.domain.ConfigurationSetting row = invocation.getArgument(0);
            row.setUpdatedAt(saved);
            row.setUpdatedBy(orga);
            return row;
        });
        ConfigurationService.Entry entry = configuration.update("support.email", " help@local.test ");
        assertThat(entry.value()).isEqualTo("help@local.test");
        assertThat(entry.updatedAt()).isEqualTo(saved);
        assertThat(entry.updatedBy()).isEqualTo(orga);
        assertThatThrownBy(() -> configuration.update("support.email", "nope")).isInstanceOfSatisfying(
                ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.CONFIGURATION_VALUE_INVALID));
    }

    @Test
    void unknownConfigurationKeysAre404() {
        assertThatThrownBy(() -> configuration.update("no.such.key", "1")).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.CONFIGURATION_KEY_NOT_FOUND));
        verify(settings, never()).save(any());
    }
}
