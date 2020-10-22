package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;

/**
 * 停止処理を実行する融通処理の親玉.
 * @author OES Project
 */
public abstract class AbstractStoppableDealExecution extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(AbstractStoppableDealExecution.class);

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution} 開始時にコピーしたものが渡される.
	 * @param deal 処理対象 DEAL オブジェクト
	 * @param otherDeals 同時に存在している他の DEAL オブジェクトのリスト
	 */
	public AbstractStoppableDealExecution(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}
	/**
	 * インスタンスを生成する.
	 * 他の {@link AbstractDealExecution} の内部状態をそのまま受け継ぐため初期化不要.
	 * @param other 他の abstractdealexecution オブジェクト
	 */
	public AbstractStoppableDealExecution(AbstractDealExecution other) {
		super(other);
	}

	/**
	 * 融通を物理的に "stop" する処理.
	 * 電圧リファレンス側でない方のユニットのデバイスを停止するか電流値を変更する.
	 * completionHandler の {@link AsyncResult#result()} で制御後のデバイス制御状態を受け取る.
	 * @param completionHandler the completion handler
	 */
	protected void stopDcdc_(Handler<AsyncResult<Void>> completionHandler) {
		// DEAL オブジェクト中に dcdc/failBeforeStop = true があったら fail させる
		if (testFeature_failIfNeed_("dcdc", "failBeforeStop", completionHandler)) return;
		if (canStopDcdc_()) {
			// 実際に止めてよければ
			if (log.isInfoEnabled()) log.info("stop slave side unit");
			// 停止制御を実行する
			// 停止が成功した場合に DEAL オブジェクト中に dcdc/failAfterStop = true があったら fail させる
			controlSlaveSideUnitDcdc_(DDCon.Mode.WAIT.name(), null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterStop", completionHandler));
		} else {
			// 止められない状態なら残りの融通の融通電流を合計し電流値を変更する
			float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(slaveSideUnitId_()));
			if (log.isInfoEnabled()) log.info("dig : " + JsonObjectUtil.getFloat(slaveSideUnitData_(), "dcdc", "param", "dig") + " -> " + newDig);
			JsonObject params = new JsonObject().put("gridCurrentA", newDig);
			// 電流値の変更が成功した場合に DEAL オブジェクト中に dcdc/failAfterStop = true があったら fail させる
			controlSlaveSideUnitDcdc_("current", params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterStop", completionHandler));
		}
	}
	/**
	 * 融通を物理的に "stop" できるかを取得する.
	 * 電圧リファレンス側でない方のユニットが他に参加している融通のうち一つでも "電圧リファレンス側でない方のユニットが起動中" なら {@code false}.
	 * @return {@code true} なら "stop" 可
	 */
	private boolean canStopDcdc_() {
		for (JsonObject aDeal : otherDeals_(slaveSideUnitId_())) {
			if (Deal.slaveSideUnitMustBeActive(aDeal)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 融通を状態的に "stop" する処理.
	 * 当該 DEAL オブジェクトを "stop" 状態にする.
	 * @param completionHandler the completion handler
	 */
	protected void stopDeal_(Handler<AsyncResult<Void>> completionHandler) {
		DealUtil.stop(vertx_, deal_, referenceDateTimeString_(), resStop -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resStop, completionHandler));
	}

}
