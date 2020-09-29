package jp.co.sony.csl.dcoes.apis.common.util.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;

/**
 * EventBus の send 失敗の種類を判定するツール.
 * @author OES Project
 */
public class ReplyFailureUtil {

	private ReplyFailureUtil() { }

	/**
	 * EventBus の send 失敗の場合にその失敗の種類を取得する.
	 * @param reply send 結果の asyncresult オブジェクト
	 * @return 失敗の種類.
	 *         失敗でない場合は {@code null}.
	 *         {@link AsyncResult#cause()} が {@link ReplyException} ではない場合も {@code null}.
	 */
	public static ReplyFailure replyFailure(AsyncResult<?> reply) {
		if (reply != null && reply.failed()) {
			return replyFailure(reply.cause());
		}
		return null;
	}
	/**
	 * 例外が EventBus の send 失敗の場合にその失敗の種類を取得する.
	 * @param cause 例外
	 * @return 失敗の種類.
	 *         {@code cause} が {@link ReplyException} ではない場合は {@code null}.
	 */
	public static ReplyFailure replyFailure(Throwable cause) {
		if (cause instanceof ReplyException) {
			return ((ReplyException) cause).failureType();
		}
		return null;
	}

	/**
	 * EventBus の send 失敗の理由が EventBus タイムアウトか否か.
	 * @param reply send 結果の asyncresult オブジェクト
	 * @return タイムアウトなら true
	 */
	public static boolean isTimeout(AsyncResult<?> reply) {
		return ReplyFailure.TIMEOUT.equals(replyFailure(reply));
	}
	/**
	 * EventBus の send 失敗の理由が受信者不在か否か.
	 * @param reply send 結果の asyncresult オブジェクト
	 * @return 受信者不在なら true
	 */
	public static boolean isNoHandlers(AsyncResult<?> reply) {
		return ReplyFailure.NO_HANDLERS.equals(replyFailure(reply));
	}
	/**
	 * EventBus の send 失敗の理由が受信側エラーか否か.
	 * @param reply send 結果の asyncresult オブジェクト
	 * @return 受信側エラーなら true
	 */
	public static boolean isRecipientFailure(AsyncResult<?> reply) {
		return ReplyFailure.RECIPIENT_FAILURE.equals(replyFailure(reply));
	}

	/**
	 * 例外が EventBus の send 失敗の場合にその失敗の理由がタイムアウトか否か.
	 * @param cause 例外
	 * @return タイムアウトなら true
	 */
	public static boolean isTimeout(Throwable cause) {
		return ReplyFailure.TIMEOUT.equals(replyFailure(cause));
	}
	/**
	 * 例外が EventBus の send 失敗の場合にその失敗の理由が受信者不在か否か.
	 * @param cause 例外
	 * @return 受信者不在なら true
	 */
	public static boolean isNoHandlers(Throwable cause) {
		return ReplyFailure.NO_HANDLERS.equals(replyFailure(cause));
	}
	/**
	 * 例外が EventBus の send 失敗の場合にその失敗の理由が受信側エラーか否か.
	 * @param cause 例外
	 * @return 受信側エラーなら true
	 */
	public static boolean isRecipientFailure(Throwable cause) {
		return ReplyFailure.RECIPIENT_FAILURE.equals(replyFailure(cause));
	}

}
