package com.zimmerbell.repaper;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Repaper {

	public final static String HOME = System.getProperty("user.home") + File.separator + ".repaper";
	public final static int GAUSS_RADIUS = 15;
	public final static float BRIGTHNESS  = 0.5f;
	
	private static final Logger LOG = LoggerFactory.getLogger(Repaper.class);
	
	private static Repaper repaperInstance;

	private TrayIcon trayIcon;
	private Source source;
	private Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
	private JobDetail updateJob = JobBuilder.newJob(UpdateJob.class).build();

	public static void main(String[] args) throws Exception {
		repaperInstance = new Repaper(new MuzeiSource());
	}
	
	public static Repaper getInstance(){
		return repaperInstance;
	}

	public Repaper(Source source) throws Exception {
		this.source = source;

		initTray();
		initScheduler();
	}

	private void initScheduler() {
		Trigger trigger = TriggerBuilder.newTrigger() //
				.startNow() //
				.withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(0, 0).withMisfireHandlingInstructionFireAndProceed()) //
				.build();
		
		
		try {
			scheduler.start();
			scheduler.scheduleJob(updateJob, trigger);
			
			// trigger now
			scheduler.triggerJob(updateJob.getKey());
		} catch (SchedulerException e) {
			logError(e);
		}
	}
	

	private void initTray() throws Exception {
		PopupMenu popup = new PopupMenu();
		MenuItem mi;

		popup.add(mi = new MenuItem("Update"));
		mi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				try {
					scheduler.triggerJob(updateJob.getKey());
				} catch (SchedulerException e) {
					logError(e);
				}
			}
		});

		popup.add(mi = new MenuItem("Exit"));
		mi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				exit();
			}
		});

		trayIcon = new TrayIcon(ImageIO.read(Repaper.class.getResourceAsStream("/icon_16.png")), null, popup);
		trayIcon.setImageAutoSize(true);
		SystemTray.getSystemTray().add(trayIcon);
	}
	
	public Source getSource() {
		return source;
	}
	
	public void triggerContentChanged(){
		try {
			trayIcon.setToolTip("\"" + source.getTitle() + "\"\n by " + source.getBy());
			
		} catch (IOException e) {
			trayIcon.setToolTip(null);
			logError(e);
		}
	}
	

	private void exit() {
		try {
			scheduler.shutdown();
		} catch (SchedulerException e) {
			logError(e);
		}
		System.exit(0);
	}

	public static void logError(Throwable e){
		LOG.error(e.getMessage(), e);
		JOptionPane.showMessageDialog(null, e.getMessage());
	}


}
