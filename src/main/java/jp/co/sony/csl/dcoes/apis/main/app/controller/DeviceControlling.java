package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * デバイス制御サービスの親玉 Verticle.
 * {@link Controller} Verticle から起動される.
 * システムの種類に応じて以下の種類がある.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DeviceControlling}
 * @author OES Project
 */
public abstract class DeviceControlling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DeviceControlling.class);

	/**
	 * HTTP リクエストのタイムアウト時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_REQUEST_TIMEOUT_MSEC = 5000L;
	/**
	 * HTTP リクエストのリトライ回数のデフォルト値.
	 * 値は {@value}.
	 */
	private static final Integer DEFAULT_RETRY_LIMIT = 3;

	private static boolean ignoreDynamicSafetyCheck_ = false;
	/**
	 * 動的安全性チェックの実行をスキップするか否かを取得する.
	 * TODO : ignore じゃなくて skip ではないだろうか...
	 * @return 動的安全性チェックをスキップするなら true
	 */
	public static boolean ignoreDynamicSafetyCheck() { return ignoreDynamicSafetyCheck_; }
	/**
	 * 動的安全性チェックの実行をスキップするか否かを指定する.
	 * @param value true ならスキップ, false なら実行する
	 */
	public static void ignoreDynamicSafetyCheck(boolean value) {
		if (log.isInfoEnabled()) {
			if (ignoreDynamicSafetyCheck_ != value) {
				if (value) {
					log.info("begin ignoring dynamic safety check");
				} else {
					log.info("end ignoring dynamic safety check");
				}
			}
		}
		ignoreDynamicSafetyCheck_ = value;
	}

	/**
	 * 起動時に呼び出される.
	 * 初期化処理を実行する.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		init(resInit -> {
			if (resInit.succeeded()) {
				startLocalStopService_(resLocalStop -> {
					if (resLocalStop.succeeded()) {
						startScramService_(resScram -> {
							if (resScram.succeeded()) {
								startDeviceControllingService_(resDeviceControlling -> {
									if (resDeviceControlling.succeeded()) {
										if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
										startFuture.complete();
									} else {
										startFuture.fail(resDeviceControlling.cause());
									}
								});
							} else {
								startFuture.fail(resScram.cause());
							}
						});
					} else {
						startFuture.fail(resLocalStop.cause());
					}
				});
			} else {
				startFuture.fail(resInit.cause());
			}
		});
	}

	/**
	 * 停止時に呼び出される.
	 * 自ユニットのデバイスを停止する.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop(Future<Void> stopFuture) throws Exception {
		vertx.eventBus().send(ServiceAddress.Controller.stopLocal(), null, repStopLocal -> {
			if (repStopLocal.succeeded()) {
				// nop
			} else {
				if (ReplyFailureUtil.isRecipientFailure(repStopLocal)) {
					// nop
				} else {
					ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repStopLocal.cause());
				}
			}
			if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
			stopFuture.complete();
		});
	}

	////

	/**
	 * 初期化.
	 * @param completionHandler the completion handler
	 */
	protected abstract void init(Handler<AsyncResult<Void>> completionHandler);
	/**
	 * 自ユニットのデバイスを停止する.
	 * completionHandler の {@link AsyncResult#result()} で操作後のデバイス制御状態を受け取る.
	 * @param completionHandler the completion handler
	 */
	protected abstract void doLocalStopWithExclusiveLock(Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * SCRAM 命令受信時に自ユニットのデバイスを停止する.
	 * 現在のモードが電圧リファレンスで {@code excludeVoltageReference} が {@code true} なら何もしない.
	 * completionHandler の {@link AsyncResult#result()} で操作後のデバイス制御状態を受け取る.
	 * @param excludeVoltageReference 現在のモードが電圧リファレンスモードの場合に停止しないフラグ
	 * @param completionHandler the completion handler
	 */
	protected abstract void doScramWithExclusiveLock(boolean excludeVoltageReference, Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * 自ユニットのデバイスを制御する.
	 * completionHandler の {@link AsyncResult#result()} で操作後のデバイス制御状態を受け取る.
	 * @param operation 処理内容
	 * @param completionHandler the completion handler
	 */
	protected abstract void doDeviceControllingWithExclusiveLock(JsonObject operation, Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * デバイス制御状態をユニットデータのキャッシュと双方向マージし結果を取得する.
	 * デバイス制御処理のレスポンスはユニットデータ中のそれより内容が少ない場合がある ( ドライバによる ).
	 * なのでユニットデータのキャッシュを最新にしつつ不足要素を補ってもらう.
	 * @param value マージ元デバイス制御状態
	 * @return マージ済デバイス制御状態
	 */
	protected abstract JsonObject mergeDeviceStatus(JsonObject value);

	////

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#stopLocal()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットのデバイスを停止する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : デバイス制御状態 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startLocalStopService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.stopLocal(), req -> {
			if (log.isInfoEnabled()) log.info("STOP LOCAL command received");
			if (log.isInfoEnabled()) log.info("( do without exclusive lock !!! )");
			// 主にエラー処理で呼ばれるためややこしい排他制御はスルーする
//			DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
//				if (resExclusiveLock.succeeded()) {
//					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doLocalStopWithExclusiveLock(resDoLocalStopWithExclusiveLock -> {
//						lock.release();
						if (resDoLocalStopWithExclusiveLock.succeeded()) {
							req.reply(resDoLocalStopWithExclusiveLock.result());
						} else {
							req.fail(-1, resDoLocalStopWithExclusiveLock.cause().getMessage());
						}
					});
//				} else {
//					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), req);
//				}
//			});
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#stopLocal()}
	 * 範囲 : グローバル
	 * 処理 : 全ユニットのデバイスを緊急停止する.
	 * 　　   緊急処理のため GridMaster インタロックは不要.
	 * メッセージボディ : なし
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "excludeVoltageReference"} : 電圧リファレンス除外フラグ
	 * 　　　　　　　　     - {@code "true"} : 自ユニットが電圧リファレンスならスルーする
	 * 　　　　　　　　     - {@code "false"} : 自ユニットが電圧リファレンスでも停止命令を送信する
	 * レスポンス : デバイス制御状態 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startScramService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Controller.scram(), req -> {
			// エラー処理で呼ばれるため GridMaster インタロックを確認しない
//			checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
//				if (resCheckGridMasterInterlock.succeeded()) {
					if (log.isInfoEnabled()) log.info("SCRAM command received");
					if (log.isInfoEnabled()) log.info("( do without exclusive lock !!! )");
					// エラー処理で呼ばれるためややこしい排他制御はスルーする
//					DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
//						if (resExclusiveLock.succeeded()) {
//							LocalExclusiveLock.Lock lock = resExclusiveLock.result();
							boolean excludeVoltageReference = Boolean.valueOf(req.headers().get("excludeVoltageReference"));
							doScramWithExclusiveLock(excludeVoltageReference, resDoScramWithExclusiveLock -> {
//								lock.release();
								if (resDoScramWithExclusiveLock.succeeded()) {
									req.reply(resDoScramWithExclusiveLock.result());
								} else {
									req.fail(-1, resDoScramWithExclusiveLock.cause().getMessage());
								}
							});
//						} else {
//							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), req);
//						}
//					});
//				} else {
//					req.fail(-1, resCheckGridMasterInterlock.cause().getMessage());
//				}
//			});
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#deviceControlling(String)}
	 * 範囲 : グローバル
	 * 処理 : ID で指定したユニットのデバイスを制御する.
	 * 　　   制御の内容はメッセージボディで送る.
	 * 　　   ヘッダに gridMasterUnitId 指定が必要であり GridMaster インタロック値と一致する必要がある.
	 * 　　   具体的な処理は子クラスで実装する.
	 * メッセージボディ : コマンドおよびパラメタ [{@link JsonObject}]
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "gridMasterUnitId"} : GridMaster ユニット ID
	 * レスポンス : デバイス制御状態 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDeviceControllingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Controller.deviceControlling(ApisConfig.unitId()), req -> {
			// GridMaster インタロックを確認する
			checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
				if (resCheckGridMasterInterlock.succeeded()) {
					DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
						if (resExclusiveLock.succeeded()) {
							LocalExclusiveLock.Lock lock = resExclusiveLock.result();
							doDeviceControllingWithExclusiveLock(req.body(), resDoDeviceControllingWithExclusiveLock -> {
								lock.release();
								if (resDoDeviceControllingWithExclusiveLock.succeeded()) {
									req.reply(resDoDeviceControllingWithExclusiveLock.result());
								} else {
									req.fail(-1, resDoDeviceControllingWithExclusiveLock.cause().getMessage());
								}
							});
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), req);
						}
					});
				} else {
					req.fail(-1, resCheckGridMasterInterlock.cause().getMessage());
				}
			});
		}).completionHandler(completionHandler);
	}

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

	/**
	 * HTTP GET で外部にリクエストを送信しレスポンスを返す.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param client httpclient オブジェクト
	 * @param uri リクエストの URI
	 * @param completionHandler the completion handler
	 */
	protected void send(HttpClient client, String uri, Handler<AsyncResult<JsonObject>> completionHandler) {
		// リトライ回数 : POLICY.controller.retryLimit [{@link Integer}]
		Integer retryLimit = PolicyKeeping.cache().getInteger(DEFAULT_RETRY_LIMIT, "controller", "retryLimit");
		new Sender_(retryLimit, client, uri).execute_(completionHandler);
	}

	////

	/**
	 * 指定された URL に対し HTTP GET する.
	 * 失敗しても指定回数リトライする.
	 * @author OES Project
	 */
	private class Sender_ {
		private int retryLimit_;
		private HttpClient client_;
		private String uri_;
		private boolean completed_ = false;
		private Sender_(Integer retryLimit, HttpClient client, String uri) {
			retryLimit_ = retryLimit;
			client_ = client;
			uri_ = uri;
		}
		/**
		 * HTTP GET 処理実行.
		 * ( 実装がまずいのか ) 二度結果が返ってくることがあるためここでブロックする.
		 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
		 * @param completionHandler the completion handler
		 */
		private void execute_(Handler<AsyncResult<JsonObject>> completionHandler) {
			executeWithRetry_(r -> {
				if (!completed_) {
					completed_ = true;
					completionHandler.handle(r);
				} else {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "executeWithRetry_() result returned more than once : " + r);
				}
			});
		}
		/**
		 * HTTP GET 処理実行.
		 * 失敗してもリトライする.
		 * @param completionHandler the completion handler
		 */
		private void executeWithRetry_(Handler<AsyncResult<JsonObject>> completionHandler) {
			send_(client_, uri_, r -> {
				if (r.succeeded()) {
					JsonObject data = r.result();
					data = mergeDeviceStatus(data); // ユニットデータのキャッシュと双方向マージする
					completionHandler.handle(Future.succeededFuture(data));
				} else {
					if (0 < --retryLimit_) {
						ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed with Device Driver", r.cause());
						executeWithRetry_(completionHandler);
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed with Device Driver", r.cause(), completionHandler);
					}
				}
			});
		}
		/**
		 * HTTP GET 処理実行.
		 * HTTP タイムアウト は {@code POLICY.controller.requestTimeoutMsec} ( デフォルト値 {@link #DEFAULT_REQUEST_TIMEOUT_MSEC} ).
		 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
		 * @param client httpclient オブジェクト
		 * @param uri アクセス URI
		 * @param completionHandler the completion handler
		 */
		private void send_(HttpClient client, String uri, Handler<AsyncResult<JsonObject>> completionHandler) {
			if (log.isInfoEnabled()) log.info("uri : " + uri);
			Long requestTimeoutMsec = PolicyKeeping.cache().getLong(DEFAULT_REQUEST_TIMEOUT_MSEC, "controller", "requestTimeoutMsec");
			client.get(uri, resGet -> {
				if (200 == resGet.statusCode()) {
					resGet.bodyHandler(body -> {
						JsonObjectUtil.toJsonObject(body, completionHandler);
					}).exceptionHandler(t -> {
						completionHandler.handle(Future.failedFuture(t));
					});
				} else {
					resGet.bodyHandler(error -> {
						completionHandler.handle(Future.failedFuture("http request failed : " + resGet.statusCode() + " : " + resGet.statusMessage() + " : " + error));
					}).exceptionHandler(t -> {
						completionHandler.handle(Future.failedFuture("http request failed : " + resGet.statusCode() + " : " + resGet.statusMessage() + " : " + t));
					});
				}
			}).setTimeout(requestTimeoutMsec).exceptionHandler(t -> {
				completionHandler.handle(Future.failedFuture(t));
			}).end();
		}
	}

}
