package com.zimmerbell.repaper;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

public class MuzeiSource implements Source {
	private final static String URL = "https://muzeiapi.appspot.com/featured";

	private JSONObject data;

	@Override
	public void update() throws IOException {
		data = new JSONObject(IOUtils.toString(new URL(URL).openStream(), "UTF8"));
	}

	private String getData(String key) throws IOException {
		if (data != null) {
			update();
		}
		return data.getString(key);
	}

	@Override
	public String getImageUri() throws IOException {
		return getData("imageUri");
	}
	
	@Override
	public String getTitle() throws IOException{
		return getData("title");
	}
	
	@Override
	public String getBy() throws IOException{
		return getData("byline");
	}

}
