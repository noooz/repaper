package com.zimmerbell.repaper;

import java.io.IOException;
import java.net.URL;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

public class MuzeiSource implements Source {
	private static final long serialVersionUID = 1L;
	
	private final static String URL = "https://muzeiapi.appspot.com/featured";

	private JSONObject json() throws IOException {
		return new JSONObject(IOUtils.toString(new URL(URL).openStream(), "UTF8"));
	}

	public String getImageUri() throws IOException {
		return json().getString("imageUri");
	}
}
