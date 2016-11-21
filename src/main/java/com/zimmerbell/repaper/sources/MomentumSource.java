package com.zimmerbell.repaper.sources;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zimmerbell.repaper.Source;

public class MomentumSource implements Source {
	
	private static final Logger LOG = LoggerFactory.getLogger(MomentumSource.class);
	
	private final static String CHROME_FOLDER = System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\User Data\\Default";
	private final static String EXTENSION_ID = "laookkfknpbbblfpciffpaejjkokdgca";
	private final static String SQLITE_FILE = CHROME_FOLDER + "\\Local Storage\\chrome-extension_" + EXTENSION_ID + "_0.localstorage";
	
	
	private JSONObject data;
	
	public static boolean exists(){
		return new File(SQLITE_FILE).exists();
	}

	@Override
	public void update() throws Exception {
		
		data = new JSONObject();
		
		Class.forName("org.sqlite.JDBC");
		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + SQLITE_FILE);
		
		try(Statement stmt = connection.createStatement()){
			
			String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
			
			try(ResultSet rs = stmt.executeQuery("SELECT * FROM ItemTable WHERE Key='momentum-background-" + today + "' ORDER BY Key DESC LIMIT 1")){
				if(!rs.next()){
					return;
				}
				String key = rs.getString("Key");
				LOG.info(key);
				
				data = new JSONObject(rs.getString("Value"));
				LOG.info(data.toString());
			}
			
		}
	}
	
	private String getData(String key) throws Exception {
		if (data == null) {
			update();
		}
		try{
			return data.getString(key);
		}catch(JSONException e){
			return null;
		}
	}

	@Override
	public String getImageUri() throws Exception {
		String filename = getData("filename");
		if(!filename.startsWith("http")){
			File[] files = new File(CHROME_FOLDER + File.separator + "Extensions" + File.separator + EXTENSION_ID).listFiles();
			Arrays.sort(files);
			String extensionHome = files[files.length - 1].getAbsolutePath();
			
			filename = extensionHome + File.separator + filename;
		}
		return filename;
	}

	@Override
	public String getTitle() throws Exception {
		return getData("title");
	}

	@Override
	public String getBy() throws Exception {
		return getData("source");
	}

	@Override
	public String getDetailsUri() throws Exception {
		return getData("sourceUrl");
	}

}
