package jp.co.sony.csl.dcoes.apis.common.util.vertx;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.VertxLoggerFormatter;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ApisLoggerFormatterTest {

	public ApisLoggerFormatterTest() {
		super();
	}

	private static final VertxLoggerFormatter BASE_ = new VertxLoggerFormatter();

	@Test public void nullProgramId(TestContext context) {
		VertxConfig.config.setJsonObject(null);
		LogRecord record = new LogRecord(Level.INFO, "test message");
		record.setLoggerName("testLogger");
		String result = new ApisLoggerFormatter().format(record);
		context.assertEquals("[[[]]] " + BASE_.format(record), result);
	}

	@Test public void emptyProgramId(TestContext context) {
		VertxConfig.config.setJsonObject(new JsonObject("{\"programId\":\"\"}"));
		LogRecord record = new LogRecord(Level.INFO, "test message");
		record.setLoggerName("testLogger");
		String result = new ApisLoggerFormatter().format(record);
		context.assertEquals("[[[]]] " + BASE_.format(record), result);
	}

	@Test public void normalProgramId(TestContext context) {
		VertxConfig.config.setJsonObject(new JsonObject("{\"programId\":\"apis-log\"}"));
		LogRecord record = new LogRecord(Level.INFO, "test message");
		record.setLoggerName("testLogger");
		String result = new ApisLoggerFormatter().format(record);
		context.assertEquals("[[[apis-log]]] " + BASE_.format(record), result);
	}

}
