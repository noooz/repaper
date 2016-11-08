package com.zimmerbell.repaper;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class ShowJob implements Job {
	private static final Logger LOG = LoggerFactory.getLogger(ShowJob.class);
	

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		try {
			Repaper.getInstance().setBackgroundImage(true);
			Thread.sleep(10 * 1000);
			Repaper.getInstance().setBackgroundImage(false);
		} catch (Throwable e) {
			LOG.error(e.getMessage(), e);
		}
	}


}
