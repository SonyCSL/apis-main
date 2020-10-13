package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * インタロックを管理する Verticle.
 * {@link Mediator} Verticle から起動される.
 * GridMaster インタロックおよび融通インタロックの獲得と開放とリセット.
 * @author OES Project
 */
public class Interlocking extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Interlocking.class);

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startGridMasterInterlockingService_(resGridMasterInterlocking -> {
			if (resGridMasterInterlocking.succeeded()) {
				startDealInterlockingService_(resDealInterlocking -> {
					if (resDealInterlocking.succeeded()) {
						startResetLocalService_(resResetLocal -> {
							if (resResetLocal.succeeded()) {
								startResetAllService_(resResetAll -> {
									if (resResetAll.succeeded()) {
										if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
										startFuture.complete();
									} else {
										startFuture.fail(resResetAll.cause());
									}
								});
							} else {
								startFuture.fail(resResetLocal.cause());
							}
						});
					} else {
						startFuture.fail(resDealInterlocking.cause());
					}
				});
			} else {
				startFuture.fail(resGridMasterInterlocking.cause());
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
	 * 同時融通可能数を取得する.
	 * グリッド電圧容量と融通ごとのグリッド電流から算出する.
	 * @param vertx vertx オブジェクト
	 * @return 同時融通可能数
	 */
	public static int dealInterlockCapacity(Vertx vertx) {
		Float gridCurrentCapacityA = HwConfigKeeping.gridCurrentCapacityA();
		Float dealGridCurrentA = PolicyKeeping.cache().getFloat("mediator", "deal", "gridCurrentA");
		if (gridCurrentCapacityA != null && dealGridCurrentA != null) {
			// HWCONFIG.gridCurrentCapacityA ÷ POLICY.mediator.deal.gridCurrentA
			return (int) (gridCurrentCapacityA / dealGridCurrentA);
		} else {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA + ", POLICY.mediator.deal.gridCurrentA : " + dealGridCurrentA);
			return 0;
		}
	}

	////

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#gridMasterInterlocking()}
	 * 範囲 : ローカル
	 * 処理 : GridMaster インタロックを確保/解放する.
	 * メッセージボディ : GridMaster ユニット ID [{@link String}]
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "command"}
	 * 　　　　　　　　     - {@code "acquire"} : インタロックを確保する
	 * 　　　　　　　　     - {@code "release"} : インタロックを解放する
	 * レスポンス : 自ユニットの ID [{@link String}]
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGridMasterInterlockingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>localConsumer(ServiceAddress.Mediator.gridMasterInterlocking(), req -> {
			String command = req.headers().get("command");
			String value = req.body();
			if (value != null) {
				if ("acquire".equalsIgnoreCase(command)) {
					InterlockUtil.lockGridMasterUnitId(vertx, value, false, resAcquire -> {
						if (resAcquire.succeeded()) {
							if (log.isInfoEnabled()) log.info("locked; gridMasterUnitId : " + value);
							req.reply(ApisConfig.unitId());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAcquire.cause(), req);
						}
					});
				} else if ("release".equalsIgnoreCase(command)) {
					InterlockUtil.unlockGridMasterUnitId(vertx, value, resRelease -> {
						if (resRelease.succeeded()) {
							if (log.isInfoEnabled()) log.info("unlocked; gridMasterUnitId : " + value);
							req.reply(ApisConfig.unitId());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), req);
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "unknown command : " + command, req);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "invalid request : " + req, req);
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#dealInterlocking(String)}
	 * 範囲 : グローバル
	 * 処理 : 融通インタロックを確保/解放する.
	 * メッセージボディ : 融通情報 [{@link JsonObject}]
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "command"}
	 * 　　　　　　　　     - {@code "acquire"} : インタロックを確保する
	 * 　　　　　　　　     - {@code "release"} : インタロックを解放する
	 * レスポンス : 自ユニットの ID [{@link String}]
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDealInterlockingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealInterlocking(ApisConfig.unitId()), req -> {
			String command = req.headers().get("command");
			JsonObject deal = req.body();
			if (deal != null) {
				String dealId = Deal.dealId(deal);
				if ("acquire".equalsIgnoreCase(command)) {
					// 先に融通インタロックを獲得してから
					InterlockUtil.lockDealId(vertx, dealId, dealInterlockCapacity(vertx), true, resAcquire -> {
						if (resAcquire.succeeded()) {
							// バッテリ容量を確保する
							DeliveryOptions options = new DeliveryOptions().addHeader("command", command);
							vertx.eventBus().<Boolean>send(ServiceAddress.Controller.batteryCapacityManaging(), deal, options, repBatteryCapacityAcquire -> {
								if (repBatteryCapacityAcquire.succeeded() && repBatteryCapacityAcquire.result().body()) {
									if (log.isInfoEnabled()) log.info("locked; dealId : " + dealId);
									req.reply(ApisConfig.unitId());
								} else {
									InterlockUtil.unlockDealId(vertx, dealId, resRelease -> {
										if (resRelease.succeeded()) {
											if (repBatteryCapacityAcquire.succeeded()) {
												req.fail(-1, "batteryCapacityManaging acquire failed");
											} else {
												ErrorExceptionUtil.reportIfNeedAndFail(vertx, repBatteryCapacityAcquire.cause(), req);
											}
										} else {
											ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), req);
										}
									});
								}
							});
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAcquire.cause(), req);
						}
					});
				} else if ("release".equalsIgnoreCase(command)) {
					InterlockUtil.unlockDealId(vertx, dealId, resRelease -> {
						if (resRelease.succeeded()) {
							InterlockUtil.getDealIds(vertx, resGet -> {
								if (resGet.succeeded()) {
									// TODO : ここでバッテリ容量管理を呼ぶか呼ばないか分岐があるとかイマイチ
									if (0 < resGet.result().size()) {
										// まだ融通が残っているのでバッテリ容量の確保を開放しない
										if (log.isInfoEnabled()) log.info("unlocked; dealId : " + dealId);
										req.reply(ApisConfig.unitId());
									} else {
										// 融通がなくなったのでバッテリ容量の確保を開放する
										DeliveryOptions options = new DeliveryOptions().addHeader("command", command);
										vertx.eventBus().<Boolean>send(ServiceAddress.Controller.batteryCapacityManaging(), deal, options, repBatteryCapacityRelease -> {
											if (repBatteryCapacityRelease.succeeded()) {
												if (log.isInfoEnabled()) log.info("unlocked; dealId : " + dealId);
												req.reply(ApisConfig.unitId());
											} else {
												ErrorExceptionUtil.reportIfNeedAndFail(vertx, repBatteryCapacityRelease.cause(), req);
											}
										});
									}
								} else {
									ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGet.cause(), req);
								}
							});
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), req);
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "unknown command : " + command, req);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "invalid request : " + req, req);
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#resetLocal()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットをリセットする.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startResetLocalService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.resetLocal(), req -> {
			InterlockUtil.resetExclusiveLock(vertx);
			Future<Void> resetDealIdFuture = Future.future();
			Future<Void> resetGridMasterUnitIdFuture = Future.future();
			doResetLocalDealId_(resetDealIdFuture);
			doResetLocalGridMasterUnitId_(resetGridMasterUnitIdFuture);
			CompositeFuture.all(resetDealIdFuture, resetGridMasterUnitIdFuture).setHandler(ar -> {
				if (ar.succeeded()) {
					req.reply(ApisConfig.unitId());
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx, ar.cause(), req);
				}
			});
		}).completionHandler(completionHandler);
	}
	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#resetAll()}
	 * 範囲 : グローバル
	 * 処理 : 全ユニットおよびクラスタに参加する全プログラムをリセットする.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startResetAllService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.resetAll(), req -> {
			InterlockUtil.resetExclusiveLock(vertx);
			Future<Void> resetDealIdFuture = Future.future();
			Future<Void> resetGridMasterUnitIdFuture = Future.future();
			InterlockUtil.resetDealId(vertx, resetDealIdFuture);
			InterlockUtil.resetGridMasterUnitId(vertx, resetGridMasterUnitIdFuture);
			CompositeFuture.all(resetDealIdFuture, resetGridMasterUnitIdFuture).setHandler(ar -> {
				if (ar.succeeded()) {
					req.reply(ApisConfig.unitId());
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx, ar.cause(), req);
				}
			});
		}).completionHandler(completionHandler);
	}
	private void doResetLocalDealId_(Handler<AsyncResult<Void>> completionHandler) {
		DealUtil.withUnitId(vertx, ApisConfig.unitId(), resDeals -> {
			if (resDeals.succeeded()) {
				if (resDeals.result().isEmpty()) {
					// 参加している融通がなければリセット
					InterlockUtil.resetDealId(vertx, resReset -> {
						if (resReset.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resReset.cause(), completionHandler);
						}
					});
				} else {
					// 参加している融通があったら何もしない
					completionHandler.handle(Future.succeededFuture());
				}
			} else {
				ErrorExceptionUtil.reportIfNeed(vertx, resDeals.cause());
			}
		});
	}
	private void doResetLocalGridMasterUnitId_(Handler<AsyncResult<Void>> completionHandler) {
		InterlockUtil.getGridMasterUnitId(vertx, resGet -> {
			if (resGet.succeeded()) {
				String value = resGet.result();
				if (ApisConfig.unitId().equals(value)) {
					// GridMaster インタロックの値が自ユニットの ID だったらアンロック
					InterlockUtil.unlockGridMasterUnitId(vertx, value, resRelease -> {
						if (resRelease.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), completionHandler);
						}
					});
				} else {
					// GridMaster インタロックの値が自ユニットの ID じゃなかったら
					vertx.eventBus().<String>send(ServiceAddress.GridMaster.helo(), null, repHeloGridMaster -> {
						if (repHeloGridMaster.succeeded()) {
							// どこかに GridMaster が存在したら何もしない
							completionHandler.handle(Future.succeededFuture());
						} else if (ReplyFailureUtil.isNoHandlers(repHeloGridMaster)) {
							// どこにも GridMaster が存在しなかったらリセット
							InterlockUtil.resetGridMasterUnitId(vertx, resReset -> {
								if (resReset.succeeded()) {
									completionHandler.handle(Future.succeededFuture());
								} else {
									ErrorExceptionUtil.reportIfNeedAndFail(vertx, resReset.cause(), completionHandler);
								}
							});
						} else {
							completionHandler.handle(Future.succeededFuture());
						}
					});
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGet.cause(), completionHandler);
			}
		});
	}

}
