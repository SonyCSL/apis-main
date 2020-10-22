package jp.co.sony.csl.dcoes.apis.main.app.mediator.util;

import java.util.List;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ErrorException;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.EncryptedClusterWideMapUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;

/**
 * 融通参加ユニットからの融通停止依頼を共有メモリ上に管理するツール.
 * @author OES Project
 */
public class DealNeedToStopUtil {
	private static final Logger log = LoggerFactory.getLogger(DealNeedToStopUtil.class);

	private static final String MAP_NAME = DealNeedToStopUtil.class.getName();
	/**
	 * 融通停止依頼を共有メモリ上に保持しておく ttl 値 [ms].
	 * 値は {@value}.
	 */
	private static final long TTL_MSEC = 30L * 60L * 1000L; // 30 min

	private DealNeedToStopUtil() { }

	/**
	 * {@code dealId} で指定された DEAL に対する停止要求を記録する.
	 * @param vertx vertx オブジェクト
	 * @param dealId 融通 ID
	 * @param reasons 理由リスト
	 * @param completionHandler the completion handler
	 */
	public static void add(Vertx vertx, String dealId, JsonArray reasons, Handler<AsyncResult<Void>> completionHandler) {
		if (dealId != null && reasons != null) {
			// 共有メモリに保存しようとする
			add_(vertx, dealId, reasons, res -> {
				if (res.succeeded()) {
					completionHandler.handle(Future.succeededFuture());
				} else {
					// 失敗しても
					if (res.cause() instanceof ErrorException) {
						// 何かヤバいエラーなら失敗とする
						completionHandler.handle(res);
					} else {
						// クロス上書きを検知して失敗したようなので再試行
						add_(vertx, dealId, reasons, res2 -> {
							if (res2.succeeded()) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								// また失敗しても
								if (res2.cause() instanceof ErrorException) {
									// 何かヤバいエラーなら失敗とする
									completionHandler.handle(res2);
								} else {
									// クロス上書きを検知して失敗したようなので再々試行
									add_(vertx, dealId, reasons, res3 -> {
										if (res3.succeeded()) {
											completionHandler.handle(Future.succeededFuture());
										} else {
											// あきらめる
											completionHandler.handle(res3);
										};
									});
								}
							};
						});
					}
				};
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealNeedToStopUtil.add(); no dealId and/or reasons ; dealId : " + dealId + " , reasons : " + reasons, completionHandler);
		}
	}
	private static void add_(Vertx vertx, String dealId, JsonArray reasons, Handler<AsyncResult<Void>> completionHandler) {
		EncryptedClusterWideMapUtil.<String, JsonArray>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				resMap.result().get(dealId, resGet -> {
					if (resGet.succeeded()) {
						JsonArray old = resGet.result();
						JsonArray neo = (old != null) ? old.copy() : new JsonArray();
						for (Object aReason : reasons) {
//							boolean found = false;
//							for (Object r : neo) {
//								if (aReason.equals(r)) {
//									found = true;
//									break;
//								}
//							}
//							if (!found) {
//								neo.add(aReason);
//							}
							if (!neo.contains(aReason)) {
								// 同じメッセージを複数書き込まないようにする
								neo.add(aReason);
							}
						}
						// 一通り更新したのち...
						if (old != null) {
							// もともとあった場合
							// 共有メモリの値を差し替える
							resMap.result().replaceIfPresent(dealId, old, neo, resReplaceIfPresent -> {
								if (resReplaceIfPresent.succeeded()) {
									Boolean replaced = resReplaceIfPresent.result();
									if (replaced) {
										// 差し替え成功 → OK
										if (log.isDebugEnabled()) log.debug("needToStop added with dealId : " + dealId);
										completionHandler.handle(Future.succeededFuture());
									} else {
										// old の値が変わっていたので差し替え失敗 → この処理中に追加されたに違いない → NG
										String msg = "DealNeedToStopUtil.add_(); failed to replace with dealId : " + dealId;
										completionHandler.handle(Future.failedFuture(msg));
									}
								} else {
									ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resReplaceIfPresent.cause(), completionHandler);
								}
							});
						} else {
							// もともとなかった場合
							// 期限付きで共有メモリに保存する
							resMap.result().putIfAbsent(dealId, neo, TTL_MSEC, resPutIfAbsent -> {
								if (resPutIfAbsent.succeeded()) {
									JsonArray existingValue = resPutIfAbsent.result();
									if (existingValue == null) {
										// 保存成功 → OK
										if (log.isDebugEnabled()) log.debug("needToStop added with dealId : " + dealId);
										completionHandler.handle(Future.succeededFuture());
									} else {
										// 保存失敗 → もともとなかったはずなのにあった → この処理中に追加されたに違いない → NG
										String msg = "DealNeedToStopUtil.add_(); failed to put with dealId : " + dealId;
										completionHandler.handle(Future.failedFuture(msg));
									}
								} else {
									ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resPutIfAbsent.cause(), completionHandler);
								}
							});
						}
					} else {
						ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resGet.cause(), completionHandler);
					}
				});
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
			}
		});
	}

	/**
	 * {@code dealId} で指定された DEAL に対する停止要求を削除する.
	 * completionHandler の {@link AsyncResult#result()} で削除した情報を受け取る.
	 * @param vertx vertx オブジェクト
	 * @param dealId 融通 ID
	 * @param completionHandler the completion handler
	 */
	public static void remove(Vertx vertx, String dealId, Handler<AsyncResult<JsonArray>> completionHandler) {
		EncryptedClusterWideMapUtil.<String, JsonArray>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				resMap.result().remove(dealId, resRemove -> {
					if (resRemove.succeeded()) {
						if (resRemove.result() != null) {
							if (log.isDebugEnabled()) log.debug("needToStop removed with dealId : " + dealId);
						} else {
							if (log.isDebugEnabled()) log.debug("no needToStop with dealId : " + dealId);
						}
						completionHandler.handle(resRemove);
					} else {
						ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resRemove.cause(), completionHandler);
					}
				});
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
			}
		});
	}

	////

	/**
	 * 記録してある停止要求を DEAL オブジェクトに転記する.
	 * @param vertx vertx オブジェクト
	 * @param deals DEAL オブジェクトのリスト
	 * @param completionHandler the completion handler
	 */
	public static void copyToDeals(Vertx vertx, List<JsonObject> deals, Handler<AsyncResult<Void>> completionHandler) {
		EncryptedClusterWideMapUtil.<String, JsonArray>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				resMap.result().entries(resEntries -> {
					if (resEntries.succeeded()) {
						Map<String, JsonArray> entries = resEntries.result();
						for (String aDealId : entries.keySet()) {
							for (JsonObject aDeal : deals) {
								if (aDealId.equals(Deal.dealId(aDeal))) {
									aDeal.put("needToStopReasons", entries.get(aDealId));
									break;
								}
							}
						}
						completionHandler.handle(Future.succeededFuture());
					} else {
						ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resEntries.cause(), completionHandler);
					}
				});
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
			}
		});
	}
}
