package com.zimmerbell.repaper.sources;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;
import com.zimmerbell.repaper.Source;

public class MomentumSource implements Source {

	private static final Logger LOG = LoggerFactory.getLogger(MomentumSource.class);

	private final static String CHROME_PROFILES = System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\User Data";
	private final static String EXTENSION_ID = "laookkfknpbbblfpciffpaejjkokdgca";
	private final static String SQLITE_FILE_PATH = "\\Local Storage\\chrome-extension_" + EXTENSION_ID + "_0.localstorage";
	private final static String LEVELDB_PATH = "\\Local Storage\\leveldb";

	private String chromeProfileFolder;
	private JSONObject data;

	public MomentumSource() {
		long t = 0;
		for (File profileFolder : new File(CHROME_PROFILES).listFiles()) {
			File extensionDir = new File(profileFolder, "Extensions\\" + EXTENSION_ID);
			if (extensionDir.exists() && extensionDir.length() > 0 && (t < extensionDir.lastModified())) {
				t = extensionDir.lastModified();
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

	private File getLevelDBFile() {
		String chromeProfileFolder = getChromeProfileFolder();
		return chromeProfileFolder == null ? null : new File(chromeProfileFolder + LEVELDB_PATH);
	}

	public boolean exists() {
		return getLevelDBFile() != null;
	}

	@Override
	public void update() throws Exception {
		updateFromLevelDB();
	}

	public void updateFromLevelDB() throws Exception {
		data = new JSONObject();

		File tempDir = Files.createTempDir();
//		File tempDir = new File("C:\\Users\\zimmermann\\AppData\\Local\\Temp\\1507708131562-0");
		System.out.println(tempDir);
		try {
			FileUtils.copyDirectory(getLevelDBFile(), tempDir);
			for (File file : tempDir.listFiles()) {
				if (file.getName().endsWith(".ldb")) {
					file.renameTo(new File(file.getParentFile(), file.getName().replaceAll("ldb$", "sst")));
				}
			}

			try (DB db = Iq80DBFactory.factory.open(tempDir, new Options()); DBIterator iterator = db.iterator()) {
				final String KEY_PREFIX = "_chrome-extension://" + EXTENSION_ID;
				for (iterator.seek(Iq80DBFactory.bytes(KEY_PREFIX)); iterator.hasNext(); iterator.next()) {
					String key = Iq80DBFactory.asString(iterator.peekNext().getKey());
					if (!key.startsWith(KEY_PREFIX)) {
						break;
					}
					key = key.substring(key.indexOf(1) + 1);

					if (key.equals(getMomentumKey())) {
						String value = Iq80DBFactory.asString(iterator.peekNext().getValue());
						value = value.substring(1);

						LOG.info(key);
						data = new JSONObject(value);
						LOG.info(data.toString());
					}

				}
			}

		} finally {
//			FileUtils.deleteDirectory(tempDir);
		}
	}

	public void updateFromSqlite() throws Exception {
		data = new JSONObject();

		Class.forName("org.sqlite.JDBC");

		String sqliteFile = getSqliteFile();
		LOG.info("sqliteFile: " + sqliteFile);
		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);

		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery("SELECT * FROM ItemTable ORDER BY Key DESC LIMIT 1")) {
				while (rs.next()) {
					LOG.info(rs.getString("Key") + ": " + rs.getString("Value"));
				}
			}

			try (ResultSet rs = stmt.executeQuery("SELECT * FROM ItemTable WHERE Key='" + getMomentumKey() + "' ORDER BY Key DESC LIMIT 1")) {
				if (!rs.next()) {
					LOG.info("nothing found");
					return;
				}
				String key = rs.getString("Key");

				LOG.info(key);
				data = new JSONObject(rs.getString("Value"));
				LOG.info(data.toString());
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
