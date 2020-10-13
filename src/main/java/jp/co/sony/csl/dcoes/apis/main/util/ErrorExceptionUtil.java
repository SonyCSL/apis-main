package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ErrorException;
import jp.co.sony.csl.dcoes.apis.common.util.StackTraceUtil;

/**
 * {@link ErrorException} まわりの便利ツール.
 * @author OES Project
 */
// んでこっちが FailureExceptionUtil ...
public class ErrorExceptionUtil {
	private static final Logger log = LoggerFactory.getLogger(ErrorExceptionUtil.class);

	private ErrorExceptionUtil() { }

	/**
	 * {@link ErrorException} を生成する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @return {@link ErrorException} オブジェクト
	 */
	public static ErrorException create(Error.Category category, Error.Extent extent, Error.Level level, String message) {
		return ErrorException.create(ApisConfig.unitId(), category, extent, level, message);
	}
	/**
	 * {@link ErrorException} を生成する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @return {@link ErrorException} オブジェクト
	 */
	public static ErrorException create(Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable) {
		return ErrorException.create(ApisConfig.unitId(), category, extent, level, Error.messageFromThrowable(throwable));
	}
	/**
	 * {@link ErrorException} を生成する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @return {@link ErrorException} オブジェクト
	 */
	public static ErrorException create(Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable) {
		return ErrorException.create(ApisConfig.unitId(), category, extent, level, Error.messageFromThrowable(message, throwable));
	}

	/**
	 * ログを出力する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 */
	public static void log(Error.Category category, Error.Extent extent, Error.Level level, String message) {
		ErrorException e = create(category, extent, level, message);
		doWriteLog_(e);
	}
	/**
	 * ログを出力し {@code completionHandler} を fail させる,
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param completionHandler fail させる completionHandler オブジェクト
	 */
	public static <T> void logAndFail(Error.Category category, Error.Extent extent, Error.Level level, String message, Handler<AsyncResult<T>> completionHandler) {
		ErrorException e = create(category, extent, level, message);
		doWriteLog_(e);
		completionHandler.handle(Future.failedFuture(e));
	}
	/**
	 * ログを出力する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 */
	public static void log(Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable) {
		ErrorException e = create(category, extent, level, throwable);
		doWriteLog_(e);
	}
	/**
	 * ログを出力し {@code completionHandler} を fail させる,
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionHandler オブジェクト
	 */
	public static <T> void logAndFail(Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		ErrorException e = create(category, extent, level, throwable);
		doWriteLog_(e);
		completionHandler.handle(Future.failedFuture(e));
	}
	/**
	 * ログを出力する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 */
	public static void log(Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable) {
		ErrorException e = create(category, extent, level, message, throwable);
		doWriteLog_(e);
	}
	/**
	 * ログを出力し {@code completionHandler} を fail させる,
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionHandler オブジェクト
	 */
	public static <T> void logAndFail(Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		ErrorException e = create(category, extent, level, message, throwable);
		doWriteLog_(e);
		completionHandler.handle(Future.failedFuture(e));
	}

	private static final Class<?>[] lastStackTraceArg_ = new Class<?>[] { ErrorExceptionUtil.class };
	/**
	 * 必要に応じてエラーを送出する.
	 * {@code throwable} が {@link ErrorException} のサブクラスならエラーを送出する.
	 * @param vertx vertx オブジェクト
	 * @param throwable 例外オブジェクト
	 */
	public static void reportIfNeed(Vertx vertx, Throwable throwable) {
		if (throwable instanceof ErrorException) {
			ErrorException e = (ErrorException) throwable;
			StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
			Error.report(vertx, e.unitId, e.category, e.extent, e.level, e.getMessage(), ste);
		}
	}

	/**
	 * {@code result} が成功していたら何もせず {@code toReplyTo} に reply する.
	 * {@code result} が失敗していたら必要に応じてエラーを送出し {@code toReplyTo} を fail する.
	 * @param <T> result オブジェクトの {@link AsyncResult#result()} および toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param result 成功か失敗か判定する asyncresult オブジェクト
	 * @param toReplyTo reply もしくは fail させる message オブジェクト
	 */
	public static <T> void reportIfNeedAndReply(Vertx vertx, AsyncResult<T> result, Message<T> toReplyTo) {
		if (result.succeeded()) {
			toReplyTo.reply(result.result());
		} else {
			reportIfNeedAndFail(vertx, result.cause(), toReplyTo);
		}
	}
	/**
	 * {@code result} が成功していたら何もせず {@code completionHandler} を succeed する.
	 * {@code result} が失敗していたら必要に応じてエラーを送出し {@code completionHandler} を fail する.
	 * @param <T> result オブジェクトの {@link AsyncResult#result()} および completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param result 成功か失敗か判定する asyncresult オブジェクト
	 * @param completionHandler succeed もしくは fail させる completionHandler オブジェクト
	 */
	public static <T> void reportIfNeedAndHandle(Vertx vertx, AsyncResult<T> result, Handler<AsyncResult<T>> completionHandler) {
		if (result.succeeded()) {
			completionHandler.handle(result);
		} else {
			reportIfNeedAndFail(vertx, result.cause(), completionHandler);
		}
	}

	/**
	 * 必要に応じてエラーを送出し {@code toReplyTo} を fail する.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportIfNeedAndFail(Vertx vertx, Throwable throwable, Message<T> toReplyTo) {
		reportIfNeed(vertx, throwable);
		toReplyTo.fail(failureCode_(throwable), throwable.getMessage());
	}
	/**
	 * 必要に応じてエラーを送出し {@code completionHandler} を fail する.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionHandler オブジェクト
	 */
	public static <T> void reportIfNeedAndFail(Vertx vertx, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		reportIfNeed(vertx, throwable);
		completionHandler.handle(Future.failedFuture(throwable));
	}

	/**
	 * ログメッセージを生成し適切なレベルでログを出力する.
	 * @param e {@link ErrorException} オブジェクト
	 */
	private static void doWriteLog_(ErrorException e) {
		// スタックトレースの最後の一行を取得する
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		// ログ文字列を生成する
		String message = Error.logMessage(e.category, e.extent, e.level, e.getMessage(), ApisConfig.unitId(), ste);
		switch (e.level) {
		case WARN:
			if (log.isWarnEnabled()) log.warn(message);
			break;
		case ERROR:
			log.error(message);
			break;
		case FATAL:
			log.fatal(message);
			break;
		default:
			log.fatal(message);
			break;
		}
	}

	/**
	 * {@link Message} を {@link Message#fail(int, String)} させる際のコードを取得する.
	 * {@code throwable} が {@link ErrorException} なら {@link Error.Level#ordinal()} の値を使う.
	 * @param throwable 例外オブジェクト
	 * @return failureCode
	 */
	private static int failureCode_(Throwable throwable) {
		return (throwable instanceof ErrorException) ? ((ErrorException) throwable).level.ordinal() : -1;
	}

}
