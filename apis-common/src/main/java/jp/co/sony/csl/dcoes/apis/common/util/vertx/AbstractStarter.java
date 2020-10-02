package jp.co.sony.csl.dcoes.apis.common.util.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.JulUtil;

/**
 * APIS プログラム共通の親玉 Verticle.
 * @author OES Project
 */
public abstract class AbstractStarter extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(AbstractStarter.class);

	/**
	 * APIS プログラムのバージョン文字列.
	 * 値は {@value}.
	 */
	public static final String APIS_VERSION = "2.26.2-a01";

	/**
	 * 起動時に呼び出される.
	 * APIS プログラム共通の初期化処理を実行する.
	 * プログラム独自の起動処理を実行するため {@link #doStart(Handler)} を呼び出す.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		init_(resInit -> {
			if (resInit.succeeded()) {
				startShutdownAllService_(resShutdownAll -> {
					if (resShutdownAll.succeeded()) {
						startShutdownLocalService_(resShutdownLocal -> {
							if (resShutdownLocal.succeeded()) {
								startMulticastLogHandlerLevelService_(resMulticastLogHandlerLevel -> {
									if (resMulticastLogHandlerLevel.succeeded()) {
										vertx.deployVerticle(new WatchdogRestarting(), resWatchdogRestarting -> {
											if (resWatchdogRestarting.succeeded()) {
												doStart(resDoStart -> {
													if (resDoStart.succeeded()) {
														if (log.isInfoEnabled()) log.info("APIS version : " + AbstractStarter.APIS_VERSION);
														if (log.isInfoEnabled()) log.info("communityId  : " + VertxConfig.communityId());
														if (log.isInfoEnabled()) log.info("clusterId    : " + VertxConfig.clusterId());
														if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
														startFuture.complete();
													} else {
														startFuture.fail(resDoStart.cause());
													}
												});
											} else {
												startFuture.fail(resWatchdogRestarting.cause());
											}
										});
									} else {
										startFuture.fail(resMulticastLogHandlerLevel.cause());
									}
								});
							} else {
								startFuture.fail(resShutdownLocal.cause());
							}
						});
					} else {
						startFuture.fail(resShutdownAll.cause());
					}
				});
			} else {
				startFuture.fail(resInit.cause());
			}
		});
	}

	/**
	 * 停止時に呼び出される.
	 * プログラム独自の停止処理を実行するため {@link #doStop(Handler)} を呼び出す.
	 * @param stopFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop(Future<Void> stopFuture) throws Exception {
		doStop(resDoStop -> {
			if (resDoStop.succeeded()) {
				// nop
			} else {
				log.error(resDoStop.cause());
			}
			if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
			stopFuture.complete();
		});
	}

	////

	/**
	 * APIS プログラム共通の初期化処理.
	 * @param completionHandler the completion handler
	 */
	private void init_(Handler<AsyncResult<Void>> completionHandler) {
		initCloseHook_();
		vertx.exceptionHandler(t -> {
			handleUnhandledException(t);
		});
		if (vertx.isClustered()) {
			checkClusterApisVersion_(completionHandler);
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}
	/**
	 * vertx インスタンスの終了時に呼ばれる処理を登録する.
	 * シグナルでプロセスが落とされる場合にも APIS の停止処理 ( {@link #doShutdown(Handler)} ) を実行するため.
	 */
	private void initCloseHook_() {
		((VertxImpl) vertx).addCloseHook(new Closeable() {
			@Override public void close(Handler<AsyncResult<Void>> completionHandler) {
				String msg = "Vert.x close hook invoked";
				if (log.isInfoEnabled()) log.info(msg);
				System.out.println(msg);
				doShutdown(r -> {
					if (r.succeeded()) {
						String msg2 = "done";
						if (log.isInfoEnabled()) log.info(msg2);
						System.out.println(msg2);
					} else {
						log.error(r.cause());
						System.err.println(r.cause());
					}
					completionHandler.handle(r);
				});
			}
		});
		if (log.isInfoEnabled()) log.info("Vert.x close hook initialized");
	}
	private static final String MAP_NAME = AbstractStarter.class.getName();
	private static final String MAP_KEY_APIS_VERSION = "apisVersion";
	/**
	 * クラスタ内 APIS プログラムのバージョンが全て同じであることを確認する.
	 * @param completionHandler the completion handler
	 */
	private void checkClusterApisVersion_(Handler<AsyncResult<Void>> completionHandler) {
		// シードは適当につくる
		String CIPHER_SEED = VertxConfig.communityId() + '-' + VertxConfig.clusterId();
		EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, CIPHER_SEED, resMap -> {
			if (resMap.succeeded()) {
				AsyncMap<String, String> theMap = resMap.result();
				theMap.putIfAbsent(MAP_KEY_APIS_VERSION, APIS_VERSION, resPutIfAbsent -> {
					if (resPutIfAbsent.succeeded()) {
						String existingValue = resPutIfAbsent.result();
						if (existingValue == null) {
							// 置けた ( エントリがなかった ) → 最初のクラスタメンバだった → 成功
							completionHandler.handle(Future.succeededFuture());
						} else {
							// 置けなかった ( もうエントリがあった )
							if (existingValue.equals(APIS_VERSION)) {
								// → 自分の値 ( バージョン ) と同じ → 成功
								completionHandler.handle(Future.succeededFuture());
							} else {
								// → 自分の値 ( バージョン ) と違う → 失敗 
								completionHandler.handle(Future.failedFuture("my APIS_VERSION : " + APIS_VERSION + " ; is different from cluster's : " + existingValue));
							}
						}
					} else {
						// そもそも処理が失敗した → 失敗
						completionHandler.handle(Future.failedFuture(resPutIfAbsent.cause()));
					}
				});
			} else {
				completionHandler.handle(Future.failedFuture(resMap.cause()));
			}
		});
	}

	////

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#multicastLogHandlerLevel()}
	 * 範囲 : グローバル
	 * 処理 : UDP マルチキャストログ出力のレベルを変更する
	 * メッセージボディ : ログレベル [{@link String}]
	 * 　　　　　　　　   指定がなければ初期状態に戻す
	 * メッセージヘッダ : なし
	 * レスポンス : 成功したら {@code "ok"}
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startMulticastLogHandlerLevelService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>consumer(ServiceAddress.multicastLogHandlerLevel(), req -> {
			try {
				if (log.isInfoEnabled()) log.info("setting multicast log level to : " + req.body() + " ...");
				JulUtil.setRootMulticastHandlerLevel(req.body());
				req.reply("ok");
			} catch (Exception e) {
				log.error(e);
				req.fail(-1, e.getMessage());
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#shutdownLocal()}
	 * 範囲 : ローカル
	 * 処理 : シャットダウンする.
	 * 　　   実際の処理は {@link #shutdown_()}.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : {@code "ok"}
	 * @param completionHandler the completion handler
	 */
	private void startShutdownLocalService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.shutdownLocal(), req -> {
			if (log.isInfoEnabled()) log.info(ServiceAddress.shutdownLocal() + " received");
			req.reply("ok");
			shutdown_();
		}).completionHandler(completionHandler);
	}
	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#shutdownAll()}
	 * 範囲 : グローバル
	 * 処理 : シャットダウンする.
	 * 　　   実際の処理は {@link #shutdown_()}.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : {@code "ok"}
	 * @param completionHandler the completion handler
	 */
	private void startShutdownAllService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.shutdownAll(), req -> {
			if (log.isInfoEnabled()) log.info(ServiceAddress.shutdownAll() + " received");
			req.reply("ok");
			shutdown_();
		}).completionHandler(completionHandler);
	}
	/**
	 * シャットダウン処理.
	 * 実際の処理は {@link #doShutdown(Handler)} であるが空実装.
	 * 必要に応じてサブクラスで実装する.
	 */
	private void shutdown_() {
		if (log.isInfoEnabled()) log.info("shutting down ...");
		doShutdown(resDoShutdown -> {
			if (resDoShutdown.succeeded()) {
				// nop
			} else {
				log.error(resDoShutdown.cause());
			}
			if (log.isInfoEnabled()) log.info("closing Vert.x ...");
			vertx.close();
		});
	}

	////

	/**
	 * 各 APIS プログラム独自の起動処理を実装するための抽象メソッド.
	 * {@link #start(Future)} から呼び出される.
	 * 具体的な処理はサブクラスで実装する.
	 * @param completionHandler the completion handler
	 */
	protected abstract void doStart(Handler<AsyncResult<Void>> completionHandler);
	/**
	 * 各 APIS プログラム独自の停止処理を実装するための空メソッド.
	 * {@link #stop(Future)} から呼び出される.
	 * 必要に応じてサブクラスで実装する.
	 * @param completionHandler the completion handler
	 */
	protected void doStop(Handler<AsyncResult<Void>> completionHandler) {
		// default implementation
		completionHandler.handle(Future.succeededFuture());
	}
	/**
	 * 各 APIS プログラム独自の停止処理を実装するための空メソッド.
	 * Vert.x の closeHook および EventBus からのシャットダウンメッセージで呼び出される.
	 * 必要に応じてサブクラスで実装する.
	 * @param completionHandler the completion handler
	 */
	protected void doShutdown(Handler<AsyncResult<Void>> completionHandler) {
		// default implementation
		completionHandler.handle(Future.succeededFuture());
	}
	/**
	 * キャッチされなかった例外に対して呼び出される空メソッド.
	 * 必要に応じてサブクラスで実装する.
	 * @param t 処理対象の例外
	 */
	protected void handleUnhandledException(Throwable t) {
		// default implementation
		log.error("Unhandled exception caught", t);
	}

}
