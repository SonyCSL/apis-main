package jp.co.sony.csl.dcoes.apis.common.util;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DateTimeUtilTest {

	public DateTimeUtilTest() {
		super();
	}

	@Test public void toLocalDateTime(TestContext context) {
		context.assertNotNull(DateTimeUtil.toLocalDateTime("1967/02/20-12:34:56"));
	}
	@Test public void toSystemDefaultZonedDateTime(TestContext context) {
		context.assertNotNull(DateTimeUtil.toSystemDefaultZonedDateTime("1967/02/20-12:34:56"));
	}
	@Test public void toString_LocalDateTime(TestContext context) {
		LocalDateTime ldt = DateTimeUtil.toLocalDateTime("1967/02/20-12:34:56");
		context.assertEquals(DateTimeUtil.toString(ldt), "1967/02/20-12:34:56");
	}
	@Test public void toString_LocalTime(TestContext context) {
		LocalDateTime ldt = DateTimeUtil.toLocalDateTime("1967/02/20-12:34:56");
		context.assertEquals(DateTimeUtil.toString(ldt.toLocalTime()), "12:34:56");
	}
	@Test public void toISO8601OffsetString(TestContext context) {
		ZonedDateTime zdt = DateTimeUtil.toSystemDefaultZonedDateTime("1967/02/20-12:34:56");
		String result = DateTimeUtil.toISO8601OffsetString(zdt);
		context.assertTrue(result.startsWith("1967-02-20T12:34:56"));
		context.assertEquals(result.length(), "1967-02-20T12:34:56".length() + "+09:00".length());
	}
	@Test public void illegalFormatTest(TestContext context) {
		context.assertNull(DateTimeUtil.toLocalDateTime(Deal.NULL_DATE_TIME_VALUE));
	}

}
