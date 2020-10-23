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
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.user.util.Misc;
import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.ScenarioEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 他ユニットからのリクエストを処理する Verticle.
 * {@link User} Verticle から起動される.
 * @author OES Project
 */
public class MediatorRequestHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(MediatorRequestHandling.class);

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startMediatorRequestHandlingService_(resMediatorRequestHandling -> {
			if (resMediatorRequestHandling.succeeded()) {
				if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
				startFuture.complete();
			} else {
				startFuture.fail(resMediatorRequestHandling.cause());
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
	 * アドレス : {@link ServiceAddress.User#mediatorRequest()}
	 * 範囲 : ローカル
	 * 処理 : 他ユニットからのリクエストを処理する.
	 * メッセージボディ : リクエスト [{@link JsonObject}]
	 * メッセージヘッダ : なし
	 * レスポンス : リクエストに対するアクセプト情報 [{@link JsonObject}]
	 * 　　　　　   アクセプトしなかった場合は {@code null}
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startMediatorRequestHandlingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>localConsumer(ServiceAddress.User.mediatorRequest(), req -> {
			JsonObject request = req.body();
			if (log.isDebugEnabled()) log.debug("request received : " + request);
			if (request != null) {
				doHandleMediatorRequest_(request, resHandleMediatorRequest -> {
					if (resHandleMediatorRequest.succeeded()) {
						req.reply(resHandleMediatorRequest.result());
					} else {
						req.fail(-1, resHandleMediatorRequest.cause().getMessage());
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "request is null", req);
			}
		}).completionHandler(completionHandler);
	}

	private void doHandleMediatorRequest_(JsonObject request, Handler<AsyncResult<JsonObject>> completionHandler) {
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
									doHandleMediatorRequest__(request, completionHandler);
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
	private void doHandleMediatorRequest__(JsonObject request, Handler<AsyncResult<JsonObject>> completionHandler) {
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
							// リクエストを評価する
							ScenarioEvaluation.treatRequest(vertx, scenario, data, request, resEvaluation -> {
								if (resEvaluation.succeeded()) {
									JsonObject accept = resEvaluation.result();
									if (accept != null) {
										// 評価の結果アクセプトが作られたので
										String direction = accept.getString("type");
										// バッテリ容量をチェックし
										vertx.eventBus().<Boolean>send(ServiceAddress.Controller.batteryCapacityTesting(), direction, repBatteryCapacityTest -> {
											if (repBatteryCapacityTest.succeeded()) {
												if (repBatteryCapacityTest.result().body()) {
													// ご希望グリッド電圧があればリクエストに仕込み
													Float efficientGridVoltageV = Misc.efficientGridVoltageV_(data);
													if (efficientGridVoltageV != null) {
														accept.put("efficientGridVoltageV", efficientGridVoltageV);
													}
													accept.put("dateTime", dateTime);
													// アクセプトを返す
													completionHandler.handle(Future.succeededFuture(accept));
												} else {
													// バッテリ容量的にダメだった
													completionHandler.handle(Future.succeededFuture());
												}
											} else {
												completionHandler.handle(Future.failedFuture(repBatteryCapacityTest.cause()));
											}
										});
									} else {
										// アクセプトしません
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
