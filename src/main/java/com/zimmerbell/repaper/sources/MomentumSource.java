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
	
	private final static String CHROME_PROFILES = System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\User Data";
	private final static String EXTENSION_ID = "laookkfknpbbblfpciffpaejjkokdgca";
	private final static String SQLITE_FILE_PATH = "\\Local Storage\\chrome-extension_" + EXTENSION_ID + "_0.localstorage";
	
	
	private String chromeProfileFolder;
	private JSONObject data;
	
	public MomentumSource() {
		long t = 0;
		for(File profileFolder : new File(CHROME_PROFILES).listFiles()) {
			File sqliteFile = new File(profileFolder, SQLITE_FILE_PATH);
			if(sqliteFile.exists() && sqliteFile.length() > 0 && (t < sqliteFile.lastModified())) {
				t = sqliteFile.lastModified();
				chromeProfileFolder = profileFolder.getAbsolutePath();
			}
		}
		LOG.info("using chrome profile: {}", chromeProfileFolder);
	}
	
	
	private String getChromeProfileFolder() {
		return chromeProfileFolder;
	}
	
	private String getSqliteFile() {
		String chromeProfileFolder = getChromeProfileFolder();
		return chromeProfileFolder == null ? null : chromeProfileFolder + SQLITE_FILE_PATH;
	}
	
	public boolean exists(){
		return getSqliteFile() != null;
	}

	@Override
	public void update() throws Exception {
		
		data = new JSONObject();
		
		Class.forName("org.sqlite.JDBC");
		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + getSqliteFile());
		
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
		if(filename != null && !filename.startsWith("http")){
			File[] files = new File(getChromeProfileFolder() + "\\Extensions\\" + EXTENSION_ID).listFiles();
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
