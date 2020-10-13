package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * クラスタ内に同一 ID のユニットが存在しないかチェックする Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * @author OES Project
 */
public class Helo extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Helo.class);

	/**
	 * クラスタ内に同一 ID のユニットが存在しないか定期的にチェックする周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_HELO_PERIOD_MSEC = 5000L;

	private long heloTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * クラスタ内に同一 ID のユニットが存在しないかチェックする.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * タイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		// 起動時に同一 ID を持つユニットの存在を確認する
		checkUniqueness_(resCheckUniqueness -> {
			if (resCheckUniqueness.succeeded()) {
				// 同一 ID 確認 OK ( いなかった )
				startHeloService_(resHelo -> {
					if (resHelo.succeeded()) {
						heloTimerHandler_(0L);
						if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
						startFuture.complete();
					} else {
						startFuture.fail(resHelo.cause());
					}
				});
			} else {
				// 同一 ID 確認 NG ( いた ) → 起動失敗
				startFuture.fail(resCheckUniqueness.cause());
			}
		});
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

	private void checkUniqueness_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().send(ServiceAddress.helo(ApisConfig.unitId()), null, repHelo -> {
			if (repHelo.succeeded()) {
				// 返事があったら失敗
				completionHandler.handle(Future.failedFuture("unit with id " + ApisConfig.unitId() + " already exists !!!"));
			} else {
				if (ReplyFailureUtil.isNoHandlers(repHelo)) {
					// 口が開いてなかったので OK
					completionHandler.handle(Future.succeededFuture());
				} else {
					// 何やらエラーが起きたので失敗
					completionHandler.handle(Future.failedFuture(repHelo.cause()));
				}
			}
		});
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#helo(String)}
	 * 範囲 : グローバル
	 * 処理 : クラスタ内に同一 ID を持つユニットが重複しないための仕組み.
	 * 　　   メッセージボディが空なら自ユニットのユニット ID を返す.
	 * 　　   メッセージボディがあり自分の {@link #deploymentID()} と一致したらスルー ( 自分なので ).
	 * 　　   メッセージボディがあり自分の {@link #deploymentID()} と一致しなかったら FATAL エラー ( 他に同じ ID のユニットがいるということなので ).
	 * メッセージボディ : 送信元 {@link Helo} Verticle の {@link #deploymentID()} [{@link String}]
	 * メッセージヘッダ : なし
	 * レスポンス : メッセージボディが空なら自ユニットの ID [{@link String}].
	 * 　　　　　   それ以外は返さない.
	 * @param completionHandler the completion handler
	 */
	private void startHeloService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>consumer(ServiceAddress.helo(ApisConfig.unitId()), req -> {
			String senderDeploymentID = req.body();
			if (null == senderDeploymentID) {
				// 値が送りつけられていなければただの問い合わせ
				// 自ユニットの ID を返す
				req.reply(ApisConfig.unitId());
			} else {
				// 値が送りつけられていたらそれは送り主の deploymentID
				if (!senderDeploymentID.equals(deploymentID())) {
					// 自分のと違う場合は他にも同じ ID のユニットがいるということ
					// → LOCAL FATAL でシャットダウンする
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.FATAL, "another unit with id " + ApisConfig.unitId() + " found !!!");
				}
				// 自分のと同じなら問題なし
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * クラスタ内に同一 ID のユニットが存在しないか定期的にチェックするタイマの設定.
	 * 待ち時間は {@code POLICY.heloPeriodMsec} ( デフォルト値 {@link #DEFAULT_HELO_PERIOD_MSEC} ).
	 */
	private void setHeloTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_HELO_PERIOD_MSEC, "heloPeriodMsec");
		setHeloTimer_(delay);
	}
	/**
	 * クラスタ内に同一 ID のユニットが存在しないか定期的にチェックするタイマの設定.
	 * @param delay 周期 [ms]
	 */
	private void setHeloTimer_(long delay) {
		heloTimerId_ = vertx.setTimer(delay, this::heloTimerHandler_);
	}
	/**
	 * クラスタ内に同一 ID のユニットが存在しないか定期的にチェックするタイマ処理.
	 * @param timerId タイマ ID
	 */
	private void heloTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != heloTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", heloTimerId_ : " + heloTimerId_);
			return;
		}
		// 自分の deploymentID を持って publish する ( 殺人ビーム )
		vertx.eventBus().publish(ServiceAddress.helo(ApisConfig.unitId()), deploymentID());
		setHeloTimer_();
	}

}
