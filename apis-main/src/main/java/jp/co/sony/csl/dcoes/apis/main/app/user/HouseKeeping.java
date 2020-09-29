package jp.co.sony.csl.dcoes.apis.main.app.user;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.user.util.Misc;
import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.ScenarioEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 自ユニットの状態を監視し必要に応じてリクエストを発する Verticle.
 * {@link User} Verticle から起動される.
 * @author OES Project
 */
public class HouseKeeping extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(HouseKeeping.class);

	/**
	 * 状態監視周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_HOUSE_KEEPING_PERIOD_MSEC = 60000L;

	private long houseKeepingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * 定期的に自ユニットの状態をチェックし必要に応じてリクエストを発するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		houseKeepingTimerHandler_(0L);
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
	 * 状態監視タイマ設定.
	 * 待ち時間は {@code POLICY.user.houseKeepingPeriodMsec} ( デフォルト値 {@link #DEFAULT_HOUSE_KEEPING_PERIOD_MSEC} ).
	 */
	private void setHouseKeepingTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_HOUSE_KEEPING_PERIOD_MSEC, "user", "houseKeepingPeriodMsec");
		setHouseKeepingTimer_(delay);
	}
	/**
	 * 状態監視タイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setHouseKeepingTimer_(long delay) {
		houseKeepingTimerId_ = vertx.setTimer(delay, this::houseKeepingTimerHandler_);
	}
	/**
	 * 状態監視タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void houseKeepingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != houseKeepingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", houseKeepingTimerId_ : " + houseKeepingTimerId_);
			return;
		}
		if (!StateHandling.isInOperation()) {
			// 定常稼働状態でなければスルー
			setHouseKeepingTimer_();
		} else {
			doHouseKeeping_(res -> {
				setHouseKeepingTimer_();
			});
		}
	}

	private void doHouseKeeping_(Handler<AsyncResult<Void>> completionHandler) {
		if (ErrorCollection.hasErrors()) {
			// ローカルエラー発生中なら何もしない
			if (log.isInfoEnabled()) log.info("this unit has errors : " + ErrorCollection.cache.jsonObject());
			completionHandler.handle(Future.succeededFuture());
		} else {
			vertx.eventBus().<Boolean>send(ServiceAddress.GridMaster.errorTesting(), null, repGlobalErrors -> {
				if (repGlobalErrors.succeeded()) {
					Boolean hasGlobalErrors = repGlobalErrors.result().body();
					if (hasGlobalErrors != null && hasGlobalErrors) {
						// グローバルエラー発生中なら何もしない
						if (log.isInfoEnabled()) log.info("global error exists");
						completionHandler.handle(Future.succeededFuture());
					} else {
						StateHandling.operationMode(vertx, resOperationMode -> {
							if (resOperationMode.succeeded()) {
								String operationMode = resOperationMode.result();
								if ("autonomous".equals(operationMode)) {
									// 融通モードが autonomous
									doHouseKeeping__(completionHandler);
								} else {
									// 融通モードが autonomous 以外なら何もしない
									if (log.isInfoEnabled()) log.info("operationMode is not autonomous : " + operationMode);
									completionHandler.handle(Future.succeededFuture());
								}
							} else {
								completionHandler.handle(Future.failedFuture(resOperationMode.cause()));
							}
						});
					}
				} else {
					if (ReplyFailureUtil.isRecipientFailure(repGlobalErrors)) {
						completionHandler.handle(Future.failedFuture(repGlobalErrors.cause()));
					} else if (ReplyFailureUtil.isNoHandlers(repGlobalErrors)) {
						ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", repGlobalErrors.cause(), completionHandler);
					} else if (ReplyFailureUtil.isTimeout(repGlobalErrors)) {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", repGlobalErrors.cause(), completionHandler);
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repGlobalErrors.cause(), completionHandler);
					}
				}
			});
		}
	}
	private void doHouseKeeping__(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>send(ServiceAddress.Controller.unitData(), null, repData -> {
			if (repData.succeeded()) {
				JsonObject data = repData.result().body();
				Integer dealInterlockCapacity = JsonObjectUtil.getInteger(data, "apis", "deal_interlock_capacity");
				JsonArray dealIds = JsonObjectUtil.getJsonArray(data, "apis", "deal_id_list");
				if (dealInterlockCapacity != null && 0 < dealInterlockCapacity && (dealIds == null || dealIds.size() < dealInterlockCapacity)) {
					// 融通インタロック数が融通可能数未満なら
					String dateTime = data.getString("time");
					// 現在時刻に対応する SCENARIO を取得して
					vertx.eventBus().<JsonObject>send(ServiceAddress.User.scenario(), dateTime, repScenario -> {
						if (repScenario.succeeded()) {
							JsonObject scenario = repScenario.result().body();
							// 自ユニットの状況 ( data ) を評価する
							ScenarioEvaluation.checkStatus(vertx, scenario, data, resEvaluation -> {
								if (resEvaluation.succeeded()) {
									JsonObject request = resEvaluation.result();
									if (request != null) {
										// 評価の結果リクエストが作られたので
										String direction = request.getString("type");
										// バッテリ容量をチェックし
										vertx.eventBus().<Boolean>send(ServiceAddress.Controller.batteryCapacityTesting(), direction, repBatteryCapacityTest -> {
											if (repBatteryCapacityTest.succeeded()) {
												if (repBatteryCapacityTest.result().body()) {
													// ご希望グリッド電圧があればリクエストに仕込み
													Float efficientGridVoltageV = Misc.efficientGridVoltageV_(data);
													if (efficientGridVoltageV != null) {
														request.put("efficientGridVoltageV", efficientGridVoltageV);
													}
													request.put("dateTime", dateTime);
													// リクエストを発する
													vertx.eventBus().send(ServiceAddress.Mediator.internalRequest(), request);
												}
												completionHandler.handle(Future.succeededFuture());
											} else {
												completionHandler.handle(Future.failedFuture(repBatteryCapacityTest.cause()));
											}
										});
									} else {
										// リクエストしません
										completionHandler.handle(Future.succeededFuture());
									}
								} else {
									completionHandler.handle(Future.failedFuture(resEvaluation.cause()));
								}
							});
						} else {
							if (ReplyFailureUtil.isRecipientFailure(repScenario)) {
								completionHandler.handle(Future.failedFuture(repScenario.cause()));
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repScenario.cause(), completionHandler);
							}
						}
					});
				} else {
					// 同時融通数いっぱい
					if (log.isInfoEnabled()) log.info("unit is busy; dealInterlockCapacity : " + dealInterlockCapacity + ", dealIds : " + dealIds);
					completionHandler.handle(Future.succeededFuture());
				}
			} else {
				if (ReplyFailureUtil.isRecipientFailure(repData)) {
					completionHandler.handle(Future.failedFuture(repData.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repData.cause(), completionHandler);
				}
			}
		});
	}

}
