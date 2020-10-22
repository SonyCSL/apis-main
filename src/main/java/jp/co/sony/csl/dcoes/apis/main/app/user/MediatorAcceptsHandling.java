package jp.co.sony.csl.dcoes.apis.main.app.user;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.ScenarioEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 自ユニットからのリクエストに対し他ユニットから返されるアクセプト群を処理する Verticle.
 * {@link User} Verticle から起動される.
 * @author OES Project
 */
public class MediatorAcceptsHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(MediatorAcceptsHandling.class);

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startMediatorAcceptsHandlingService_(resMediatorAcceptsHandling -> {
			if (resMediatorAcceptsHandling.succeeded()) {
				if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
				startFuture.complete();
			} else {
				startFuture.fail(resMediatorAcceptsHandling.cause());
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
	 * アドレス : {@link ServiceAddress.User#mediatorAccepts()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットが発したリクエストに対するアクセプト群を処理する.
	 * メッセージボディ : リクエストおよびアクセプト群 [{@link JsonObject}]
	 * 　　　　　　　　   - {@code "request"} : リクエスト情報 [{@link JsonObject}}
	 * 　　　　　　　　   - {@code "accepts"} : アクセプト情報のリスト [{@link JsonArray}}
	 * メッセージヘッダ : なし
	 * レスポンス : 選択結果のアクセプト情報 [{@link JsonObject}]
	 * 　　　　　   アクセプトが選ばれなかった場合は {@code null}
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startMediatorAcceptsHandlingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>localConsumer(ServiceAddress.User.mediatorAccepts(), req -> {
			JsonObject values = req.body();
			JsonObject request = values.getJsonObject("request");
			JsonArray accepts = values.getJsonArray("accepts");
			if (log.isDebugEnabled()) log.debug("accepts received : " + accepts);
			if (request != null && accepts != null && !accepts.isEmpty()) {
				doHandleMediatorAccepts_(request, accepts, resHandleMediatorAccepts -> {
					if (resHandleMediatorAccepts.succeeded()) {
						req.reply(resHandleMediatorAccepts.result());
					} else {
						req.fail(-1, resHandleMediatorAccepts.cause().getMessage());
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "request is null and/or accepts is null or empty; request : " + request + ", accepts : " + accepts, req);
			}
		}).completionHandler(completionHandler);
	}

	private void doHandleMediatorAccepts_(JsonObject request, JsonArray accepts, Handler<AsyncResult<JsonObject>> completionHandler) {
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
									doHandleMediatorAccepts__(request, accepts, completionHandler);
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
	private void doHandleMediatorAccepts__(JsonObject request, JsonArray accepts_, Handler<AsyncResult<JsonObject>> completionHandler) {
		List<JsonObject> accepts = new ArrayList<>();
		for (Object obj : accepts_) {
			if (obj instanceof JsonObject) {
				accepts.add((JsonObject) obj);
			}
		}
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
							// アクセプト群を評価する
							ScenarioEvaluation.chooseAccept(vertx, scenario, data, request, accepts, resEvaluation -> {
								if (resEvaluation.succeeded()) {
									JsonObject accept = resEvaluation.result();
									if (accept != null) {
										// 評価の結果アクセプトが選ばれたので
										String direction = request.getString("type");
										// バッテリ容量をチェックし
										vertx.eventBus().<Boolean>send(ServiceAddress.Controller.batteryCapacityTesting(), direction, repBatteryCapacityTest -> {
											if (repBatteryCapacityTest.succeeded()) {
												if (repBatteryCapacityTest.result().body()) {
													// 選ばれたアクセプトを返す
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
										// アクセプトは選ばれませんでした
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
