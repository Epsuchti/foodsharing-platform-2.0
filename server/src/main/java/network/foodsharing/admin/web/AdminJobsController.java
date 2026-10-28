package network.foodsharing.admin.web;

import java.util.List;
import network.foodsharing.api.AdminJobsApi;
import network.foodsharing.api.model.JobInfo;
import network.foodsharing.api.model.JobRun;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scheduled jobs (ORGA, M4 B3).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class AdminJobsController implements AdminJobsApi {

    @Override
    public ResponseEntity<List<JobInfo>> listAdminJobs() {
        throw ApiException.notImplemented("listAdminJobs");
    }

    @Override
    public ResponseEntity<JobRun> runAdminJob(String name) {
        throw ApiException.notImplemented("runAdminJob");
    }
}
