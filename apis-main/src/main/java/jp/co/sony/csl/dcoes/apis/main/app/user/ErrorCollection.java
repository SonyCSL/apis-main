package jp.co.sony.csl.dcoes.apis.main.app.user;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;

/**
 * エラーを管理する Verticle.
 * {@link User} Verticle から起動される.
 * 任意の場所からブロードキャストされたエラー情報のうち自ユニットのローカルエラーのみを保持する.
 * @author OES Project
 */
public class ErrorCollection extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(ErrorCollection.class);

	/**
	 * エラー処理完了後引き続きエラー状態を一定時間維持する時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_ERROR_SUSTAINING_MSEC = 30000L;

	/**
	 * ローカルエラーを保持しておくキャッシュ.
	 */
	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private static boolean hasErrors_ = false;
	private static long errorHandledMillis_ = 0;

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startErrorTestingService_(resErrorTesting -> {
			if (resErrorTesting.succeeded()) {
				startErrorCollectingService_(resErrorCollecting -> {
					if (resErrorCollecting.succeeded()) {
						if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
						startFuture.complete();
					} else {
						startFuture.fail(resErrorCollecting.cause());
					}
				});
			} else {
				startFuture.fail(resErrorTesting.cause());
			}
		});
	}

	/**
	 * 停止時に呼び出される.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.User#errorTesting(String)}
	 * 範囲 : グローバル
	 * 処理 : ローカルエラーの有無を確認する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : ローカルエラーの有無 [{@link Boolean}]
	 * @param completionHandler the completion handler
	 */
	private void startErrorTestingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.User.errorTesting(ApisConfig.unitId()), req -> {
			req.reply(Boolean.valueOf(hasErrors()));
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#error()}
	 * 範囲 : グローバル
	 * 処理 : エラーを受信する.
	 * 　　   自ユニットのローカルエラーのみ保持する.
	 * メッセージボディ : エラー情報 [{@link JsonObject}]
	 * メッセージヘッダ : なし
	 * レスポンス : なし
	 * @param completionHandler the completion handler
	 */
	private void startErrorCollectingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.error(), req -> {
			handleError_(req);
		}).completionHandler(completionHandler);
	}

	////

	private void handleError_(Message<JsonObject> req) {
		JsonObject error = req.body();
		if (Error.Extent.LOCAL == Error.extent(error)) {
			// LOCAL のみを対象とする
			if (ApisConfig.unitId().equals(Error.unitId(error))) {
				doCache_(error);
				doWriteLog_(error);
			}
		}
	}

	////

	private void doCache_(JsonObject error) {
		if (Error.Level.WARN != Error.level(error)) {
			// WARN 以外を対象とする → ERROR FATAL UNKNOWN が対象
			errorReceived_();
			cache.add(error, Error.category(error).name(), Error.level(error).name());
		}
	}

	private void doWriteLog_(JsonObject error) {
		switch (Error.level(error)) {
		case WARN:
			if (log.isWarnEnabled()) log.warn(Error.logMessage(error));
			break;
		case ERROR:
			log.error(Error.logMessage(error));
			break;
		case FATAL:
			log.fatal(Error.logMessage(error));
			break;
		default:
			log.fatal(Error.logMessage(error));
			break;
		}
	}

	////

	/**
	 * ローカルエラーの有無を取得する.
	 * 直近のエラー処理終了から POLICY.user.errorSustainingMsec 経つまでは有りと判定する.
	 * @return エラーがあれば true.
	 *         エラーがなくても直近のエラー処理終了から POLICY.user.errorSustainingMsec 以内であれば true.
	 *         それ以外は false.
	 */
	public static boolean hasErrors() {
		if (hasErrors_) {
			return true;
		} else {
			// 現時点でエラーがなくても
			if (0 < errorHandledMillis_) {
				// 過去にエラー処理が実行され
				long errorSustainingMsec = PolicyKeeping.cache().getLong(DEFAULT_ERROR_SUSTAINING_MSEC, "user", "errorSustainingMsec");
				if (System.currentTimeMillis() < errorHandledMillis_ + errorSustainingMsec) {
					// その処理が終了した時刻からまだ POLICY.user.errorSustainingMsec ( デフォルト DEFAULT_ERROR_SUSTAINING_MSEC ) 経過していなければ
					// エラーあり判定を返す
					return true;
				} else {
					errorHandledMillis_ = 0;
				}
			}
		}
		return false;
	}

	static synchronized void errorReceived_() {
		hasErrors_ = true;
	}
	/**
	 * エラー処理が完了したことを通知する.
	 * 直近のエラー処理終了時刻を記録しておく.
	 */
	static synchronized void errorHandled_() {
		if (hasErrors_) {
			hasErrors_ = false;
			errorHandledMillis_ = System.currentTimeMillis();
		}
	}

}
