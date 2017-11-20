package com.zimmerbell.repaper;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.testng.annotations.Test;

import com.zimmerbell.repaper.sources.MomentumSource;

@Test
public class ReadLevelDBTest {
	private final static String LEVELDB_DIR = MomentumSource.CHROME_PROFILES + File.separator + "Default" + MomentumSource.CHROME_LEVELDB_PATH;

	public void testOpenLevelDB() throws IOException {
		File tempDir = Files.createTempDirectory("leveldb").toFile();
		try {
			FileUtils.copyDirectory(new File(LEVELDB_DIR), tempDir);
			for (File file : tempDir.listFiles()) {
				if (file.getName().endsWith(".ldb")) {
					file.renameTo(new File(file.getParentFile(), file.getName().replaceAll("ldb$", "sst")));
				}
			}

			try (DB db = factory.open(tempDir, new Options())) {

			}
		} finally {
			FileUtils.deleteDirectory(tempDir);
		}
	}
}
