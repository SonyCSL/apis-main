package jp.co.sony.csl.dcoes.apis.common;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.util.StackTraceUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;

/**
 * APIS プログラムのエラーを表す.
 * @author OES Project
 */
// 名前変えたい。失敗した。orz
// java.lang.Error ともカブってるし、Level.ERROR とも紛らわしいし (T_T)
// Failure とかどうかな ...
public class Error {
	private static final Logger log = LoggerFactory.getLogger(Error.class);

	private Error() { }

	/**
	 * エラーの種類.
	 * @author OES Project
	 */
	public enum Category {
		/**
		 * ハードウェア.
		 * 制御対象機器やコンピュータなど.
		 */
		HARDWARE,
		/**
		 * フレームワーク.
		 * ソフトウェアやネットワークなど.
		 */
		FRAMEWORK,
		/**
		 * ロジック.
		 * ソフトウェア制御上の不整合など.
		 */
		LOGIC,
		/**
		 * ユーザ.
		 * 設定など.
		 */
		USER,
		/**
		 * 不明.
		 * プログラムからは発生しない.
		 */
		UNKNOWN,
	}
	/**
	 * エラーの範囲.
	 * @author OES Project
	 */
	public enum Extent {
		/**
		 * クラスタ全体.
		 */
		GLOBAL,
		/**
		 * ユニット.
		 */
		LOCAL,
		/**
		 * 不明.
		 * プログラムからは発生しない.
		 */
		UNKNOWN,
	}
	/**
	 * エラーの深刻さ.
	 * @author OES Project
	 */
	public enum Level {
		/**
		 * 警告.
		 * ログに出力するだけ.
		 */
		WARN,
		/**
		 * 障害.
		 * 融通を停止したり状態をリセットしたり.
		 */
		ERROR,
		/**
		 * 致命的.
		 * シャットダウンする.
		 */
		FATAL,
		/**
		 * 不明.
		 * プログラムからは発生しない.
		 */
		UNKNOWN,
	}

	/**
	 * 文字列から対応する {@link Category} を取得.
	 * @param value 文字列
	 * @return {@code value} に対応する category オブジェクト.
	 *         該当する {@link Category} がつからなければ {@link Category#UNKNOWN}.
	 */
	public static Category category(String value) {
		try {
			return Error.Category.valueOf(value);
		} catch (Exception e) {
			log.error(e);
			return Error.Category.UNKNOWN;
		}
	}
	/**
	 * 文字列から対応する {@link Extent} を取得.
	 * @param value 文字列
	 * @return {@code value} に対応する extent オブジェクト.
	 *         該当する {@link Extent} がつからなければ {@link Extent#UNKNOWN}.
	 */
	public static Extent extent(String value) {
		try {
			return Error.Extent.valueOf(value);
		} catch (Exception e) {
			log.error(e);
			return Error.Extent.UNKNOWN;
		}
	}
	/**
	 * 文字列から対応する {@link Level} を取得.
	 * @param value 文字列
	 * @return {@code value} に対応する level オブジェクト.
	 *         該当する {@link Level} がつからなければ {@link Level#UNKNOWN}.
	 */
	public static Level level(String value) {
		try {
			return Error.Level.valueOf(value);
		} catch (Exception e) {
			log.error(e);
			return Error.Level.UNKNOWN;
		}
	}

	/**
	 * エラーを表す {@link JsonObject} からエラー生成ユニット ID を取得.
	 * @param error 対象のエラー jsonobject オブジェクト
	 * @return エラーを生成したユニット ID
	 */
	public static String unitId(JsonObject error) {
		return error.getString("unitId");
	}
	/**
	 * エラーを表す {@link JsonObject} から {@link Category} を取得.
	 * @param error 対象のエラー jsonobject オブジェクト
	 * @return category オブジェクト
	 */
	public static Category category(JsonObject error) {
		return category(error.getString("category"));
	}
	/**
	 * エラーを表す {@link JsonObject} から {@link Extent} を取得.
	 * @param error 対象のエラー jsonobject オブジェクト
	 * @return extent オブジェクト
	 */
	public static Extent extent(JsonObject error) {
		return extent(error.getString("extent"));
	}
	/**
	 * エラーを表す {@link JsonObject} から {@link Level} を取得.
	 * @param error 対象のエラー jsonobject オブジェクト
	 * @return level オブジェクト
	 */
	public static Level level(JsonObject error) {
		return level(error.getString("level"));
	}
	/**
	 * エラーを表す {@link JsonObject} からエラーメッセージを取得.
	 * @param error 対象のエラー jsonobject オブジェクト
	 * @return エラーメッセージ
	 */
	public static String message(JsonObject error) {
		return error.getString("message");
	}
	/**
	 * エラーを表す {@link JsonObject} からスタックトレースを取得.
	 * @param error 対象のエラー jsonobject オブジェクト
	 * @return スタックトレースを表す jsonobject オブジェクト
	 */
	public static JsonObject stackTrace(JsonObject error) {
		return JsonObjectUtil.getJsonObject(error, "stackTrace");
	}

	/**
	 * エラーを表す {@link JsonObject} から {@link StackTraceElement} を取得.
	 * @param error 対象のエラー jsonobject オブジェクト
	 * @return stacktraceelement オブジェクト
	 */
	public static StackTraceElement stackTraceElement(JsonObject error) {
		JsonObject ste = stackTrace(error);
		if (ste != null && 0 < ste.size()) {
			return new StackTraceElement(ste.getString("className"), ste.getString("methodName"), ste.getString("fileName"), ste.getInteger("lineNumber"));
		}
		return null;
	}

	/**
	 * エラーを表す {@link JsonObject} からログ出力用の文字列を取得.
	 * @param error 対象のエラー jsonobject オブジェクト
	 * @return ログ出力用文字列
	 */
	public static String logMessage(JsonObject error) {
		Category category = category(error);
		Extent extent = extent(error);
		Level level = level(error);
		String message = message(error);
		String unitId = unitId(error);
		StackTraceElement ste = stackTraceElement(error);
		return logMessage(category, extent, level, message, unitId, ste);
	}
	/**
	 * ログ出力用の文字列を取得.
	 * @param category エラーの category オブジェクト
	 * @param extent エラーの extent オブジェクト
	 * @param level エラーの level オブジェクト
	 * @param message エラーメッセージ
	 * @param unitId エラー生成ユニット ID
	 * @param ste エラーの stacktraceelement オブジェクト
	 * @return ログ出力用文字列
	 */
	public static String logMessage(Category category, Extent extent, Level level, String message, String unitId, StackTraceElement ste) {
		StringBuilder sb = new StringBuilder();
		sb.append('(').append(category).append(':').append(extent).append(':').append(level).append(':').append(unitId).append(") ").append(message);
		if (ste != null) {
			sb.append(" [").append(ste).append(']');
		}
		return sb.toString();
	}

	/**
	 * 例外からログ用のメッセージ文字列を取得.
	 * @param t 対象の throwable オブジェクト
	 * @return ログ用のメッセージ文字列
	 */
	public static String messageFromThrowable(Throwable t) {
		return (null != t) ? t.toString() : "( Throwable is null )";
	}

	////

	/**
	 * エラーを表す {@link JsonObject} を作成.
	 * @param unitId エラー生成ユニット ID
	 * @param category エラーの category オブジェクト
	 * @param extent エラーの extent オブジェクト
	 * @param level エラーの level オブジェクト
	 * @param message エラーメッセージ
	 * @param ste エラーの stacktraceelement オブジェクト
	 * @return エラーを表す jsonobject オブジェクト
	 */
	public static JsonObject generateErrorObject(String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message, StackTraceElement ste) {
		JsonObject json = new JsonObject();
		json.put("unitId", unitId);
		json.put("category", category.name());
		json.put("extent", extent.name());
		json.put("level", level.name());
		json.put("message", message);
		if (ste != null) {
			JsonObjectUtil.put(json, ste.getFileName(), "stackTrace", "fileName");
			JsonObjectUtil.put(json, ste.getLineNumber(), "stackTrace", "lineNumber");
			JsonObjectUtil.put(json, ste.getClassName(), "stackTrace", "className");
			JsonObjectUtil.put(json, ste.getMethodName(), "stackTrace", "methodName");
		}
		return json;
	}
	/**
	 * エラーを表す {@link JsonObject} を作成し {@link io.vertx.core.eventbus.EventBus#publish(String, Object) EventBus に publish} する.
	 * @param vertx the {@link Vertx} instance, cannot be {@code null}
	 * @param unitId エラー生成ユニット ID
	 * @param category エラーの category オブジェクト
	 * @param extent エラーの extent オブジェクト
	 * @param level エラーの level オブジェクト
	 * @param message エラーメッセージ
	 * @param ste エラーの stacktraceelement オブジェクト
	 */
	public static void report(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message, StackTraceElement ste) {
		JsonObject json = generateErrorObject(unitId, category, extent, level, message, ste);
		vertx.eventBus().publish(ServiceAddress.error(), json);
	}

	private static final Class<?>[] lastStackTraceArg_ = new Class<?>[] { Error.class };
	/**
	 * エラーを表す {@link JsonObject} を作成し {@link io.vertx.core.eventbus.EventBus#publish(String, Object) EventBus に publish} する.
	 * {@link StackTraceUtil#lastStackTrace(Class[]) 呼び出し元のスタックトレースから最後の要素を生成} して使用する.
	 * @param vertx the {@link Vertx} instance, cannot be {@code null}
	 * @param unitId エラー生成ユニット ID
	 * @param category エラーの category オブジェクト
	 * @param extent エラーの extent オブジェクト
	 * @param level エラーの level オブジェクト
	 * @param message エラーメッセージ
	 */
	public static void report(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message) {
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		report(vertx, unitId, category, extent, level, message, ste);
	}
	/**
	 * エラーを表す {@link JsonObject} を作成し {@link io.vertx.core.eventbus.EventBus#publish(String, Object) EventBus に publish} する.
	 * {@code throwable} から {@link #messageFromThrowable(Throwable) ログ用のメッセージ文字列を生成} して使用する.
	 * 呼び出し元のスタックトレースの最後の {@link StackTraceElement} を生成して使用する.
	 * @param vertx the {@link Vertx} instance, cannot be {@code null}
	 * @param unitId エラー生成ユニット ID
	 * @param category エラーの category オブジェクト
	 * @param extent エラーの extent オブジェクト
	 * @param level エラーの level オブジェクト
	 * @param throwable エラーの throwable オブジェクト
	 */
	public static void report(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable) {
		report(vertx, unitId, category, extent, level, messageFromThrowable(throwable));
	}

}
