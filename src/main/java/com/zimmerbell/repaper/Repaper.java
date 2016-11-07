package com.zimmerbell.repaper;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import javax.imageio.ImageIO;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.UINT_PTR;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

public class Repaper {

	private final static String HOME = System.getProperty("user.home") + File.separator + ".repaper";
	private final static int GAUSS_RADIUS = 15;
	private final static float BRIGTHNESS = 0.5f;
	private Source source;

	public static void main(String[] args) throws MalformedURLException, IOException {
		Repaper repaper = new Repaper(new MuzeiSource());
		repaper.update();
	}

	public Repaper(Source source) {
		this.source = source;
	}
	

	public void update() throws IOException {
		BufferedImage image = ImageIO.read(new URL(source.getImageUri()).openStream());
		
		image = blur(image);
		image = darken(image);

		File file = new File(HOME, "current.jpg");
		file.getParentFile().mkdirs();

		ImageIO.write(image, "jpg", file);

		/**
		 * source: https://stackoverflow.com/a/4750765/3529762
		 */
		SPI.INSTANCE.SystemParametersInfo(new UINT_PTR(SPI.SPI_SETDESKWALLPAPER), new UINT_PTR(0), file.getCanonicalPath(), new UINT_PTR(SPI.SPIF_UPDATEINIFILE | SPI.SPIF_SENDWININICHANGE));
	}

	private BufferedImage blur(BufferedImage image) {
		// float[] matrix = new float[400];
		// for (int i = 0; i < 400; i++)
		// matrix[i] = 1.0f / 400.0f;
		// image = new ConvolveOp(new Kernel(20, 20, matrix)).filter(image,
		// null);

		image = getGaussianBlurFilter(GAUSS_RADIUS, true).filter(image, null);
		image = getGaussianBlurFilter(GAUSS_RADIUS, false).filter(image, null);

		// cropping black borders
		BufferedImage croppedImage = new BufferedImage(image.getWidth() - (2 * GAUSS_RADIUS), image.getHeight() - (2 * GAUSS_RADIUS), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = croppedImage.createGraphics();
		g.drawImage(image, //
				0, 0, croppedImage.getWidth(), croppedImage.getHeight(), //
				GAUSS_RADIUS, GAUSS_RADIUS, croppedImage.getWidth() + GAUSS_RADIUS, croppedImage.getHeight() + GAUSS_RADIUS, //
				null);
		image = croppedImage;

		return image;
	}
	
	
	private BufferedImage darken(BufferedImage image){
		return new RescaleOp(BRIGTHNESS, 0, null).filter(image, null);
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
			{
				put(OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
				put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
			}
		});

		boolean SystemParametersInfo(UINT_PTR uiAction, UINT_PTR uiParam, String pvParam, UINT_PTR fWinIni);
	}
}
