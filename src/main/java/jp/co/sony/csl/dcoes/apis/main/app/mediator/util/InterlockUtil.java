package jp.co.sony.csl.dcoes.apis.main.app.mediator.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;

import java.util.Collection;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.EncryptedClusterWideMapUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;

/**
 * インタロックを管理するツール.
 * GridMaster インタロックは共有メモリ上に管理する.
 * 融通インタロックはローカルメモリ上に管理する.
 * @author OES Project
 */
public class InterlockUtil {
	private static final Logger log = LoggerFactory.getLogger(InterlockUtil.class);

	private static final String MAP_NAME = InterlockUtil.class.getName();
	private static final String MAP_KEY_GRID_MASTER_UNIT_ID = "gridMasterUnitId";
	private static final String MAP_KEY_DEAL_ID = "dealId";

	private static final LocalExclusiveLock exclusiveLock_ = new LocalExclusiveLock(InterlockUtil.class.getName());
	/**
	 * 排他ロックを獲得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void acquireExclusiveLock(Vertx vertx, Handler<AsyncResult<LocalExclusiveLock.Lock>> completionHandler) {
		exclusiveLock_.acquire(vertx, completionHandler);
	}
	/**
	 * 排他ロックをリセットする.
	 * @param vertx vertx オブジェクト
	 */
	public static void resetExclusiveLock(Vertx vertx) {
		exclusiveLock_.reset(vertx);
	}

	private InterlockUtil() { }

	/**
	 * GridMaster インタロックを獲得する.
	 * @param vertx vertx オブジェクト
	 * @param value 獲得する値
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : 同じ値で獲得済みの場合, 別の値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : 同じ値で獲得済みの場合は LOCAL:WARN. 別の値で獲得済みの場合は LOCAL:ERROR にする.
	 * @param completionHandler the completion handler
	 */
	public static void lockGridMasterUnitId(Vertx vertx, String value, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		lockClusterWide_(vertx, MAP_KEY_GRID_MASTER_UNIT_ID, value, ignoreInconsistency, completionHandler);
	}
	/**
	 * GridMaster インタロックを開放する.
	 * インタロックが獲得中でない場合は LOCAL:WARN にする.
	 * 別の値で獲得中の場合は LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	public static void unlockGridMasterUnitId(Vertx vertx, String value, Handler<AsyncResult<Void>> completionHandler) {
		unlockClusterWide_(vertx, MAP_KEY_GRID_MASTER_UNIT_ID, value, completionHandler);
	}
	/**
	 * GridMaster インタロック値を取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void getGridMasterUnitId(Vertx vertx, Handler<AsyncResult<String>> completionHandler) {
		getClusterWide_(vertx, MAP_KEY_GRID_MASTER_UNIT_ID, completionHandler);
	}
	/**
	 * GridMaster インタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void resetGridMasterUnitId(Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
		resetClusterWide_(vertx, MAP_KEY_GRID_MASTER_UNIT_ID, completionHandler);
	}

	/**
	 * 融通インタロックを獲得する.
	 * @param vertx vertx オブジェクト
	 * @param value 獲得する値
	 * @param capacity 同時に獲得できるロックの数
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : capacity いっぱいの場合, 同じ値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : capacity いっぱいの場合 LOCAL:ERROR, 同じ値で獲得済みの場合は LOCAL:WARN にする
	 *        不整合は以下の通り.
	 *        - capacity を超える
	 *        - 指定した値で獲得済みである
	 * @param completionHandler the completion handler
	 */
	public static void lockDealId(Vertx vertx, String value, int capacity, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		lockLocalMultiple_(vertx, MAP_KEY_DEAL_ID, value, capacity, ignoreInconsistency, completionHandler);
	}
	/**
	 * 融通インタロックを開放する.
	 * インタロックが獲得中でない場合, 一つもロックがなければ LOCAL:WARN, 他に獲得中なら LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	public static void unlockDealId(Vertx vertx, String value, Handler<AsyncResult<Void>> completionHandler) {
		unlockLocalMultiple_(vertx, MAP_KEY_DEAL_ID, value, completionHandler);
	}
	/**
	 * 融通インタロック値を取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void getDealIds(Vertx vertx, Handler<AsyncResult<Collection<String>>> completionHandler) {
		getLocalMultiple_(vertx, MAP_KEY_DEAL_ID, completionHandler);
	}
	/**
	 * 融通インタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void resetDealId(Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
		resetLocalMultiple_(vertx, MAP_KEY_DEAL_ID, completionHandler);
	}

	////

	/**
	 * クラスタ全体でのインタロックを獲得する.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 獲得する値
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : 同じ値で獲得済みの場合, 別の値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : 同じ値で獲得済みの場合は LOCAL:WARN. 別の値で獲得済みの場合は LOCAL:ERROR にする.
	 * @param completionHandler the completion handler
	 */
	private static void lockClusterWide_(Vertx vertx, String key, String value, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					AsyncMap<String, String> lockMap = resMap.result();
					lockMap.putIfAbsent(key, value, resPutIfAbsent -> {
						if (resPutIfAbsent.succeeded()) {
							String existingValue = resPutIfAbsent.result();
							if (existingValue == null) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								String msg = (existingValue.equals(value)) ? "already locked with same " + key + " : " + existingValue : "already locked with different " + key + " : " + existingValue;
								if (ignoreInconsistency) {
									if (log.isDebugEnabled()) log.debug(msg);
									completionHandler.handle(Future.failedFuture(msg));
								} else if (existingValue.equals(value)) {
									ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
								} else {
									ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
								}
							}
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resPutIfAbsent.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	/**
	 * クラスタ全体でのインタロックを開放する.
	 * 獲得中でない場合は LOCAL:WARN にする.
	 * 別の値で獲得中の場合は LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	private static void unlockClusterWide_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					AsyncMap<String, String> lockMap = resMap.result();
					lockMap.removeIfPresent(key, value, resRemoveIfPresent -> {
						if (resRemoveIfPresent.succeeded()) {
							Boolean removed = resRemoveIfPresent.result();
							if (removed) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								lockMap.get(key, resGet -> {
									if (resGet.succeeded()) {
										String currentValue = resGet.result();
										String msg = "locked with different " + key + " : " + currentValue;
										if (null == currentValue) {
											ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
										} else {
											ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
										}
									} else {
										ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resGet.cause(), completionHandler);
									}
								});
							}
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resRemoveIfPresent.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	/**
	 * クラスタ全体でのインタロックを取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	private static void getClusterWide_(Vertx vertx, String key, Handler<AsyncResult<String>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					AsyncMap<String, String> lockMap = resMap.result();
					lockMap.get(key, resGet -> {
						if (resGet.succeeded()) {
							completionHandler.handle(Future.succeededFuture(resGet.result()));
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resGet.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}
	/**
	 * クラスタ全体でのインタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	private static void resetClusterWide_(Vertx vertx, String key, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					AsyncMap<String, String> lockMap = resMap.result();
					lockMap.remove(key, resRemove -> {
						if (resRemove.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resRemove.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}

	////

	/**
	 * ユニット内でのインタロックを獲得する.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 獲得する値
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : 同じ値で獲得済みの場合, 別の値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : 同じ値で獲得済みの場合は LOCAL:WARN. 別の値で獲得済みの場合は LOCAL:ERROR にする.
	 * @param completionHandler the completion handler
	 */
	@SuppressWarnings("unused") private static void lockLocal_(Vertx vertx, String key, String value, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME);
			String existingValue = lockMap.putIfAbsent(key, value);
			if (existingValue == null) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				String msg = (existingValue.equals(value)) ? "already locked with same " + key + " : " + existingValue : "already locked with different " + key + " : " + existingValue;
				if (ignoreInconsistency) {
					if (log.isDebugEnabled()) log.debug(msg);
					completionHandler.handle(Future.failedFuture(msg));
				} else if (existingValue.equals(value)) {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
				}
			}
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	/**
	 * ユニット内でのインタロックを開放する.
	 * 獲得中でない場合は LOCAL:WARN にする.
	 * 別の値で獲得中の場合は LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	@SuppressWarnings("unused") private static void unlockLocal_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME);
			boolean removed = lockMap.removeIfPresent(key, value);
			if (removed) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				String currentValue = lockMap.get(key);
				String msg = "locked with different " + key + " : " + currentValue;
				if (null == currentValue) {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
				}
			}
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	/**
	 * ユニット内でのインタロックを取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	@SuppressWarnings("unused") private static void getLocal_(Vertx vertx, String key, Handler<AsyncResult<String>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME);
			completionHandler.handle(Future.succeededFuture(lockMap.get(key)));
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}
	/**
	 * ユニット内でのインタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	@SuppressWarnings("unused") private static void resetLocal_(Vertx vertx, String key, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME);
			lockMap.remove(key);
			completionHandler.handle(Future.succeededFuture());
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}

	////

	/**
	 * ユニット内でのインタロックを獲得する.
	 * 複数のインタロックを保持できる.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 獲得する値
	 * @param capacity 同時に獲得できるロックの数
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : capacity いっぱいの場合, 同じ値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : capacity いっぱいの場合 LOCAL:ERROR, 同じ値で獲得済みの場合は LOCAL:WARN にする
	 *        不整合は以下の通り.
	 *        - capacity を超える
	 *        - 指定した値で獲得済みである
	 * @param completionHandler the completion handler
	 */
	private static void lockLocalMultiple_(Vertx vertx, String key, String value, int capacity, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			// 複数のロックを保持するのでローカル排他ロックを獲得して処理する
			acquireExclusiveLock(vertx, resExclusiveLock -> {
				if (resExclusiveLock.succeeded()) {
					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doLockLocalMultipleWithExclusiveLock_(vertx, key, value, capacity, ignoreInconsistency, resDoLockLocalMultipleWithExclusiveLock -> {
						lock.release();
						completionHandler.handle(resDoLockLocalMultipleWithExclusiveLock);
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	private static void doLockLocalMultipleWithExclusiveLock_(Vertx vertx, String key, String value, int capacity, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME + "_m_" + key);
		if (!lockMap.values().contains(value)) {
			if (lockMap.size() < capacity) {
				lockMap.put(value, value);
				completionHandler.handle(Future.succeededFuture());
			} else {
				String msg = key + " locks are occupied; capacity : " + capacity;
				if (ignoreInconsistency) {
					if (log.isDebugEnabled()) log.debug(msg);
					completionHandler.handle(Future.failedFuture(msg));
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
				}
			}
		} else {
			String msg = "already locked with same " + key + " : " + value;
			if (ignoreInconsistency) {
				if (log.isDebugEnabled()) log.debug(msg);
				completionHandler.handle(Future.failedFuture(msg));
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
			}
		}
	}
	/**
	 * ユニット内でのインタロックを開放する.
	 * インタロックが獲得中でない場合, 一つもロックがなければ LOCAL:WARN, 他に獲得中なら LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	private static void unlockLocalMultiple_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			// 複数のロックを保持するのでローカル排他ロックを獲得して処理する
			acquireExclusiveLock(vertx, resExclusiveLock -> {
				if (resExclusiveLock.succeeded()) {
					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doUnlockLocalMultipleWithExclusiveLock_(vertx, key, value, resDoLockLocalMultipleWithExclusiveLock -> {
						lock.release();
						completionHandler.handle(resDoLockLocalMultipleWithExclusiveLock);
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	private static void doUnlockLocalMultipleWithExclusiveLock_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME + "_m_" + key);
		if (lockMap.values().contains(value)) {
			lockMap.remove(value);
			completionHandler.handle(Future.succeededFuture());
		} else {
			String msg = "not locked with same " + key + " : " + value;
			if (lockMap.size() == 0) {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
			}
		}
	}
	/**
	 * ユニット内でのインタロック値を取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	private static void getLocalMultiple_(Vertx vertx, String key, Handler<AsyncResult<Collection<String>>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME + "_m_" + key);
			completionHandler.handle(Future.succeededFuture(lockMap.values()));
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}
	/**
	 * ユニット内でのインタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	private static void resetLocalMultiple_(Vertx vertx, String key, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME + "_m_" + key);
			lockMap.clear();
			completionHandler.handle(Future.succeededFuture());
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}

}
