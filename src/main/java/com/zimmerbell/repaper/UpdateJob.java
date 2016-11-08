package com.zimmerbell.repaper;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.io.File;
import java.net.URL;
import java.util.HashMap;

import javax.imageio.ImageIO;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.UINT_PTR;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

public class UpdateJob implements Job {
	private static final Logger LOG = LoggerFactory.getLogger(UpdateJob.class);
	
	public static final String KEY_SOURCE = "source";
	

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		LOG.info("update");
		
		Repaper repaper = Repaper.getInstance();
		
		Source source = repaper.getSource();

		try {
			source.update();
			
			BufferedImage image = ImageIO.read(new URL(source.getImageUri()).openStream());

			File dir = new File(Repaper.HOME);
			dir.mkdirs();

			ImageIO.write(image, "jpg", new File(dir, "current-original.jpg"));

			image = blur(image);
			image = darken(image);
			File file = new File(dir, "current.jpg");
			ImageIO.write(image, "jpg", file);

			/**
			 * source: https://stackoverflow.com/a/4750765/3529762
			 */
			SPI.INSTANCE.SystemParametersInfo(new UINT_PTR(SPI.SPI_SETDESKWALLPAPER), new UINT_PTR(0), file.getCanonicalPath(), new UINT_PTR(SPI.SPIF_UPDATEINIFILE | SPI.SPIF_SENDWININICHANGE));

			repaper.triggerContentChanged();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
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
	public static ConvolveOp gauss(final int radius) {

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
	public static ConvolveOp getGaussianBlurFilter(int radius, boolean horizontal) {
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
