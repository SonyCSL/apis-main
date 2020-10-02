package jp.co.sony.csl.dcoes.apis.common.util;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * {@link Number} まわりのどうってことないツール.
 * @author OES Project
 */
public class NumberUtil {
	private static final Logger log = LoggerFactory.getLogger(NumberUtil.class);

	private NumberUtil() { }

	/**
	 * {@link Float} の負号を反転するだけ.
	 * @param value 負号を反転する float オブジェクト.
	 *              {@code null} 可.
	 * @return 負号を反転した float オブジェクト.
	 *         {@code value} が {@code null} なら {@code null}.
	 */
	public static Float negativeValue(Float value) {
		return (value != null) ? - value : null;
	}

	/**
	 * 文字列を {@link Integer} に変換する.
	 * @param value 変換元文字列.
	 *              {@code null} 可.
	 * @return 変換した integer オブジェクト.
	 *         {@code value} が {@code null} なら {@code null}.
	 *         変換に失敗しても {@code null}.
	 */
	public static Integer toInteger(String value) {
		if (value != null && !value.isEmpty()) {
			try {
				return Integer.valueOf(value);
			} catch (Exception e) {
				if (log.isWarnEnabled()) log.warn(e);
			}
		}
		return null;
	}

}
