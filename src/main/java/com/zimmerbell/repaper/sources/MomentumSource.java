package com.zimmerbell.repaper.sources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.iq80.leveldb.*;
import static org.fusesource.leveldbjni.JniDBFactory.*;

import com.zimmerbell.repaper.Source;

public class MomentumSource implements Source {

	private static final Logger LOG = LoggerFactory.getLogger(MomentumSource.class);

	public final static String CHROME_PROFILES = System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\User Data";
	public final static String FIREFOX_PROFILES = System.getenv("LOCALAPPDATA") + "\\..\\Roaming\\Mozilla\\Firefox\\Profiles";
	public final static String CHROME_LEVELDB_PATH = "\\Local Storage\\leveldb";
	private final static String CHROME_EXTENSION_ID = "laookkfknpbbblfpciffpaejjkokdgca";
	private final static String FIREFOX_SQLITE_FILE_PATH = "\\webappsstore.sqlite";

	private String chromeProfileFolder;
	private String firefoxProfileFolder;
	private JSONObject data;

	public MomentumSource() {
		long t;
		
		t = 0;
		for (File profileFolder : new File(CHROME_PROFILES).listFiles()) {
			File extensionDir = new File(profileFolder, "Extensions\\" + CHROME_EXTENSION_ID);
			if (extensionDir.exists() && extensionDir.length() > 0 && (extensionDir.lastModified() > t)) {
				t = extensionDir.lastModified();
				chromeProfileFolder = profileFolder.getAbsolutePath();
			}
		}
		LOG.info("using chrome profile: {}", chromeProfileFolder);
		
		t = 0;
		for (File profileFolder : new File(FIREFOX_PROFILES).listFiles()) {
			File sqliteFile = new File(profileFolder, FIREFOX_SQLITE_FILE_PATH);
			if (sqliteFile.exists() && sqliteFile.lastModified() > t) {
				t = sqliteFile.lastModified();
				firefoxProfileFolder = profileFolder.getAbsolutePath();
			}
		}
		LOG.info("using firefox profile: {}", firefoxProfileFolder);
	}

	private String getChromeProfileFolder() {
		return chromeProfileFolder;
	}
	
	private String getFirefoxProfileFolder() {
		return firefoxProfileFolder;
	}

	private String getFirefoxSqliteFile() {
		String firefoxProfileFolder = getFirefoxProfileFolder();
		return firefoxProfileFolder == null ? null : firefoxProfileFolder + FIREFOX_SQLITE_FILE_PATH;
	}

	private File getChromeLevelDBFile() {
		String chromeProfileFolder = getChromeProfileFolder();
		return chromeProfileFolder == null ? null : new File(chromeProfileFolder + CHROME_LEVELDB_PATH);
	}

	public boolean exists() {
		return getChromeLevelDBFile() != null;
	}

	@Override
	public void update() throws Exception {
		if(updateFromChromeLevelDB()) {
			return;
		}
		if(updateFromFirefoxSqlite()) {
			return;
		}
	}

	public boolean updateFromChromeLevelDB() throws Exception {
		data = new JSONObject();

		File tempDir = Files.createTempDirectory("leveldb").toFile();
		System.out.println(tempDir);
		try {
			FileUtils.copyDirectory(getChromeLevelDBFile(), tempDir);
			for (File file : tempDir.listFiles()) {
				if (file.getName().endsWith(".ldb")) {
					file.renameTo(new File(file.getParentFile(), file.getName().replaceAll("ldb$", "sst")));
				}
			}

			try (DB db = factory.open(tempDir, new Options()); DBIterator iterator = db.iterator()) {
				final String KEY_PREFIX = "_chrome-extension://" + CHROME_EXTENSION_ID;
				for (iterator.seek(bytes(KEY_PREFIX)); iterator.hasNext(); iterator.next()) {
					String key = asString(iterator.peekNext().getKey());
					if (!key.startsWith(KEY_PREFIX)) {
						break;
					}
					key = key.substring(key.indexOf(1) + 1);

					if (key.equals(getMomentumKey())) {
						String value = asString(iterator.peekNext().getValue());
						value = value.substring(1);

						LOG.info(key);
						data = new JSONObject(value);
						LOG.info(data.toString());
						return true;
					}
				}
			}

		} finally {
			try {
				FileUtils.deleteDirectory(tempDir);
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}
		}
		
		return false;
	}

	public boolean updateFromFirefoxSqlite() throws Exception {
		data = new JSONObject();

		Class.forName("org.sqlite.JDBC");

		String sqliteFile = getFirefoxSqliteFile();
		LOG.info("sqliteFile: " + sqliteFile);
		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);

		try (Statement stmt = connection.createStatement()) {

			try (ResultSet rs = stmt.executeQuery("SELECT * FROM webappsstore2 WHERE Key='" + getMomentumKey() + "' ORDER BY Key DESC LIMIT 1")) {
				if (!rs.next()) {
					LOG.info("nothing found");
					return false;
				}
				String key = rs.getString("Key");

				LOG.info(key);
				data = new JSONObject(rs.getString("Value"));
				LOG.info(data.toString());
				return true;
			}

		}
	}

	private String getMomentumKey() {
		String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		;
		return "momentum-background-" + today;
	}

	private String getData(String key) throws Exception {
		if (data == null) {
			update();
		}
		try {
			return data.getString(key);
		} catch (JSONException e) {
			return null;
		}
	}

	@Override
	public String getImageUri() throws Exception {
		String filename = getData("filename");
		if (filename != null && !filename.startsWith("http")) {
			File[] files = new File(getChromeProfileFolder() + "\\Extensions\\" + CHROME_EXTENSION_ID).listFiles();
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
