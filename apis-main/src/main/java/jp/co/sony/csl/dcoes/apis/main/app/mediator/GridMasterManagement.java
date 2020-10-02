package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.GridMaster;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

/**
 * GridMaster を管理する Verticle.
 * {@link Mediator} Verticle から起動される.
 * GridMaster の起動と停止および移動.
 * 定期的な存在確認と不在時の対応.
 * @author OES Project
 */
public class GridMasterManagement extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(GridMasterManagement.class);

	/**
	 * GridMaster インタロックが不整合の場合に再確認するまでの待ち時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_INTERLOCK_INCONSISTENCY_RETRY_WAIT_MSEC = 2000L;
	/**
	 * GridMaster が不在の場合に再確認するまでの待ち時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_ABSENCE_ENSURE_WAIT_MSEC = 5000L;

	/**
	 * 一発だけのエラーはスルーするためのキャッシュ.
	 */
	private static final JsonObjectWrapper errors = new JsonObjectWrapper();

	private long gridMasterWatchingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 定期的に GridMaster の存在を確認するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startGridMasterActivationService_(resGridMasterActivation -> {
			if (resGridMasterActivation.succeeded()) {
				startGridMasterDeactivationService_(resGridMasterDeactivation -> {
					if (resGridMasterDeactivation.succeeded()) {
						startGridMasterEnsuringService_(resGridMasterEnsuring -> {
							if (resGridMasterEnsuring.succeeded()) {
								startResetLocalService_(resResetLocal -> {
									if (resResetLocal.succeeded()) {
										startResetAllService_(resResetAll -> {
											if (resResetAll.succeeded()) {
												gridMasterWatchingTimerHandler_(0L);
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
								startFuture.fail(resGridMasterEnsuring.cause());
							}
						});
					} else {
						startFuture.fail(resGridMasterDeactivation.cause());
					}
				});
			} else {
				startFuture.fail(resGridMasterActivation.cause());
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

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#gridMasterActivation(String)}
	 * 範囲 : グローバル
	 * 処理 : GridMaster を起動する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGridMasterActivationService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Mediator.gridMasterActivation(ApisConfig.unitId()), req -> {
			if (!StateHandling.isStopping()) {
				// GridMaster メインループの実行と排他制御
				MainLoop.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock1 -> {
					if (resExclusiveLock1.succeeded()) {
						LocalExclusiveLock.Lock lock1 = resExclusiveLock1.result();
						// 融通情報の管理まわりの処理との排他制御は不要になった
//						DealManagement.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock2 -> {
//							if (resExclusiveLock2.succeeded()) {
//								LocalExclusiveLock.Lock lock2 = resExclusiveLock2.result();
								doGridMasterActivationWithExclusiveLock_(resDoGridMasterActivationWithExclusiveLock -> {
//									lock2.release();
									lock1.release();
									if (resDoGridMasterActivationWithExclusiveLock.succeeded()) {
										req.reply(ApisConfig.unitId());
									} else {
										req.fail(-1, resDoGridMasterActivationWithExclusiveLock.cause().getMessage());
									}
								});
//							} else {
//								lock1.release();
//								ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock2.cause(), req);
//							}
//						});
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock1.cause(), req);
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "this unit is stopping ...", req);
			}
		}).completionHandler(completionHandler);
	}
	private void doGridMasterActivationWithExclusiveLock_(Handler<AsyncResult<Void>> completionHandler) {
		DeliveryOptions acquireOptions = new DeliveryOptions().addHeader("command", "acquire");
		vertx.eventBus().send(ServiceAddress.Mediator.gridMasterInterlocking(), ApisConfig.unitId(), acquireOptions, repAcquire -> {
			if (repAcquire.succeeded()) {
				vertx.deployVerticle(new GridMaster(), resDeployGridMaster -> {
					if (resDeployGridMaster.succeeded()) {
						completionHandler.handle(Future.succeededFuture());
					} else {
						log.error(resDeployGridMaster.cause());
						System.err.println(resDeployGridMaster.cause());
						DeliveryOptions releaseOptions = new DeliveryOptions().addHeader("command", "release");
						vertx.eventBus().send(ServiceAddress.Mediator.gridMasterInterlocking(), ApisConfig.unitId(), releaseOptions, repRelease -> {
							if (repRelease.succeeded()) {
								// nop
							} else {
								if (ReplyFailureUtil.isRecipientFailure(repRelease)) {
									// nop
								} else {
									ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, repRelease.cause());
								}
							}
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resDeployGridMaster.cause(), completionHandler);
						});
					}
				});
			} else {
				if (ReplyFailureUtil.isRecipientFailure(repAcquire)) {
					completionHandler.handle(Future.failedFuture(repAcquire.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, repAcquire.cause(), completionHandler);
				}
			}
		});
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#gridMasterDeactivation(String)}
	 * 範囲 : グローバル
	 * 処理 : GridMaster を停止する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGridMasterDeactivationService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Mediator.gridMasterDeactivation(ApisConfig.unitId()), req -> {
			// GridMaster メインループの実行と排他制御
			MainLoop.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock1 -> {
				if (resExclusiveLock1.succeeded()) {
					LocalExclusiveLock.Lock lock1 = resExclusiveLock1.result();
					// 融通情報の管理まわりの処理との排他制御は不要になった
//					DealManagement.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock2 -> {
//						if (resExclusiveLock2.succeeded()) {
//							LocalExclusiveLock.Lock lock2 = resExclusiveLock2.result();
							doGridMasterDeactivationWithExclusiveLock_(resDoGridMasterDeactivationWithExclusiveLock -> {
//								lock2.release();
								lock1.release();
								if (resDoGridMasterDeactivationWithExclusiveLock.succeeded()) {
									req.reply(ApisConfig.unitId());
								} else {
									req.fail(-1, resDoGridMasterDeactivationWithExclusiveLock.cause().getMessage());
								}
							});
//						} else {
//							lock1.release();
//							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock2.cause(), req);
//						}
//					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock1.cause(), req);
				}
			});
		}).completionHandler(completionHandler);
	}
	private void doGridMasterDeactivationWithExclusiveLock_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().send(ServiceAddress.GridMaster.undeploymentLocal(), null, repUndeploy -> {
			if (repUndeploy.succeeded()) {
				DeliveryOptions releaseOptions = new DeliveryOptions().addHeader("command", "release");
				vertx.eventBus().send(ServiceAddress.Mediator.gridMasterInterlocking(), ApisConfig.unitId(), releaseOptions, repRelease -> {
					if (repRelease.succeeded()) {
						completionHandler.handle(Future.succeededFuture());
					} else {
						if (ReplyFailureUtil.isRecipientFailure(repRelease)) {
							completionHandler.handle(Future.failedFuture(repRelease.cause()));
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, repRelease.cause(), completionHandler);
						}
					}
				});
			} else {
				if (ReplyFailureUtil.isNoHandlers(repUndeploy)) {
					completionHandler.handle(Future.succeededFuture());
				} else if (ReplyFailureUtil.isRecipientFailure(repUndeploy)) {
					completionHandler.handle(Future.failedFuture(repUndeploy.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, repUndeploy.cause(), completionHandler);
				}
			}
		});
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#gridMasterEnsuring()}
	 * 範囲 : ローカル
	 * 処理 : GridMaster の存在メンテナンス.
	 * 　　   存在の確認.
	 * 　　   存在場所の確認.
	 * 　　   適切なユニットへの移動.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : GridMaster が存在すユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGridMasterEnsuringService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Mediator.gridMasterEnsuring(), req -> {
			String properGridMasterUnitId = properGridMasterUnitId_();
			// まず GridMaster がいるか, いるならどこかを確認する
			vertx.eventBus().<String>send(ServiceAddress.GridMaster.helo(), null, repHeloGridMaster -> {
				if (repHeloGridMaster.succeeded()) {
					// どこかに GridMaster が存在する
					String gridMasterUnitId = repHeloGridMaster.result().body();
					if (log.isInfoEnabled()) log.info("gridMasterUnitId : " + gridMasterUnitId);
					if (properGridMasterUnitId == null || properGridMasterUnitId.equals(gridMasterUnitId)) {
						// GridMaster がいるべきユニットが決まっていない ( どこでもよい ) か既にいるべきユニットにいる
						// → そのままで OK
						if (log.isInfoEnabled()) log.info("no need to move");
						// GridMaster インタロックが正しいか確認する
						InterlockUtil.getGridMasterUnitId(vertx, resInterlockGridMasterUnitId -> {
							if (resInterlockGridMasterUnitId.succeeded()) {
								String interlockGridMasterUnitId = resInterlockGridMasterUnitId.result();
								if (gridMasterUnitId.equals(interlockGridMasterUnitId)) {
									// OK なので一回目の不整合をクリア
									errors.remove("gridMasterEnsuring");
									req.reply(gridMasterUnitId);
								} else {
									ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "gridMaster interlock inconsistency; gridMasterUnitId : " + gridMasterUnitId + ", interlockGridMasterUnitId : " + interlockGridMasterUnitId, req);
								}
							} else {
								ErrorExceptionUtil.reportIfNeedAndFail(vertx, resInterlockGridMasterUnitId.cause(), req);
							}
						});
					} else {
						// いるべきユニットではないところにいる
						if (log.isInfoEnabled()) log.info("should move GridMaster");
						// まず今のを落とす
						vertx.eventBus().<String>send(ServiceAddress.Mediator.gridMasterDeactivation(gridMasterUnitId), null, repGridMasterDeactivation -> {
							if (repGridMasterDeactivation.succeeded()) {
								// 正しいところに立てる
								activateGridMaster_(properGridMasterUnitId, resActivateGridMaster -> {
									if (resActivateGridMaster.succeeded()) {
										// OK なので一回目の不整合をクリア
										errors.remove("gridMasterEnsuring");
										req.reply(properGridMasterUnitId);
									} else {
										req.fail(-1, resActivateGridMaster.cause().getMessage());
									}
								});
							} else {
								if (ReplyFailureUtil.isRecipientFailure(repGridMasterDeactivation)) {
									req.fail(-1, repGridMasterDeactivation.cause().getMessage());
								} else if (ReplyFailureUtil.isNoHandlers(repGridMasterDeactivation)) {
									ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, repGridMasterDeactivation.cause(), req);
								} else {
									ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, repGridMasterDeactivation.cause(), req);
								}
							}
						});
					}
				} else if (ReplyFailureUtil.isNoHandlers(repHeloGridMaster)) {
					// どこにもいない
					if (log.isInfoEnabled()) log.info("no GridMaster exists");
					// GridMaster インタロックを確認する
					InterlockUtil.getGridMasterUnitId(vertx, resInterlockGridMasterUnitId -> {
						if (resInterlockGridMasterUnitId.succeeded()) {
							String interlockGridMasterUnitId = resInterlockGridMasterUnitId.result();
							if (null == interlockGridMasterUnitId) {
								// インタロックに不整合がなければ
								String properGridMasterUnitId2 = (properGridMasterUnitId != null) ? properGridMasterUnitId : ApisConfig.unitId();
								// 正しいところに立てる
								activateGridMaster_(properGridMasterUnitId2, resActivateGridMaster -> {
									if (resActivateGridMaster.succeeded()) {
										// OK なので一回目の不整合をクリア
										errors.remove("gridMasterEnsuring");
										req.reply(properGridMasterUnitId2);
									} else {
										req.fail(-1, resActivateGridMaster.cause().getMessage());
									}
								});
							} else {
								// インタロックに不整合あり
								// エラー発生をひとまず記録する
								errors.add(Boolean.FALSE, "gridMasterEnsuring", "ERROR_1");
								if (1 < errors.getJsonArray("gridMasterEnsuring", "ERROR_1").size()) {
									// 一発目じゃないならエラーにする
									ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "gridMaster interlock inconsistency; no GridMaster exists, interlockGridMasterUnitId : " + interlockGridMasterUnitId, req);
								} else {
									// 一発目ならスルーして...
									ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "gridMaster interlock inconsistency; no GridMaster exists, interlockGridMasterUnitId : " + interlockGridMasterUnitId);
									Long retryWaitMsec = PolicyKeeping.cache().getLong(DEFAULT_INTERLOCK_INCONSISTENCY_RETRY_WAIT_MSEC, "gridMaster", "gridMasterEnsuring", "interlockInconsistency", "retryWaitMsec");
									// ちょっと待ってもう一度この処理をよぶ
									vertx.setTimer(retryWaitMsec, timerId -> {
										vertx.eventBus().<String>send(ServiceAddress.Mediator.gridMasterEnsuring(), null, repAgain -> {
											if (repAgain.succeeded()) {
												// 成功したら一回目の不整合をクリア
												// TODO : もう一度呼んだ処理の中でクリアされるのでは？だから不要では？
												errors.remove("gridMasterEnsuring");
												req.reply(repAgain.result().body());
											} else {
												req.fail(-1, repAgain.cause().getMessage());
											}
										});
									});
								}
							}
						} else {
							req.fail(-1, resInterlockGridMasterUnitId.cause().getMessage());
						}
					});
				} else if (ReplyFailureUtil.isRecipientFailure(repHeloGridMaster)) {
					req.fail(-1, repHeloGridMaster.cause().getMessage());
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, repHeloGridMaster.cause(), req);
				}
			});
		}).completionHandler(completionHandler);
	}
	private void activateGridMaster_(String unitId, Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>send(ServiceAddress.Mediator.gridMasterActivation(unitId), null, repGridMasterActivation -> {
			if (repGridMasterActivation.succeeded()) {
				String newGridMasterUnitId = repGridMasterActivation.result().body();
				if (unitId.equals(newGridMasterUnitId)) {
					if (log.isInfoEnabled()) log.info("newGridMasterUnitId : " + newGridMasterUnitId);
					completionHandler.handle(Future.succeededFuture());
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "invalid newGridMasterUnitId : " + newGridMasterUnitId + ", should be : " + unitId, completionHandler);
				}
			} else {
				if (ReplyFailureUtil.isRecipientFailure(repGridMasterActivation)) {
					completionHandler.handle(Future.failedFuture(repGridMasterActivation.cause()));
				} else if (ReplyFailureUtil.isNoHandlers(repGridMasterActivation)) {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, repGridMasterActivation.cause(), completionHandler);
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, repGridMasterActivation.cause(), completionHandler);
				}
			}
		});
	}
	/**
	 * GridMaster が立っているべきユニットの ID を取得する.
	 * @return GridMaster が立っているべきユニットの ID.
	 *         {@code null} ならどこでもよい.
	 */
	private String properGridMasterUnitId_() {
		JsonObject policy = PolicyKeeping.cache().jsonObject();
		String gridMasterSelectionStrategy = Policy.gridMasterSelectionStrategy(policy);
		if ("anywhere".equals(gridMasterSelectionStrategy)) {
			return null;
		} else if ("fixed".equals(gridMasterSelectionStrategy)) {
			return Policy.gridMasterSelectionFixedUnitId(policy);
		} else {
			// 残る gridMasterSelectionStrategy は "voltageReferenceUnit" なので電圧リファレンスユニットの ID を探す
			String voltageReferenceUnitId = DealExecution.voltageReferenceUnitId();
			if (voltageReferenceUnitId == null) {
				if (log.isInfoEnabled()) log.info("no voltage reference unit");
			}
			return voltageReferenceUnitId;
		}
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
			doReset_(req);
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
			doReset_(req);
		}).completionHandler(completionHandler);
	}
	private void doReset_(Message<?> message) {
		vertx.eventBus().publish(ServiceAddress.GridMaster.undeploymentLocal(), null);
		MainLoop.resetExclusiveLock(vertx);
		message.reply(ApisConfig.unitId());
	}

	/**
	 * GridMaster の存在確認タイマ設定.
	 * 待ち時間は {@code POLICY.gridMaster.mainLoopPeriodMsec} ( デフォルト値 {@link MainLoop#DEFAULT_MAIN_LOOP_PERIOD_MSEC} ) をベースにユニット数を考慮しランダムに算出.
	 */
	private void setGridMasterWatchingTimer_() {
		Long mainLoopPeriodMsec = PolicyKeeping.cache().getLong(MainLoop.DEFAULT_MAIN_LOOP_PERIOD_MSEC, "gridMaster", "mainLoopPeriodMsec");
		int numberOfMembers = PolicyKeeping.numberOfMembers();
		long delay = (long) (mainLoopPeriodMsec * numberOfMembers * 2L * Math.random());
		// タイマの待ち時間 = ベースの周期 x ユニット数 x 2 x 乱数 ( 0 〜 1 )
		// → つまり全ユニットが起動している状況でクラスタ全体でベース周期くらいの頻度になる計算
		if (delay == 0L) delay = mainLoopPeriodMsec;
		setGridMasterWatchingTimer_(delay);
	}
	/**
	 * GridMaster の存在確認タイマ設定.
	 * @param delay 待ち時間 [ms]
	 */
	private void setGridMasterWatchingTimer_(long delay) {
		gridMasterWatchingTimerId_ = vertx.setTimer(delay, this::gridMasterWatchingTimerHandler_);
	}
	/**
	 * GridMaster の存在確認タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void gridMasterWatchingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != gridMasterWatchingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", gridMasterWatchingTimerId_ : " + gridMasterWatchingTimerId_);
			return;
		}
		if (!StateHandling.isInOperation()) {
			// 本稼働中でなければスルー
			setGridMasterWatchingTimer_();
		} else {
			vertx.eventBus().<String>send(ServiceAddress.GridMaster.helo(), null, repHeloGridMaster -> {
				if (repHeloGridMaster.succeeded()) {
					// どこかに GridMaster がいたらそれで OK
				} else if (ReplyFailureUtil.isNoHandlers(repHeloGridMaster)) {
					// GridMaster がいなかったら
					Long ensureWaitMsec = PolicyKeeping.cache().getLong(DEFAULT_ABSENCE_ENSURE_WAIT_MSEC, "gridMaster", "gridMasterWatching", "absence", "ensureWaitMsec");
					long delay = (long) (ensureWaitMsec + ensureWaitMsec * Math.random());
					// タイマの待ち時間 = ベースの時間 x 2 x 乱数 ( 0 〜 1 )
					if (delay == 0L) delay = ensureWaitMsec;
					if (log.isInfoEnabled()) log.info("no GridMaster exists, wait " + delay + "ms and check again ...");
					vertx.setTimer(delay, v -> {
						// しばらく待ったあと GridMaster 存在メンテナンス処理を叩く
						vertx.eventBus().send(ServiceAddress.Mediator.gridMasterEnsuring(), null);
					});
				} else if (ReplyFailureUtil.isRecipientFailure(repHeloGridMaster)) {
					// nop
				} else {
					ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, repHeloGridMaster.cause());
				}
				setGridMasterWatchingTimer_();
			});
		}
	}

}
