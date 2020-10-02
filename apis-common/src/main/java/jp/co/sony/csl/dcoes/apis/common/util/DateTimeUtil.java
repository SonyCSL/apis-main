package jp.co.sony.csl.dcoes.apis.common.util;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Java の日時まわりの便利ツール.
 * @author OES Project
 */
public class DateTimeUtil {
	private static final Logger log = LoggerFactory.getLogger(DateTimeUtil.class);

	private DateTimeUtil() { }

	/**
	 * APIS プログラムの標準フォーマット.
	 * uuuu/MM/dd-HH:mm:ss
	 */
	private static final DateTimeFormatter LocalDateTimeFormatter_ = DateTimeFormatter.ofPattern("uuuu/MM/dd-HH:mm:ss");

	/**
	 * APIS プログラムの標準フォーマット文字列をパースし {@link LocalDateTime} を取得する.
	 * @param value APIS プログラムの標準フォーマットで表現された日時文字列
	 * @return パースした localdatetime オブジェクト.
	 *         {@code value} が {@code null} なら {@code null}.
	 *         パースに失敗したら null.
	 */
	public static LocalDateTime toLocalDateTime(String value) {
		if (value != null) {
			int pos = value.indexOf('.');
			if (-1 != pos) value = value.substring(0, pos);
			try {
				return LocalDateTime.parse(value, LocalDateTimeFormatter_);
			} catch (Exception e) {
				if (log.isWarnEnabled()) log.warn(e);
			}
		}
		return null;
	}
	/**
	 * APIS プログラムの標準フォーマット文字列をパースし {@link ZonedDateTime} を取得する.
	 * タイムゾーンは {@link ZoneId#systemDefault() システムのデフォルトタイムゾーン} をセットする.
	 * @param value APIS プログラムの標準フォーマットで表現された日時文字列
	 * @return パースした localdatetime オブジェクト.
	 *         {@code value} が {@code null} なら {@code null}.
	 *         パースに失敗したら null.
	 */
	public static ZonedDateTime toSystemDefaultZonedDateTime(String value) {
		LocalDateTime ldt = toLocalDateTime(value);
		if (ldt != null) {
			return ldt.atZone(ZoneId.systemDefault());
		}
		return null;
	}

	/**
	 * {@link LocalDateTime} を APIS プログラムの標準フォーマットでフォーマットした文字列を取得する.
	 * 例 : 2020/01/20-18:48:30
	 * @param value localdatetime オブジェクト
	 * @return APIS プログラムの標準フォーマットでフォーマットした文字列.
	 *         {@code value} が {@code null} なら {@code null}.
	 */
	public static String toString(LocalDateTime value) {
		return (value != null) ? LocalDateTimeFormatter_.format(value) : null;
	}
	/**
	 * {@link LocalTime} を {@link DateTimeFormatter#ISO_LOCAL_TIME} でフォーマットした文字列を取得する.
	 * 例 : 18:48:30
	 * @param value localtime オブジェクト
	 * @return {@link DateTimeFormatter#ISO_LOCAL_TIME} でフォーマットした文字列.
	 *         {@code value} が {@code null} なら {@code null}.
	 */
	public static String toString(LocalTime value) {
		return (value != null) ? DateTimeFormatter.ISO_LOCAL_TIME.format(value) : null;
	}
	/**
	 * {@link ZonedDateTime} を {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} でフォーマットした文字列を取得する.
	 * 例 : 2020-01-20T18:48:30+09:00
	 * @param value zoneddatetime オブジェクト
	 * @return {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} でフォーマットした文字列.
	 *         {@code value} が {@code null} なら {@code null}.
	 */
	public static String toISO8601OffsetString(ZonedDateTime value) {
		return (value != null) ? DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value) : null;
	}

}
