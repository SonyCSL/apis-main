package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * データ応答サービスの親玉 Verticle.
 * {@link Controller} Verticle から起動される.
 * システムの種類に応じて以下の種類がある.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataResponding}
 * @author OES Project
 */
public abstract class DataResponding extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DataResponding.class);

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startInternalUnitDataService_(resInternalUnitData -> {
			if (resInternalUnitData.succeeded()) {
				startExternalUnitDataService_(resExternalUnitData -> {
					if (resExternalUnitData.succeeded()) {
						startInternalUnitDeviceStatusService_(resInternalUnitDeviceStatus -> {
							if (resInternalUnitDeviceStatus.succeeded()) {
								startExternalUnitDeviceStatusService_(resExternalUnitDeviceStatus -> {
									if (resExternalUnitDeviceStatus.succeeded()) {
										startUnitDatasService_(resUnitDatas -> {
											if (resUnitDatas.succeeded()) {
												if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
												startFuture.complete();
											} else {
												startFuture.fail(resUnitDatas.cause());
											}
										});
									} else {
										startFuture.fail(resExternalUnitDeviceStatus.cause());
									}
								});
							} else {
								startFuture.fail(resInternalUnitDeviceStatus.cause());
							}
						});
					} else {
						startFuture.fail(resExternalUnitData.cause());
					}
				});
			} else {
				startFuture.fail(resInternalUnitData.cause());
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
	 * キャッシュ済みのデバイス制御状態を取得する.
	 * @return キャッシュ済みのデバイス制御状態
	 */
	protected abstract JsonObject cachedDeviceStatus();

	////

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#unitData()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットのユニットデータを取得する.
	 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
	 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
	 * メッセージボディ : なし
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
	 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
	 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
	 * レスポンス : ユニットデータ [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startInternalUnitDataService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.unitData(), req -> {
			getDataAndReply_(req);
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#unitData(String)}
	 * 範囲 : グローバル
	 * 処理 : ID で指定したユニットのユニットデータを取得する.
	 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
	 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
	 * 　　   ヘッダに gridMasterUnitId 指定が必要であり GridMaster インタロック値と一致する必要がある.
	 * メッセージボディ : なし
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
	 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
	 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
	 * 　　　　　　　　   - {@code "gridMasterUnitId"} : GridMaster ユニット ID
	 * レスポンス : ユニットデータ [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startExternalUnitDataService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Controller.unitData(ApisConfig.unitId()), req -> {
			// GridMaster インタロックを確認する
			checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
				if (resCheckGridMasterInterlock.succeeded()) {
					getDataAndReply_(req);
				} else {
					req.fail(-1, resCheckGridMasterInterlock.cause().getMessage());
				}
			});
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#unitDeviceStatus()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットのデバイス制御状態を取得する.
	 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
	 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
	 * メッセージボディ : なし
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
	 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
	 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
	 * レスポンス : デバイス制御状態 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startInternalUnitDeviceStatusService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.unitDeviceStatus(), req -> {
			getDeviceStatusAndReply_(req);
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#unitDeviceStatus(String)}
	 * 範囲 : グローバル
	 * 処理 : ID で指定したユニットのデバイス制御状態を取得する.
	 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
	 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
	 * 　　   ヘッダに gridMasterUnitId 指定が必要であり GridMaster インタロック値と一致する必要がある.
	 * メッセージボディ : なし
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
	 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
	 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
	 * 　　　　　　　　   - {@code "gridMasterUnitId"} : GridMaster ユニット ID
	 * レスポンス : デバイス制御状態 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startExternalUnitDeviceStatusService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Controller.unitDeviceStatus(ApisConfig.unitId()), req ->  {
			// GridMaster インタロックを確認する
			checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
				if (resCheckGridMasterInterlock.succeeded()) {
					getDeviceStatusAndReply_(req);
				} else {
					req.fail(-1, resCheckGridMasterInterlock.cause().getMessage());
				}
			});
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#unitDatas()}
	 * 範囲 : グローバル
	 * 処理 : 指定されたアドレスに対し自ユニットのユニットデータを送る.
	 * 　　   GridMaster のデータ収集処理で使用する.
	 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
	 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
	 * 　　   ヘッダに gridMasterUnitId 指定が必要であり GridMaster インタロック値と一致する必要がある.
	 * 　　   ヘッダにデータを送り返すアドレス replyAddress 指定が必要である.
	 * メッセージボディ : なし
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
	 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
	 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
	 * 　　　　　　　　   - {@code "gridMasterUnitId"} : GridMaster ユニット ID
	 * 　　　　　　　　   - {@code "replyAddress"} : データを送り返すアドレス
	 * レスポンス : なし
	 * @param completionHandler the completion handler
	 */
	private void startUnitDatasService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Controller.unitDatas(), req -> {
			String replyAddress = req.headers().get("replyAddress");
//			if (log.isInfoEnabled()) log.info("DataResponding:" + replyAddress + " received");
			if (replyAddress != null) {
				// GridMaster インタロックを確認する
				checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
					if (resCheckGridMasterInterlock.succeeded()) {
//						if (log.isInfoEnabled()) log.info("DataResponding:" + replyAddress + " getting data");
						getData_(req, resGetData -> {
							if (resGetData.succeeded()) {
								vertx.eventBus().send(replyAddress, resGetData.result());
//								if (log.isInfoEnabled()) log.info("DataResponding:" + replyAddress + " replied");
							} else {
								log.error(resGetData.cause());
							}
						});
					}
				});
			} else {
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal access; no replyAddress in request header");
			}
		}).completionHandler(completionHandler);
	}

	////

	/**
	 * GridMaster インタロックを確認する.
	 * POLICY に記載されたクラスタメンバに含まれているかも先に確認する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param <T> message オブジェクトの {@link Message#body()} の型
	 * @param req message オブジェクト
	 * @param completionHandler the completion handler
	 */
	private <T> void checkGridMasterInterlock_(Message<T> req, Handler<AsyncResult<Void>> completionHandler) {
		String reqGridMasterUnitId = req.headers().get("gridMasterUnitId");
		if (reqGridMasterUnitId != null) {
			if (PolicyKeeping.isMember(reqGridMasterUnitId)) {
				InterlockUtil.getGridMasterUnitId(vertx, resGridMasterUnitId -> {
					if (resGridMasterUnitId.succeeded()) {
						String interlockedGridMasterUnitId = resGridMasterUnitId.result();
						if (interlockedGridMasterUnitId != null) {
							if (reqGridMasterUnitId.equals(interlockedGridMasterUnitId)) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "access from illegal gridMaster; interlocked gridMasterUnitId: " + interlockedGridMasterUnitId + ", gridMasterUnitId in request: " + reqGridMasterUnitId, completionHandler);
							}
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal access; no interlocked gridMasterUnitId", completionHandler);
						}
					} else {
						ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGridMasterUnitId.cause(), completionHandler);
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "request received from illegal unit : " + reqGridMasterUnitId, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal access; no gridMasterUnitId in request header", completionHandler);
		}
	}

	private void getDeviceStatusAndReply_(Message<Void> message) {
		getDeviceStatus_(message, resGetDeviceStatus -> {
			if (resGetDeviceStatus.succeeded()) {
				message.reply(resGetDeviceStatus.result());
			} else {
				message.fail(-1, resGetDeviceStatus.cause().getMessage());
			}
		});
	}
	private void getDeviceStatus_(Message<Void> message, Handler<AsyncResult<JsonObject>> completionHandler) {
		String urgent = message.headers().get("urgent");
		if (urgent != null && Boolean.valueOf(urgent)) {
			// urgent ならデータを取得して返す
			DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
				if (resExclusiveLock.succeeded()) {
					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doGetDeviceStatusWithExclusiveLock_(resDoGetDeviceStatusWithExclusiveLock -> {
						lock.release();
						completionHandler.handle(resDoGetDeviceStatusWithExclusiveLock);
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), completionHandler);
				}
			});
		} else {
			// urgent でなければキャッシュされたデータを返す
			completionHandler.handle(Future.succeededFuture(cachedDeviceStatus()));
		}
	}
	private void doGetDeviceStatusWithExclusiveLock_(Handler<AsyncResult<JsonObject>> completionHandler) {
		vertx.eventBus().<JsonObject>send(ServiceAddress.Controller.urgentUnitDeviceStatus(), null, rep -> {
			if (rep.succeeded()) {
				completionHandler.handle(Future.succeededFuture(rep.result().body()));
			} else {
				if (ReplyFailureUtil.isRecipientFailure(rep)) {
					completionHandler.handle(Future.failedFuture(rep.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, rep.cause(), completionHandler);
				}
			}
		});
	}

	private void getDataAndReply_(Message<Void> message) {
		getData_(message, resGetData -> {
			if (resGetData.succeeded()) {
				message.reply(resGetData.result());
			} else {
				message.fail(-1, resGetData.cause().getMessage());
			}
		});
	}
	private void getData_(Message<Void> message, Handler<AsyncResult<JsonObject>> completionHandler) {
		String urgent = message.headers().get("urgent");
		if (urgent != null && Boolean.valueOf(urgent)) {
			// urgent ならデータを取得して返す
			DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
				if (resExclusiveLock.succeeded()) {
					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doGetDataWithExclusiveLock_(resDoGetDataWithExclusiveLock -> {
						lock.release();
						completionHandler.handle(resDoGetDataWithExclusiveLock);
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), completionHandler);
				}
			});
		} else {
			// urgent でなければキャッシュされたデータを返す
			completionHandler.handle(Future.succeededFuture(DataAcquisition.cache.jsonObject()));
		}
	}
	private void doGetDataWithExclusiveLock_(Handler<AsyncResult<JsonObject>> completionHandler) {
		vertx.eventBus().<JsonObject>send(ServiceAddress.Controller.urgentUnitData(), null, rep -> {
			if (rep.succeeded()) {
				completionHandler.handle(Future.succeededFuture(rep.result().body()));
			} else {
				if (ReplyFailureUtil.isRecipientFailure(rep)) {
					completionHandler.handle(Future.failedFuture(rep.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, rep.cause(), completionHandler);
				}
			}
		});
	}

}
