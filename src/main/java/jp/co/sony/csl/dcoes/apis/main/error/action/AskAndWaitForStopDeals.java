package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * エラー処理の実クラス.
 * 参加している融通を定期的に確認し停止を GridMaster に依頼する.
 * 参加している融通が無くなったら終了する.
 * 規定の時間が過ぎたら諦める.
 * @author OES Project
 */
public class AskAndWaitForStopDeals extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(AskAndWaitForStopDeals.class);

	/**
	 * 最初に融通停止依頼を出してから自ユニットを強制停止するまでの待ち時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	public static final Long DEFAULT_STOP_ME_TIMEOUT_MSEC = 60000L;
	/**
	 * 融通が消滅したか定期的に確認し融通停止依頼を出す時間間隔のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	public static final Long DEFAULT_STOP_ME_CHECK_PERIOD_MSEC = 1000L;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} あるいは {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling} でコピーしたものが渡される.
	 * @param logMessages エラー処理で記録するログメッセージのリスト
	 */
	public AskAndWaitForStopDeals(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		new AskAndWaitForStopDeals_().execute_(completionHandler);
	}

	/**
	 * 融通がなくなるまで融通停止依頼を出し続ける処理を実装したクラス.
	 * @author OES Project
	 */
	private class AskAndWaitForStopDeals_ {
		private Handler<AsyncResult<Void>> completionHandler_;
		private long timedOutTimerId_ = 0L;
		private long waitForStopTimerId_ = 0L;
		private boolean timedOut_ = false;
		private void execute_(Handler<AsyncResult<Void>> completionHandler) {
			completionHandler_ = completionHandler;
			// 時間切れタイマを仕込む. 時間は POLICY.controller.stopMeTimeoutMsec . デフォルト DEFAULT_STOP_ME_TIMEOUT_MSEC
			timedOutTimerId_ = vertx_.setTimer(PolicyKeeping.cache().getLong(DEFAULT_STOP_ME_TIMEOUT_MSEC, "controller", "stopMeTimeoutMsec"), timerId -> {
				// 時間切れになったらフラグを立てる
				timedOut_ = true;
			});
			// 処理を実行する
			waitForStopTimerHandler_(0L);
		}

		/**
		 * 融通をチェックし停止要求を送るタイマ設定.
		 * 待ち時間は {@code POLICY.controller.stopMeCheckPeriodMsec} ( デフォルト値 {@link #DEFAULT_STOP_ME_CHECK_PERIOD_MSEC} ).
		 */
		private void setWaitForStopTimer_() {
			Long delay = PolicyKeeping.cache().getLong(DEFAULT_STOP_ME_CHECK_PERIOD_MSEC, "controller", "stopMeCheckPeriodMsec");
			setWaitForStopTimer_(delay);
		}
		/**
		 * 融通をチェックし停止要求を送るタイマ設定.
		 * @param delay 周期 [ms]
		 */
		private void setWaitForStopTimer_(long delay) {
			if (log.isInfoEnabled()) log.info("waiting for deal stops ...");
			waitForStopTimerId_ = vertx_.setTimer(delay, this::waitForStopTimerHandler_);
		}
		/**
		 * 融通をチェックし停止要求を送るタイマ設定.
		 * @param timerId タイマ ID
		 */
		private void waitForStopTimerHandler_(Long timerId) {
			if (null == timerId || timerId.longValue() != waitForStopTimerId_) {
				ErrorUtil.report(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", waitForStopTimerId_ : " + waitForStopTimerId_);
				return;
			}
			DealUtil.withUnitId(vertx_, ApisConfig.unitId(), resWithUnitId -> {
				if (resWithUnitId.succeeded()) {
					List<JsonObject> deals = resWithUnitId.result();
					if (deals.isEmpty()) {
						// 融通が無くなったら
						if (log.isInfoEnabled()) log.info("done");
						// 時間切れ用タイマをキャンセルし
						vertx_.cancelTimer(timedOutTimerId_);
						// 成功で終了
						completionHandler_.handle(Future.succeededFuture());
					} else if (timedOut_) {
						// 融通がまだあるけど時間切れ
						if (log.isWarnEnabled()) log.warn("... timed out");
						// 失敗で終了
						completionHandler_.handle(Future.failedFuture("timed out"));
					} else {
						// 融通がまだあり時間切れもしていない
						if (log.isInfoEnabled()) log.info("deal exists ...");
						// 停止依頼を出す
						doAskForStop_(deals);
						// また待つ
						setWaitForStopTimer_();
					}
				} else {
					if (log.isWarnEnabled()) log.warn("... failed");
					vertx_.cancelTimer(timedOutTimerId_);
					completionHandler_.handle(Future.failedFuture(resWithUnitId.cause()));
				}
			});
		}

		private void doAskForStop_(List<JsonObject> deals) {
			if (log.isInfoEnabled()) log.info(deals.size() + " deal(s) found");
			for (JsonObject aDeal : deals) {
				if (Deal.isDeactivated(aDeal)) {
					if (log.isInfoEnabled()) log.info("no need to ask for stop deal : " + Deal.dealId(aDeal));
				} else {
					if (log.isInfoEnabled()) log.info("asking for stop deal : " + Deal.dealId(aDeal));
					JsonObject message = new JsonObject().put("dealId", Deal.dealId(aDeal)).put("reasons", logMessages_);
					// 融通 ID と理由文字列を持って要求を送出
					vertx_.eventBus().send(ServiceAddress.Mediator.dealNeedToStop(), message);
				}
			}
		}

	}

}
