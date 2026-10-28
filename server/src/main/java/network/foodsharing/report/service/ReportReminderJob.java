package network.foodsharing.report.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily: due reminders of open reports become {@code REPORT_REMINDER} bells to the handlers (G1). */
@Component
public class ReportReminderJob implements RunnableJob {

    private static final Logger log = LoggerFactory.getLogger(ReportReminderJob.class);

    private final JobRunner jobRunner;
    private final ReportService reports;

    public ReportReminderJob(JobRunner jobRunner, ReportService reports) {
        this.jobRunner = jobRunner;
        this.reports = reports;
    }

    @Scheduled(cron = "${app.reports.reminder-cron:0 0 8 * * *}", zone = "Europe/Zurich")
    @SchedulerLock(name = "report-reminders", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "report-reminders";
    }

    @Override
    public void runNow() {
        int sent = reports.sendDueReminders();
        if (sent > 0) {
            log.info("Report reminders sent for {} reports", sent);
        }
    }
}
