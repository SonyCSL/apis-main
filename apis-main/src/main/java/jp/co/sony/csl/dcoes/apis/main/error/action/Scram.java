package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * エラー処理の実クラス.
 * クラスタ内の全融通を緊急停止する.
 * @author OES Project
 */
public class Scram extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(Scram.class);

	/**
	 * 一度目の SCRAM 命令 ( 電圧リファレンスはスルーする ) 送信から二度目 ( 電圧リファレンスも反応する ) までの待ち時間のデフォルト値.
	 * 値は {@value}.
	 */
	public static final Long DEFAULT_SCRAM_VOLTAGE_REFERENCE_DELAY_MSEC = 5000L;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} あるいは {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling} でコピーしたものが渡される.
	 * @param logMessages エラー処理で記録するログメッセージのリスト
	 */
	public Scram(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		DeliveryOptions options = new DeliveryOptions();
//		options.addHeader("gridMasterUnitId", VertxConfig.unitId());
		if (log.isInfoEnabled()) log.info("publishing SCRAM message to all units ( excluding voltage reference ) ...");
		// まず "電圧リファレンスは除く" オプション ( excludeVoltageReference = true ) で SCRAM 命令を publish
		DeliveryOptions excludeVoltageReferenceOptions = new DeliveryOptions(options).addHeader("excludeVoltageReference", Boolean.TRUE.toString());
		vertx_.eventBus().publish(ServiceAddress.Controller.scram(), null, excludeVoltageReferenceOptions);
		// POLICY.controller.scramVoltageReferenceDelayMsec ( デフォルト DEFAULT_SCRAM_VOLTAGE_REFERENCE_DELAY_MSEC ) のタイマを仕込む
		vertx_.setTimer(JsonObjectUtil.getLong(policy_, DEFAULT_SCRAM_VOLTAGE_REFERENCE_DELAY_MSEC, "controller", "scramVoltageReferenceDelayMsec"), h -> {
			if (log.isInfoEnabled()) log.info("publishing SCRAM message to all units ( including voltage reference ) ...");
			// "電圧リファレンスも含む" オプション ( excludeVoltageReference = false ) で SCRAM 命令を publish
			DeliveryOptions includeVoltageReferenceOptions = new DeliveryOptions(options).addHeader("excludeVoltageReference", Boolean.FALSE.toString());
			vertx_.eventBus().publish(ServiceAddress.Controller.scram(), null, includeVoltageReferenceOptions);
			if (log.isInfoEnabled()) log.info("SCRAM all deals ...");
			// 共有メモリ上の全 DEAL オブジェクトを SCRAM 処理する
			DealUtil.all(vertx_, resAll -> {
				if (resAll.succeeded()) {
					List<JsonObject> deals = resAll.result();
					new DealScramming_(deals).doLoop_(completionHandler);
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx_, resAll.cause(), completionHandler);
				}
			});
		});
	}
	/**
	 * DEAL オブジェクトを SCRAM 処理するクラス.
	 * @author OES Project
	 */
	private class DealScramming_ {
		private List<JsonObject> dealsForLoop_;
		/**
		 * インスタンス生成.
		 * @param deals 処理対象 DEAL オブジェクトのリスト
		 */
		private DealScramming_(List<JsonObject> deals) {
			dealsForLoop_ = new ArrayList<JsonObject>(deals);
		}
		private void doLoop_(Handler<AsyncResult<Void>> completionHandler) {
			if (dealsForLoop_.isEmpty()) {
				if (log.isInfoEnabled()) log.info("done");
				completionHandler.handle(Future.succeededFuture());
			} else {
				JsonObject aDeal = dealsForLoop_.remove(0);
				// DEAL オブジェクトを SCRAM 済み状態にする
				DealUtil.scram(vertx_, aDeal, DataAcquisition.cache.getString("time"), logMessages_.encode(), resScram -> {
					if (resScram.succeeded()) {
						// 共有メモリから融通を削除する
						vertx_.eventBus().send(ServiceAddress.Mediator.dealDisposition(), Deal.dealId(aDeal), repDealDisposition -> {
							if (repDealDisposition.succeeded()) {
								// 成功ならすることはない
							} else {
								if (ReplyFailureUtil.isRecipientFailure(repDealDisposition)) {
									// 失敗しても向こう側の処理での失敗ならここで何もすることはない
								} else {
									// それ以外の失敗はここでエラーを出しておく
									ErrorUtil.report(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, repDealDisposition.cause());
								}
							}
							doLoop_(completionHandler);
						});
					} else {
						ErrorExceptionUtil.reportIfNeed(vertx_, resScram.cause());
						doLoop_(completionHandler);
					}
				});
			}
		}
	}

}
