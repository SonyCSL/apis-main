package jp.co.sony.csl.dcoes.apis.main.app.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.error.handling.AbstractErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalAnyFatalsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalFrameworkErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalHardwareErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalLogicErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalUserErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * エラーに対応する Verticle.
 * {@link User} Verticle から起動される.
 * 定期的にエラーの有無を確認する.
 * エラーが存在していたらその種類と範囲と深刻さごとに適切な対応処理を作成して実行させる.
 * @author OES Project
 */
public class ErrorHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(ErrorHandling.class);

	/**
	 * エラー処理周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_ERROR_HANDLING_PERIOD_MSEC = 1000L;

	private long errorHandlingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * 定期的にエラーをチェックし処理するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		errorHandlingTimerHandler_(0L);
		if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
		startFuture.complete();
	}

	/**
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * エラー処理タイマ設定.
	 * 待ち時間は {@code POLICY.user.errorHandlingPeriodMsec} ( デフォルト値 {@link #DEFAULT_ERROR_HANDLING_PERIOD_MSEC} ).
	 */
	private void setErrorHandlingTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_ERROR_HANDLING_PERIOD_MSEC, "user", "errorHandlingPeriodMsec");
		setErrorHandlingTimer_(delay);
	}
	/**
	 * エラー処理タイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setErrorHandlingTimer_(long delay) {
		errorHandlingTimerId_ = vertx.setTimer(delay, this::errorHandlingTimerHandler_);
	}
	/**
	 * エラー処理タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void errorHandlingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != errorHandlingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", errorHandlingTimerId_ : " + errorHandlingTimerId_);
			return;
		}
		// POLICY を確保し ( 処理中に変更される可能性があるので )
		JsonObject policy = PolicyKeeping.cache().jsonObject();
		new HandleErrors_(policy).doLoop_(r -> {
			// 処理が終わったら終わったマークをつける
			ErrorCollection.errorHandled_();
			setErrorHandlingTimer_();
		});
	}

	////

	/**
	 * エラー処理クラス.
	 * エラーの種類をループする.
	 * @author OES Project
	 */
	private class HandleErrors_ {
		private JsonObject policy_;
		private List<Error.Category> categoriesForLoop_;
		/**
		 * インスタンス生成.
		 * @param policy POLICY オブジェクト
		 */
		private HandleErrors_(JsonObject policy) {
			policy_ = policy;
			categoriesForLoop_ = new ArrayList<Error.Category>(Arrays.asList(Error.Category.values()));
		}
		private void doLoop_(Handler<AsyncResult<Void>> completionHandler) {
			if (categoriesForLoop_.isEmpty()) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				Error.Category aCategory = categoriesForLoop_.remove(0);
				new HandleErrorsByCategory_(policy_, aCategory).doLoop_(r -> {
					doLoop_(completionHandler);
				});
			}
		}
	}
	/**
	 * エラー処理クラス.
	 * エラー種類を指定されて誕生する.
	 * エラーの深刻さをループする.
	 * @author OES Project
	 * TODO : gridmaster.ErrorHandling のように HandleErrorsByCategory_ を HandleErrors_ のインナークラスにしてしまってはどうでしょう
	 */
	private class HandleErrorsByCategory_ {
		private JsonObject policy_;
		private Error.Category category_;
		private List<Error.Level> levelsForLoop_;
		private HandleErrorsByCategory_(JsonObject policy, Error.Category category) {
			policy_ = policy;
			category_ = category;
			levelsForLoop_ = new ArrayList<Error.Level>(Arrays.asList(Error.Level.values()));
		}
		private void doLoop_(Handler<AsyncResult<Void>> completionHandler) {
			if (levelsForLoop_.isEmpty()) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				Error.Level aLevel = levelsForLoop_.remove(0);
				JsonArray errors = ErrorCollection.cache.removeJsonArray(category_.name(), aLevel.name());
				if (errors != null && 0 < errors.size()) {
					if (log.isInfoEnabled()) log.info("[" + category_ + ':' + aLevel + "] : " + errors);
					AbstractErrorsHandling handler = null;
					switch (aLevel) {
					case WARN:
						// 警告レベルは何もしない
						log.fatal("#### should never happen; category : " + category_ + ", level : " + aLevel);
						break;
					case ERROR:
						// 障害レベルなら種類に応じて処理オブジェクトを作りお任せする
						switch (category_) {
						case HARDWARE:
							handler = new LocalHardwareErrorsHandling(vertx, policy_, errors);
							break;
						case FRAMEWORK:
							handler = new LocalFrameworkErrorsHandling(vertx, policy_, errors);
							break;
						case LOGIC:
							handler = new LocalLogicErrorsHandling(vertx, policy_, errors);
							break;
						case USER:
							handler = new LocalUserErrorsHandling(vertx, policy_, errors);
							break;
						case UNKNOWN:
							log.fatal("#### should never happen; category : " + category_ + ", level : " + aLevel);
							break;
						}
						break;
					case FATAL:
					case UNKNOWN:
						// 致命的レベルまたは何らかの理由で不明レベルなら種類にかかわらず FATAL な処理 ( シャットダウン ) を実行する
						handler = new LocalAnyFatalsHandling(vertx, policy_, errors);
						break;
					}
					if (handler != null) {
						handler.handle(resHandle -> {
							doLoop_(completionHandler);
						});
					} else {
						doLoop_(completionHandler);
					}
				} else {
					doLoop_(completionHandler);
				}
			}
		}
	}

}
