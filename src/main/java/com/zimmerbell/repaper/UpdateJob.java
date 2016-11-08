package com.zimmerbell.repaper;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class UpdateJob implements Job {

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		Repaper.getInstance().update();
	}

}
