package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.StackTraceUtil;

/**
 * {@link Error} まわりの便利ツール.
 * @author OES Project
 */
// これは FailureUtil ...
public class ErrorUtil {

	private ErrorUtil() { }

	private static final Class<?>[] lastStackTraceArg_ = new Class<?>[] { ErrorUtil.class };
	/**
	 * エラーオブジェクトを生成する.
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @return エラーオブジェクト
	 */
	public static JsonObject generateErrorObject(String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message) {
		// スタックトレースの最後の行を取得する
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		return Error.generateErrorObject(unitId, category, extent, level, message, ste);
	}

	/**
	 * エラーを送出する.
	 * @param vertx vertx オブジェクト
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 */
	public static void report(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message) {
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		Error.report(vertx, unitId, category, extent, level, message, ste);
	}
	/**
	 * エラーを送出する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 */
	public static void report(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message) {
		report(vertx, ApisConfig.unitId(), category, extent, level, message);
	}
	/**
	 * エラーを送出する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 */
	public static void report(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable) {
		report(vertx, category, extent, level, Error.messageFromThrowable(throwable));
	}
	/**
	 * エラーを送出する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 */
	public static void report(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable) {
		report(vertx, category, extent, level, Error.messageFromThrowable(message, throwable));
	}

	/**
	 * エラーを送出し {@code toReplyTo} を fail させる.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message, Message<T> toReplyTo) {
		report(vertx, unitId, category, extent, level, message);
		toReplyTo.fail(level.ordinal(), message);
	}
	/**
	 * エラーを送出し {@code toReplyTo} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Message<T> toReplyTo) {
		reportAndFail(vertx, ApisConfig.unitId(), category, extent, level, message, toReplyTo);
	}
	/**
	 * エラーを送出し {@code toReplyTo} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable, Message<T> toReplyTo) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(throwable), toReplyTo);
	}
	/**
	 * エラーを送出し {@code toReplyTo} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable, Message<T> toReplyTo) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(message, throwable), toReplyTo);
	}

	/**
	 * エラーを送出し {@code future} を fail させる.
	 * @param <T> future オブジェクトの {@link Future#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param future fail させる future オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message, Future<T> future) {
		report(vertx, unitId, category, extent, level, message);
		future.fail(message);
	}
	/**
	 * エラーを送出し {@code future} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> future オブジェクトの {@link Future#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param future fail させる future オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Future<T> future) {
		reportAndFail(vertx, ApisConfig.unitId(), category, extent, level, message, future);
	}
	/**
	 * エラーを送出し {@code future} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> future オブジェクトの {@link Future#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param future fail させる future オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable, Future<T> future) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(throwable), future);
	}
	/**
	 * エラーを送出し {@code future} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> future オブジェクトの {@link Future#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @param future fail させる future オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable, Future<T> future) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(message, throwable), future);
	}

	/**
	 * エラーを送出し {@code completionHandler} を fail する.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param completionHandler fail させる completionhandler オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message, Handler<AsyncResult<T>> completionHandler) {
		report(vertx, unitId, category, extent, level, message);
		completionHandler.handle(Future.failedFuture(message));
	}
	/**
	 * エラーを送出し {@code completionHandler} を fail する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param completionHandler fail させる completionhandler オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Handler<AsyncResult<T>> completionHandler) {
		reportAndFail(vertx, ApisConfig.unitId(), category, extent, level, message, completionHandler);
	}
	/**
	 * エラーを送出し {@code completionHandler} を fail する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionhandler オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(throwable), completionHandler);
	}
	/**
	 * エラーを送出し {@code completionHandler} を fail する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionhandler オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(message, throwable), completionHandler);
	}

}
