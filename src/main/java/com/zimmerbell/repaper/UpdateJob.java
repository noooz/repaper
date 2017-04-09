package com.zimmerbell.repaper;

import java.util.Date;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateJob implements Job {
	private final static Logger LOG = LoggerFactory.getLogger(UpdateJob.class);

	private final static String KEY_COUNT = "count";
	private final static int MAX_RETRIES = 10;
	private final static long RETRY_WAIT_SECONDS = 60 * 5;

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		JobDataMap data = context.getJobDetail().getJobDataMap();

		try {
			Repaper.getInstance().update();
		} catch (Exception e) {
			final int retries = data.containsKey(KEY_COUNT) ? data.getIntValue(KEY_COUNT) : 0;
			if (retries < MAX_RETRIES) {
				data.put(KEY_COUNT, retries + 1);

				LOG.warn("Retry job " + data);

				final JobDetail job = context.getJobDetail().getJobBuilder().usingJobData(data).build();

				final Trigger trigger = TriggerBuilder.newTrigger().forJob(job)//
						.startAt(new Date(context.getFireTime().getTime() + RETRY_WAIT_SECONDS * 1000)).build();
				try {
					context.getScheduler().scheduleJob(job, trigger);
				} catch (SchedulerException e1) {
					LOG.error("Error creating job");
				    throw new JobExecutionException(e1);
				}
			}else{
				throw new JobExecutionException(e);
			}
		}
	}

}
