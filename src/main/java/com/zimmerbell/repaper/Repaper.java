package com.zimmerbell.repaper;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.UINT_PTR;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

public class Repaper {

	public final static String HOME = System.getProperty("user.home") + File.separator + ".repaper";
	public final static int GAUSS_RADIUS = 15;
	public final static float BRIGTHNESS  = 0.5f;
	
	
	public final static File CURRENT_FILE = new File(HOME, "current.jpg");
	public final static File CURRENT_FILE_ORIGINAL = new File(HOME, "current-original.jpg");
	
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
				.withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(5, 0).withMisfireHandlingInstructionFireAndProceed()) //
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
		
		popup.add(mi = new MenuItem("Show original"));
		mi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				showOriginal();
			}
		});
		
		if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)){
			popup.add(mi = new MenuItem("Details"));
			mi.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event) {
					try {
						Desktop.getDesktop().browse(new URI(source.getDetailsUri()));
					} catch (Exception e) {
						logError(e);
					}
				}
			});	
		}
		
		popup.addSeparator();

		popup.add(mi = new MenuItem("Exit"));
		mi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				exit();
			}
		});

		trayIcon = new TrayIcon(ImageIO.read(Repaper.class.getResourceAsStream("/icon_16.png")), null, popup);
		trayIcon.setImageAutoSize(true);
		
		trayIcon.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				showOriginal();
			}
		});
		
		SystemTray.getSystemTray().add(trayIcon);
	}
	
	public void update(){
		try {
			LOG.info("update");
			
			source.update();
			
			BufferedImage image = ImageIO.read(new URL(source.getImageUri()).openStream());
			
			CURRENT_FILE_ORIGINAL.getParentFile().mkdir();
			ImageIO.write(image, "jpg", CURRENT_FILE_ORIGINAL);

			image = blur(image);
			image = darken(image);
			
			CURRENT_FILE.getParentFile().mkdirs();
			ImageIO.write(image, "jpg", CURRENT_FILE);
			
			trayIcon.setToolTip("\"" + source.getTitle() + "\"\n by " + source.getBy());
			
			setBackgroundImage(false);

			
		} catch (IOException e) {
			trayIcon.setToolTip(null);
			logError(e);
		}
	}
	
	private void showOriginal(){
		try {
			JobKey jobKey = JobKey.jobKey("showJob");
			//scheduler.interrupt(jobKey);
			scheduler.scheduleJob(JobBuilder.newJob(ShowJob.class).withIdentity(jobKey).build(), TriggerBuilder.newTrigger().build());
		}catch(ObjectAlreadyExistsException e){
			 // do nothing
		} catch (SchedulerException e) {
			logError(e);
		}
	}
	
	public void setBackgroundImage(boolean original) throws IOException{
		LOG.info("show " + (original ? " original" : "") + " image");
		
		File file = original ? CURRENT_FILE_ORIGINAL : CURRENT_FILE; 
		
		/**
		 * source: https://stackoverflow.com/a/4750765/3529762
		 */
		SPI.INSTANCE.SystemParametersInfo(new UINT_PTR(SPI.SPI_SETDESKWALLPAPER), new UINT_PTR(0), file.getCanonicalPath(), new UINT_PTR(SPI.SPIF_UPDATEINIFILE | SPI.SPIF_SENDWININICHANGE));
		
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

	private BufferedImage blur(BufferedImage image) {

		// image = gauss(GAUSS_RADIUS).filter(image, null);

		image = getGaussianBlurFilter(Repaper.GAUSS_RADIUS, true).filter(image, null);
		image = getGaussianBlurFilter(Repaper.GAUSS_RADIUS, false).filter(image, null);

		// cropping black borders
		BufferedImage croppedImage = new BufferedImage(image.getWidth() - (2 * Repaper.GAUSS_RADIUS), image.getHeight() - (2 * Repaper.GAUSS_RADIUS), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = croppedImage.createGraphics();
		g.drawImage(image, //
				0, 0, croppedImage.getWidth(), croppedImage.getHeight(), //
				Repaper.GAUSS_RADIUS, Repaper.GAUSS_RADIUS, croppedImage.getWidth() + Repaper.GAUSS_RADIUS, croppedImage.getHeight() + Repaper.GAUSS_RADIUS, //
				null);
		image = croppedImage;

		return image;
	}

	/**
	 * https://en.wikipedia.org/wiki/Gaussian_blur#Mathematics
	 * 
	 * @param radius
	 * @return
	 */
	@SuppressWarnings("unused")
	private static ConvolveOp gauss(final int radius) {

		double sigma = ((2 * radius) + 1) / 6.0;

		float[][] matrix = new float[(2 * radius) + 1][(2 * radius) + 1];
		for (int x = 0; x <= radius; x++) {
			for (int y = 0; y <= radius; y++) {
				float d = (float) (1 / (2 * Math.PI * sigma * sigma) * Math.exp(-((x * x) + (y * y)) / (2 * sigma * sigma)));

				matrix[radius + x][radius + y] = d;
				matrix[radius + x][radius - y] = d;
				matrix[radius - x][radius + y] = d;
				matrix[radius - x][radius - y] = d;
			}
		}

		for (int x = 0; x < matrix.length; x++) {
			for (int y = 0; y < matrix[x].length; y++) {
				System.out.print((x - radius) + "," + (y - radius) + "=" + matrix[x][y]);
				System.out.print("\t");
			}
			System.out.println();
		}

		float[] data = new float[((2 * radius) + 1) * ((2 * radius) + 1)];
		int d = 0;
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[i].length; j++) {
				data[d++] = matrix[i][j];
			}
		}

		return new ConvolveOp(new Kernel((2 * radius) + 1, (2 * radius) + 1, data));
	}

	private BufferedImage darken(BufferedImage image) {
		return new RescaleOp(Repaper.BRIGTHNESS, 0, null).filter(image, null);
	}

	/**
	 * http://www.java2s.com/Code/Java/Advanced-Graphics/GaussianBlurDemo.htm
	 * 
	 * @param radius
	 * @param horizontal
	 * @return
	 */
	private static ConvolveOp getGaussianBlurFilter(int radius, boolean horizontal) {
		if (radius < 1) {
			throw new IllegalArgumentException("Radius must be >= 1");
		}

		int size = radius * 2 + 1;
		float[] data = new float[size];

		float sigma = radius / 3.0f;
		float twoSigmaSquare = 2.0f * sigma * sigma;
		float sigmaRoot = (float) Math.sqrt(twoSigmaSquare * Math.PI);
		float total = 0.0f;

		for (int i = -radius; i <= radius; i++) {
			float distance = i * i;
			int index = i + radius;
			data[index] = (float) Math.exp(-distance / twoSigmaSquare) / sigmaRoot;
			total += data[index];
		}

		for (int i = 0; i < data.length; i++) {
			data[i] /= total;
		}

		Kernel kernel = null;
		if (horizontal) {
			kernel = new Kernel(size, 1, data);
		} else {
			kernel = new Kernel(1, size, data);
		}

		// return new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
		return new ConvolveOp(kernel);
	}

	/**
	 * source: https://stackoverflow.com/a/4750765/3529762
	 */
	public interface SPI extends StdCallLibrary {
		long SPI_SETDESKWALLPAPER = 20;
		long SPIF_UPDATEINIFILE = 0x01;
		long SPIF_SENDWININICHANGE = 0x02;

		SPI INSTANCE = (SPI) Native.loadLibrary("user32", SPI.class, new HashMap<Object, Object>() {
			private static final long serialVersionUID = 1L;

			{
				put(OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
				put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
			}
		});

		boolean SystemParametersInfo(UINT_PTR uiAction, UINT_PTR uiParam, String pvParam, UINT_PTR fWinIni);
	}

}
