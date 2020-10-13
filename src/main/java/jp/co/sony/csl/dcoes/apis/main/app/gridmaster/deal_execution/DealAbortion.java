package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;

/**
 * 融通を異常終了する.
 * @author OES Project
 */
public class DealAbortion extends AbstractStoppableDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealAbortion.class);

	private String reason_;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution} 開始時にコピーしたものが渡される.
	 * @param deal 処理対象 DEAL オブジェクト
	 * @param otherDeals 同時に存在している他の DEAL オブジェクトのリスト
	 * @param reason 異常終了の理由
	 */
	public DealAbortion(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals, String reason) {
		super(vertx, policy, deal, otherDeals);
		reason_ = reason;
	}
	/**
	 * インスタンスを生成する.
	 * 他の {@link AbstractDealExecution} の内部状態をそのまま受け継ぐため初期化不要.
	 * @param other 他の abstractdealexecution オブジェクト
	 * @param reason 異常終了の理由
	 */
	public DealAbortion(AbstractDealExecution other, String reason) {
		super(other);
		reason_ = reason;
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		// 電圧リファレンス側でない方のデバイスを停止し
		stopDcdc_(resStopDcdc -> {
			if (resStopDcdc.succeeded()) {
				// DEAL オブジェクトを stop 処理し
				stopDeal_(resStopDeal -> {
					if (resStopDeal.succeeded()) {
						// DEAL オブジェクトを abort 処理する
						abortDeal_(resAbort -> {
							if (resAbort.succeeded()) {
								// deactivate ( 電圧リファレンス側を止める ) 処理に移行する
								new DealDeactivation(this).execute(completionHandler);
							} else {
								completionHandler.handle(resAbort);
							}
						});
					} else {
						completionHandler.handle(resStopDeal);
					}
				});
			} else {
				completionHandler.handle(resStopDcdc);
			}
		});
	}

	@Override protected void stopDeal_(Handler<AsyncResult<Void>> completionHandler) {
		// 現状の DEAL オブジェクトに応じて DEAL オブジェクトの状態を変更する
		if (Deal.isStarted(deal_)) {
			if (!Deal.isStopped(deal_)) {
				super.stopDeal_(completionHandler);
			} else {
				if (log.isInfoEnabled()) log.info("already stopped");
				completionHandler.handle(Future.succeededFuture());
			}
		} else {
			if (log.isInfoEnabled()) log.info("not yet started");
			completionHandler.handle(Future.succeededFuture());
		}
	}

	private void abortDeal_(Handler<AsyncResult<Void>> completionHandler) {
		DealUtil.abort(vertx_, deal_, referenceDateTimeString_(), reason_, resAbort -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resAbort, completionHandler));
	}

}
