package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
//import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * クラスタ内に他に GridMaster が存在しないかチェックする Verticle.
 * {@link GridMaster} から起動される.
 * @author OES Project
 */
public class Helo extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Helo.class);

	/**
	 * クラスタ内に自分以外に GridMaster が存在しないか定期的にチェックする周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_HELO_PERIOD_MSEC = 5000L;

	private long heloTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 定期的に GridMaster が重複しないための仕組みを動かすタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
//		checkUniqueness_(resCheckUniqueness -> {
//			if (resCheckUniqueness.succeeded()) {
				startHeloService_(resHelo -> {
					if (resHelo.succeeded()) {
						heloTimerHandler_(0L);
						if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
						startFuture.complete();
					} else {
						startFuture.fail(resHelo.cause());
					}
				});
//			} else {
//				startFuture.fail(resCheckUniqueness.cause());
//			}
//		});
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

//	private void checkUniqueness_(Handler<AsyncResult<Void>> completionHandler) {
//		vertx.eventBus().send(ServiceAddress.GridMaster.helo(), null, repHelo -> {
//			if (repHelo.succeeded()) {
//				completionHandler.handle(Future.failedFuture("GridMaster already exists"));
//			} else {
//				if (ReplyFailureUtil.isNoHandlers(repHelo)) {
//					completionHandler.handle(Future.succeededFuture());
//				} else {
//					completionHandler.handle(Future.failedFuture(repHelo.cause()));
//				}
//			}
//		});
//	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.GridMaster#helo()}
	 * 範囲 : グローバル
	 * 処理 : GridMaster が重複しないための仕組み.
	 * 　　   メッセージボディが空なら自ユニットのユニット ID を返す ( GM ユニット ID の問合せなので ).
	 * 　　   メッセージボディがあり自分の {@link #deploymentID()} と一致したらスルー ( 自分なので ).
	 * 　　   メッセージボディがあり自分の {@link #deploymentID()} と一致しなかったらグローバルエラー ( 他に GM がいるということなので ).
	 * メッセージボディ : 送信元 {@link Helo} Verticle の {@link #deploymentID()} [{@link String}]
	 * メッセージヘッダ : なし
	 * レスポンス : メッセージボディが空なら自ユニットの ID [{@link String}].
	 * 　　　　　   それ以外は返さない.
	 * @param completionHandler the completion handler
	 */
	private void startHeloService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>consumer(ServiceAddress.GridMaster.helo(), req -> {
			String senderDeploymentID = req.body();
			if (null == senderDeploymentID) {
				// 値が送りつけられていなければただの問い合わせ
				// 自ユニットの ID を返す
				req.reply(ApisConfig.unitId());
			} else {
				// 値が送りつけられていたらそれは送り主の deploymentID
				if (!senderDeploymentID.equals(deploymentID())) {
					// 自分のと違う場合は他にも GridMaster がいるということ
					// → GLOBAL ERROR でリセットする
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "another GridMaster exists !!!");
				}
				// 自分のと同じなら問題なし
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * クラスタ内に自分以外に GridMaster が存在しないか定期的にチェックするタイマの設定.
	 * 待ち時間は {@code POLICY.gridMaster.heloPeriodMsec} ( デフォルト値 {@link #DEFAULT_HELO_PERIOD_MSEC} ).
	 */
	private void setHeloTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_HELO_PERIOD_MSEC, "gridMaster", "heloPeriodMsec");
		setHeloTimer_(delay);
	}
	/**
	 * クラスタ内に自分以外に GridMaster が存在しないか定期的にチェックするタイマの設定.
	 * @param delay 周期 [ms]
	 */
	private void setHeloTimer_(long delay) {
		heloTimerId_ = vertx.setTimer(delay, this::heloTimerHandler_);
	}
	/**
	 * クラスタ内に自分以外に GridMaster が存在しないか定期的にチェックするタイマ処理.
	 * @param timerId タイマ ID
	 */
	private void heloTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != heloTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", heloTimerId_ : " + heloTimerId_);
			return;
		}
		// 自分の deploymentID を持って publish する ( 殺人ビーム )
		vertx.eventBus().publish(ServiceAddress.GridMaster.helo(), deploymentID());
		setHeloTimer_();
	}

}
