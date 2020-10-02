package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;

import java.io.File;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.StringUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.EncryptedClusterWideMapUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.FileSystemUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 各種動作状態を管理する Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * @author OES Project
 */
public class StateHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(StateHandling.class);

	/**
	 * 保存パスのフォーマットのデフォルト値.
	 * 値は {@value}.
	 */
	private static final JsonObjectUtil.DefaultString DEFAULT_FILE_FORMAT = new JsonObjectUtil.DefaultString(StringUtil.TMPDIR + "/apis/state/%s");

	private static String operationMode_ = null;
	private static boolean started_ = false;
	private static boolean stopping_ = false;

	/**
	 * 起動時に呼び出される.
	 * 初期化処理を実行する.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		init_(resInit -> {
			if (resInit.succeeded()) {
				startGlobalOperationModeService_(resGlobalOperationMode -> {
					if (resGlobalOperationMode.succeeded()) {
						startLocalOperationModeService_(resLocalOperationMode -> {
							if (resLocalOperationMode.succeeded()) {
								if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
								startFuture.complete();
							} else {
								startFuture.fail(resLocalOperationMode.cause());
							}
						});
					} else {
						startFuture.fail(resGlobalOperationMode.cause());
					}
				});
			} else {
				startFuture.fail(resInit.cause());
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
	 * 起動時の初期化.
	 * ローカルの各種ステータスをファイルシステムから読み込む.
	 * @param completionHandler the completion handler
	 */
	private void init_(Handler<AsyncResult<Void>> completionHandler) {
		readFromFile_(vertx, "operationMode", res -> {
			if (res.succeeded()) {
				String result = res.result();
				if (result != null && !"heteronomous".equals(result) && !"stop".equals(result)) {
					// "heteronomous" でも "stop" でもなければ null ( 無指定 ) として扱う
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "local operationMode '" + result + "' not supported, default to null ( follow global )");
					result = null;
				}
				operationMode_ = result;
				completionHandler.handle(Future.succeededFuture());
			} else {
				completionHandler.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#operationMode()}
	 * 範囲 : グローバル
	 * 処理 : グローバル動作モードを set/get する.
	 * 　　   値は以下のいずれか.
	 * 　　   - "autonomous"
	 * 　　   - "heteronomous"
	 * 　　   - "stop"
	 * 　　   - "manual"
	 * メッセージボディ :
	 * 　　　　　　　　   set の場合 : 設定するグローバル融通モード [{@link String}]
	 * 　　　　　　　　   get の場合 : なし
	 * メッセージヘッダ : {@code "command"}
	 * 　　　　　　　　   - {@code "set"} : グローバル融通モードを変更する
	 * 　　　　　　　　   - {@code "get"} : グローバル融通モードを取得する
	 * レスポンス :
	 * 　　　　　   set の場合 : 自ユニットの ID [{@link String}]
	 * 　　　　　   get の場合 : 現在のグローバル融通モード [{@link String}]
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGlobalOperationModeService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>consumer(ServiceAddress.operationMode(), req -> {
			String command = req.headers().get("command");
			if ("set".equals(command)) {
				String value = req.body();
				if (value != null && !"autonomous".equals(value) && !"heteronomous".equals(value) && !"stop".equals(value) && !"manual".equals(value)) {
					// "autonomous" でも "heteronomous" でも "stop" でも "manual" でもなければ null ( 無指定 ) として扱う
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "global operationMode '" + value + "' not supported, default to null ( follow policy )");
					value = null;
				}
				String result = value;
				setToClusterWideMap_(vertx, "operationMode", result, r -> {
					if (r.succeeded()) {
						if (log.isInfoEnabled()) log.info("global operationMode set to : " + result);
						req.reply(ApisConfig.unitId());
					} else {
						req.fail(-1, r.cause().getMessage());
					}
				});
			} else {
				// "set" 以外は "get" として扱う
				globalOperationMode(vertx, r -> {
					if (r.succeeded()) {
						req.reply(r.result());
					} else {
						req.fail(-1, r.cause().getMessage());
					}
				});
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.User#operationMode(String)}
	 * 範囲 : グローバル
	 * 処理 : 自ユニットのローカル融通モードを set/get する.
	 * 　　   値は以下のいずれか.
	 * 　　   - {@code null}
	 * 　　   - "heteronomous"
	 * 　　   - "stop"
	 * メッセージボディ :
	 * 　　　　　　　　   set の場合 : 設定するローカル融通モード [{@link String}]
	 * 　　　　　　　　   get の場合 : なし
	 * メッセージヘッダ : {@code "command"}
	 * 　　　　　　　　   - {@code "set"} : ローカル融通モードを変更する
	 * 　　　　　　　　   - {@code "get"} : ローカル融通モードを取得する
	 * レスポンス :
	 * 　　　　　   set の場合 : 自ユニットの ID [{@link String}]
	 * 　　　　　   get の場合 : 現在のローカル融通モード [{@link String}]
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startLocalOperationModeService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>consumer(ServiceAddress.User.operationMode(ApisConfig.unitId()), req -> {
			String command = req.headers().get("command");
			if ("set".equals(command)) {
				String value = req.body();
				if (value != null && !"heteronomous".equals(value) && !"stop".equals(value)) {
					// "heteronomous" でも "stop" でもなければ null ( 無指定 ) として扱う
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "local operationMode '" + value + "' not supported, default to null ( follow global )");
					value = null;
				}
				String result = value;
				operationMode_ = result;
				// 再起動しても保持するようにファイルシステムに書いておく
				writeToFile_(vertx, "operationMode", result, r -> {
					if (r.succeeded()) {
						if (log.isInfoEnabled()) log.info("local operationMode set to : " + result);
						req.reply(ApisConfig.unitId());
					} else {
						req.fail(-1, r.cause().getMessage());
					}
				});
			} else {
				localOperationMode(vertx, r -> {
					if (r.succeeded()) {
						req.reply(r.result());
					} else {
						req.fail(-1, r.cause().getMessage());
					}
				});
			}
		}).completionHandler(completionHandler);
	}

	////

	/**
	 * グローバル融通モードを取得する.
	 * 値は以下のいずれか.
	 * - "autonomous"
	 * - "heteronomous"
	 * - "stop"
	 * - "manual"
	 * 共有メモリに正しい値があればそれを返す.
	 * POLICY に正しい値があればそれを返す.
	 * いずれも無ければ {@code "stop"}.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void globalOperationMode(Vertx vertx, Handler<AsyncResult<String>> completionHandler) {
		getFromClusterWideMap_(vertx, "operationMode", res -> {
			if (res.succeeded()) {
				String result = res.result();
				if (result != null && !"autonomous".equals(result) && !"heteronomous".equals(result) && !"stop".equals(result) && !"manual".equals(result)) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "global operationMode '" + result + "' not supported, follow policy");
					result = null;
				}
				if (result == null) {
					// おかしな値だったら POLICY の設定値に落ちる
					result = PolicyKeeping.cache().getString("operationMode");
					if (result != null && !"autonomous".equals(result) && !"heteronomous".equals(result) && !"stop".equals(result) && !"manual".equals(result)) {
						ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "policy operationMode '" + result + "' not supported, default to null");
						result = null;
					}
				}
				if (result == null) {
					// それでもおかしな値だったら "stop" に落ちる
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "global operationMode is null, default to 'stop'");
					result = "stop";
				}
				completionHandler.handle(Future.succeededFuture(result));
			} else {
				completionHandler.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	/**
	 * ローカル融通モードを取得する.
	 * 値は以下のいずれか.
	 * - {@code null}
	 * - "heteronomous"
	 * - "stop"
	 * ローカルメモリに正しい値があればそれを返す.
	 * 無ければ {@code null}.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void localOperationMode(Vertx vertx, Handler<AsyncResult<String>> completionHandler) {
		String result = operationMode_;
		if (result != null && !"heteronomous".equals(result) && !"stop".equals(result)) {
			// "heteronomous" でも "stop" でもなければ null ( 無指定 ) として扱う
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "local operationMode '" + result + "' not supported, default to null");
			result = null;
		}
		completionHandler.handle(Future.succeededFuture(result));
	}

	/**
	 * グローバル融通モード, ローカル融通モード, およびそれらを総合した実効融通モードを取得する.
	 * 実効融通モードの算出条件は以下のとおり.
	 * - グローバル : {@code "autonomous"},   ローカル : {@code null}             → 実効 : {@code "autonomous"}
	 * - グローバル : {@code "autonomous"},   ローカル : {@code "heteronomous"}   → 実効 : {@code "heteronomous"}
	 * - グローバル : {@code "autonomous"},   ローカル : {@code "stop"}           → 実効 : {@code "stop"}
	 * - グローバル : {@code "heteronomous"}, ローカル : {@code null}             → 実効 : {@code "heteronomous"}
	 * - グローバル : {@code "heteronomous"}, ローカル : {@code "heteronomous"}   → 実効 : {@code "heteronomous"}
	 * - グローバル : {@code "heteronomous"}, ローカル : {@code "stop"}           → 実効 : {@code "stop"}
	 * - グローバル : {@code "stop"},         ローカル : {@code null}             → 実効 : {@code "stop"}
	 * - グローバル : {@code "stop"},         ローカル : {@code "heteronomous"}   → 実効 : {@code "stop"}
	 * - グローバル : {@code "stop"},         ローカル : {@code "stop"}           → 実効 : {@code "stop"}
	 * - グローバル : {@code "manual"},       ローカル : {@code null}             → 実効 : {@code "manual"}
	 * - グローバル : {@code "manual"},       ローカル : {@code "heteronomous"}   → 実効 : {@code "manual"}
	 * - グローバル : {@code "manual"},       ローカル : {@code "stop"}           → 実効 : {@code "manual"}
	 * 結果の {@link JsonObject} の中身は以下のとおり.
	 * - global : グローバル融通モード
	 * - local : ローカル融通モード
	 * - effective : 実効融通モード
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void operationModes(Vertx vertx, Handler<AsyncResult<JsonObject>> completionHandler) {
		Future<String> globalFuture = Future.future();
		Future<String> localFuture = Future.future();
		globalOperationMode(vertx, globalFuture);
		localOperationMode(vertx, localFuture);
		CompositeFuture.<String, String>all(globalFuture, localFuture).setHandler(ar -> {
			if (ar.succeeded()) {
				String global = ar.result().resultAt(0);
				String local = ar.result().resultAt(1);
				String effective = null;
				if (local == null) {
					effective = global;
				} else if ("autonomous".equals(global)) {
					effective = local;
				} else if ("heteronomous".equals(global)) {
					effective = local;
				} else if ("stop".equals(global)) {
					effective = global;
				} else if ("manual".equals(global)) {
					effective = global;
				}
				if (effective == null) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "illegal operationModes; global : " + global + ", local : " + local + "; use 'stop'");
					effective = "stop";
				}
				JsonObject result = new JsonObject();
				result.put("global", global);
				result.put("local", local);
				result.put("effective", effective);
				completionHandler.handle(Future.succeededFuture(result));
			} else {
				completionHandler.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	/**
	 * 実効融通モードを取得する.
	 * {@link #operationModes(Vertx, Handler)} の結果から {@code "effective"} を取り出して返す.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void operationMode(Vertx vertx, Handler<AsyncResult<String>> completionHandler) {
		operationModes(vertx, res -> {
			if (res.succeeded()) {
				completionHandler.handle(Future.succeededFuture(res.result().getString("effective")));
			} else {
				completionHandler.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	////

	/**
	 * 動作状態を起動済みに変更する.
	 */
	public static void setStarted() {
		started_ = true;
		if (log.isInfoEnabled()) log.info("started");
	}
	/**
	 * 動作状態を停止中に変更する.
	 */
	public static void setStopping() {
		if (log.isInfoEnabled()) log.info("stopping");
		stopping_ = true;
	}
	/**
	 * 動作状態が起動済みか否かを取得する.
	 * @return 起動済みなら {@code true}
	 */
	public static boolean isStarted() { return started_; }
	/**
	 * 動作状態が停止中か否かを取得する.
	 * @return 停止中なら {@code true}
	 */
	public static boolean isStopping() { return stopping_; }
	/**
	 * 動作状態が稼働中か否かを取得する.
	 * @return 稼働中なら {@code true}
	 */
	public static boolean isInOperation() { return started_ && !stopping_; }

	////////

	private static final String MAP_NAME = StateHandling.class.getName();
	/**
	 * 共有メモリに値を書き込む.
	 * @param vertx vertx オブジェクト
	 * @param key キー
	 * @param value 書き込む値
	 * @param completionHandler the completion handler
	 */
	private static void setToClusterWideMap_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				AsyncMap<String, String> map = resMap.result();
				if (value != null) {
					map.put(key, value, resPut -> {
						if (resPut.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resPut.cause(), completionHandler);
						}
					});
				} else {
					map.remove(key, resRemove -> {
						if (resRemove.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resRemove.cause(), completionHandler);
						}
					});
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resMap.cause(), completionHandler);
			}
		});
	}
	/**
	 * 共有メモリから値を読み込む.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param key キー
	 * @param completionHandler the completion handler
	 */
	private static void getFromClusterWideMap_(Vertx vertx, String key, Handler<AsyncResult<String>> completionHandler) {
		EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				AsyncMap<String, String> map = resMap.result();
				map.get(key, resGet -> {
					if (resGet.succeeded()) {
						completionHandler.handle(Future.succeededFuture(resGet.result()));
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resGet.cause(), completionHandler);
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resMap.cause(), completionHandler);
			}
		});
	}

	////

	/**
	 * ファイルシステムに値を書き込む.
	 * @param vertx vertx オブジェクト
	 * @param path ファイルのパス
	 * @param value 書き込む値
	 * @param completionHandler the completion handler
	 */
	private static void writeToFile__(Vertx vertx, String path, String value, Handler<AsyncResult<Void>> completionHandler) {
		vertx.fileSystem().writeFile(path, Buffer.buffer(value), resWriteFile -> {
			if (resWriteFile.succeeded()) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resWriteFile.cause(), completionHandler);
			}
		});
	}
	private static final String PATH_FORMAT_;
	static {
		String s = VertxConfig.config.getString(DEFAULT_FILE_FORMAT, "stateFileFormat");
		PATH_FORMAT_ = StringUtil.fixFilePath(s);
	}
	/**
	 * ファイルシステムに値を書き込む.
	 * @param vertx vertx オブジェクト
	 * @param key キー
	 * @param value 書き込む値
	 * @param completionHandler the completion handler
	 */
	private static void writeToFile_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		String path = String.format(PATH_FORMAT_, key);
		if (value != null) {
			vertx.fileSystem().exists(path, resExists -> {
				if (resExists.succeeded()) {
					if (resExists.result()) {
						// 書く
						writeToFile__(vertx, path, value, completionHandler);
					} else {
						// ファイルがなければまず作る
						String dir = new File(path).getParent();
						FileSystemUtil.ensureDirectory(vertx, dir, resEnsureDir -> {
							if (resEnsureDir.succeeded()) {
								vertx.fileSystem().createFile(path, resCreate -> {
									if (resCreate.succeeded()) {
										// 書く
										writeToFile__(vertx, path, value, completionHandler);
									} else {
										vertx.fileSystem().exists(path, resExistsAgain -> {
											if (resExistsAgain.succeeded()) {
												if (resExistsAgain.result()) {
													writeToFile__(vertx, path, value, completionHandler);
												} else {
													ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resCreate.cause(), completionHandler);
												}
											} else {
												ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resExistsAgain.cause(), completionHandler);
											}
										});
									}
								});
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resEnsureDir.cause(), completionHandler);
							}
						});
					}
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resExists.cause(), completionHandler);
				}
			});
		} else {
			// 値が null ならファイルを削除する
			vertx.fileSystem().exists(path, resExists -> {
				if (resExists.succeeded()) {
					if (resExists.result()) {
						vertx.fileSystem().delete(path, resDelete -> {
							if (resDelete.succeeded()) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								vertx.fileSystem().exists(path, resExistsAgain -> {
									if (resExistsAgain.succeeded()) {
										if (resExistsAgain.result()) {
											ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resDelete.cause(), completionHandler);
										} else {
											completionHandler.handle(Future.succeededFuture());
										}
									} else {
										ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resExistsAgain.cause(), completionHandler);
									}
								});
							}
						});
					} else {
						completionHandler.handle(Future.succeededFuture());
					}
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resExists.cause(), completionHandler);
				}
			});
		}
	}
	/**
	 * ファイルシステムから値を取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param key キー
	 * @param completionHandler the completion handler
	 */
	private static void readFromFile_(Vertx vertx, String key, Handler<AsyncResult<String>> completionHandler) {
		String path = String.format(PATH_FORMAT_, key);
		vertx.fileSystem().exists(path, resExists -> {
			if (resExists.succeeded()) {
				if (resExists.result()) {
					vertx.fileSystem().readFile(path, resReadFile -> {
						if (resReadFile.succeeded()) {
							String result = String.valueOf(resReadFile.result()).trim();
							completionHandler.handle(Future.succeededFuture(result));
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resReadFile.cause(), completionHandler);
						}
					});
				} else {
					completionHandler.handle(Future.succeededFuture());
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resExists.cause(), completionHandler);
			}
		});
	}

}
