package jp.co.sony.csl.dcoes.apis.common.util.vertx;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import jp.co.sony.csl.dcoes.apis.common.util.EncryptionUtil;

/**
 * Vert.x の Cluster Wide Map を暗号化して利用するためのユーティリティ.
 * @author OES Project
 */
public class EncryptedClusterWideMapUtil {
	private static final Logger log = LoggerFactory.getLogger(EncryptedClusterWideMapUtil.class);

	private static final char CLASS_VALUE_DELIMITER = ':';
	private static final String CLASS_CODE_STRING = "s";
	private static final String CLASS_CODE_JSON_OBJECT = "jo";
	private static final String CLASS_CODE_JSON_ARRAY = "ja";

	private static boolean SECURE_CLUSTER = VertxConfig.securityEnabled();
	static {
		if (SECURE_CLUSTER) {
			if (log.isInfoEnabled()) log.info("ClusterWideMap will be encrypted");
		}
	}

	/**
	 * 値を暗号化して保持する {@link AsyncMap} インスタンスを取得する.
	 * 暗号処理のシードは {@link EncryptionUtil#initialize(Handler) 暗号処理系のデフォルト値} が使われる.
	 * {@link VertxConfig#securityEnabled()} が {@code false} なら暗号化しない.
	 * @param <K> the type of keys maintained by this map
	 * @param <V> the type of mapped values
	 * @param vertx vertx インスタンス
	 * @param name the name of the map
	 * @param resultHandler the map will be returned asynchronously in this handler
	 */
	public static <K, V> void getEncryptedClusterWideMap(Vertx vertx, String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
		getEncryptedClusterWideMap(vertx, name, null, resultHandler);
	}
	/**
	 * 値を暗号化して保持する {@link AsyncMap} インスタンスを取得する.
	 * {@link VertxConfig#securityEnabled()} が {@code false} なら暗号化しない.
	 * @param <K> the type of keys maintained by this map
	 * @param <V> the type of mapped values
	 * @param vertx vertx インスタンス
	 * @param name the name of the map
	 * @param seed 暗号処理のためのシード. {@code null} なら {@link EncryptionUtil#initialize(Handler) 暗号処理系のデフォルト値} が使われる
	 * @param resultHandler the map will be returned asynchronously in this handler
	 */
	public static <K, V> void getEncryptedClusterWideMap(Vertx vertx, String name, String seed, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
		if (SECURE_CLUSTER) {
			vertx.sharedData().<K, String>getClusterWideMap(name, resMap -> {
				if (resMap.succeeded()) {
					AsyncMap<K, V> result;
					try {
						result = new EncryptedAsyncMap<K, V>(resMap.result(), seed);
					} catch (Exception e) {
						log.error(e);
						resultHandler.handle(Future.failedFuture(e));
						return;
					}
					resultHandler.handle(Future.succeededFuture(result));
				} else {
					resultHandler.handle(Future.failedFuture(resMap.cause()));
				}
			});
		} else {
			vertx.sharedData().<K, V>getClusterWideMap(name, resultHandler);
		}
	}

	/**
	 * 内容を暗号化して保持する {@link AsyncMap} 実装.
	 * 文字列はそのまま暗号化する.
	 * {@link JsonArray} および {@link JsonObject} はシリアライズ結果を暗号化する.
	 * @author OES Project
	 * @param <K> the type of keys maintained by this map
	 * @param <V> the type of mapped values
	 */
	private static class EncryptedAsyncMap<K, V> implements AsyncMap<K, V> {

		private final AsyncMap<K, String> delegate_;
		private final Cipher cipher_;
		private final String seed_;

		/**
		 * インスタンスを作成する.
		 * @param other wrap する asyncmap オブジェクト
		 * @param seed シード
		 * @throws GeneralSecurityException {@link EncryptionUtil#generateCipher()}
		 */
		private EncryptedAsyncMap(AsyncMap<K, String> other, String seed) throws GeneralSecurityException {
			delegate_ = other;
			cipher_ = EncryptionUtil.generateCipher();
			seed_ = seed;
		}

		////

		/**
		 * 暗号化されたエントリを復号する.
		 * 元の型が {@link JsonArray} および {@link JsonObject} ならシリアライズされた状態なので復元する.
		 * @param entry 暗号化された {@link String}
		 * @param resultHandler this will be called some time later with the async result.
		 */
		private void decrypt_(String entry, Handler<AsyncResult<V>> resultHandler) {
			int pos = entry.indexOf(CLASS_VALUE_DELIMITER);
			if (0 < pos) {
				String clazz = entry.substring(0, pos);
				String value = entry.substring(++pos);
				String decrypted;
				try {
					decrypted = EncryptionUtil.decrypt(value, cipher_, seed_);
				} catch (Exception e) {
					log.error(e);
					resultHandler.handle(Future.failedFuture(e));
					return;
				}
				switch (clazz) {
				case CLASS_CODE_STRING:
					@SuppressWarnings("unchecked") V s = (V) decrypted;
					resultHandler.handle(Future.succeededFuture(s));
					break;
				case CLASS_CODE_JSON_OBJECT:
					JsonObjectUtil.toJsonObject(decrypted, res -> {
						if (res.succeeded()) {
							@SuppressWarnings("unchecked") V jo = (V) res.result();
							resultHandler.handle(Future.succeededFuture(jo));
						} else {
							resultHandler.handle(Future.failedFuture(res.cause()));
						}
					});
					break;
				case CLASS_CODE_JSON_ARRAY:
					JsonObjectUtil.toJsonArray(decrypted, res -> {
						if (res.succeeded()) {
							@SuppressWarnings("unchecked") V ja = (V) res.result();
							resultHandler.handle(Future.succeededFuture(ja));
						} else {
							resultHandler.handle(Future.failedFuture(res.cause()));
						}
					});
					break;
				default:
					resultHandler.handle(Future.failedFuture("unsupported class code : " + clazz));
				}
			} else {
				resultHandler.handle(Future.failedFuture("illegal entry : " + entry));
			}
		}
		/**
		 * エントリを暗号化する.
		 * {@link String} はそのまま暗号化する.
		 * {@link JsonArray} および {@link JsonObject} はシリアライズした結果を暗号化する.
		 * それ以外の型のオブジェクトはエラーを返す.
		 * @param obj 暗号化対象オブジェクト
		 * @param resultHandler this will be called some time later with the async result.
		 */
		private void encrypt_(Object obj, Handler<AsyncResult<String>> resultHandler) {
			String clazz = null;
			String value = null;
			if (obj instanceof String) {
				clazz = CLASS_CODE_STRING;
				value = (String) obj;
			} else if (obj instanceof JsonObject) {
				clazz = CLASS_CODE_JSON_OBJECT;
				value = ((JsonObject) obj).encode();
			} else if (obj instanceof JsonArray) {
				clazz = CLASS_CODE_JSON_ARRAY;
				value = ((JsonArray) obj).encode();
			}
			if (clazz != null && value != null) {
				String encrypted;
				try {
					encrypted = EncryptionUtil.encrypt(value, cipher_, seed_);
				} catch (Exception e) {
					log.error(e);
					resultHandler.handle(Future.failedFuture(e));
					return;
				}
				resultHandler.handle(Future.succeededFuture(clazz + CLASS_VALUE_DELIMITER + encrypted));
			} else {
				resultHandler.handle(Future.failedFuture("unsupported class : " + obj.getClass().getName()));
			}
		}

		////

		/**
		 * {@inheritDoc}
		 */
		@Override public void get(K k, Handler<AsyncResult<V>> resultHandler) {
			delegate_.get(k, res -> {
				if (res.succeeded()) {
					String entry = res.result();
					if (entry != null) {
						decrypt_(entry, resultHandler);
					} else {
						resultHandler.handle(Future.succeededFuture(null));
					}
				} else {
					resultHandler.handle(Future.failedFuture(res.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void put(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
			encrypt_(v, res -> {
				if (res.succeeded()) {
					delegate_.put(k, res.result(), completionHandler);
				} else {
					completionHandler.handle(Future.failedFuture(res.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void put(K k, V v, long ttl, Handler<AsyncResult<Void>> completionHandler) {
			encrypt_(v, res -> {
				if (res.succeeded()) {
					delegate_.put(k, res.result(), ttl, completionHandler);
				} else {
					completionHandler.handle(Future.failedFuture(res.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> completionHandler) {
			encrypt_(v, resEncrypt -> {
				if (resEncrypt.succeeded()) {
					delegate_.putIfAbsent(k, resEncrypt.result(), resPutIfAbsent -> {
						if (resPutIfAbsent.succeeded()) {
							String entry = resPutIfAbsent.result();
							if (entry != null) {
								decrypt_(entry, completionHandler);
							} else {
								completionHandler.handle(Future.succeededFuture(null));
							}
						} else {
							completionHandler.handle(Future.failedFuture(resPutIfAbsent.cause()));
						}
					});
				} else {
					completionHandler.handle(Future.failedFuture(resEncrypt.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void putIfAbsent(K k, V v, long ttl, Handler<AsyncResult<V>> completionHandler) {
			encrypt_(v, resEncrypt -> {
				if (resEncrypt.succeeded()) {
					delegate_.putIfAbsent(k, resEncrypt.result(), ttl, resPutIfAbsent -> {
						if (resPutIfAbsent.succeeded()) {
							String entry = resPutIfAbsent.result();
							if (entry != null) {
								decrypt_(entry, completionHandler);
							} else {
								completionHandler.handle(Future.succeededFuture(null));
							}
						} else {
							completionHandler.handle(Future.failedFuture(resPutIfAbsent.cause()));
						}
					});
				} else {
					completionHandler.handle(Future.failedFuture(resEncrypt.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void remove(K k, Handler<AsyncResult<V>> resultHandler) {
			delegate_.remove(k, res -> {
				if (res.succeeded()) {
					String entry = res.result();
					if (entry != null) {
						decrypt_(entry, resultHandler);
					} else {
						resultHandler.handle(Future.succeededFuture(null));
					}
				} else {
					resultHandler.handle(Future.failedFuture(res.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
			encrypt_(v, resEncrypt -> {
				if (resEncrypt.succeeded()) {
					delegate_.removeIfPresent(k, resEncrypt.result(), resultHandler);
				} else {
					resultHandler.handle(Future.failedFuture(resEncrypt.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
			encrypt_(v, resEncrypt -> {
				if (resEncrypt.succeeded()) {
					delegate_.replace(k, resEncrypt.result(), resReplace -> {
						if (resReplace.succeeded()) {
							String entry = resReplace.result();
							if (entry != null) {
								decrypt_(entry, resultHandler);
							} else {
								resultHandler.handle(Future.succeededFuture(null));
							}
						} else {
							resultHandler.handle(Future.failedFuture(resReplace.cause()));
						}
					});
				} else {
					resultHandler.handle(Future.failedFuture(resEncrypt.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
			encrypt_(oldValue, resEncryptOld -> {
				if (resEncryptOld.succeeded()) {
					encrypt_(newValue, resEncryptNew -> {
						if (resEncryptNew.succeeded()) {
							delegate_.replaceIfPresent(k, resEncryptOld.result(), resEncryptNew.result(), resultHandler);
						} else {
							resultHandler.handle(Future.failedFuture(resEncryptNew.cause()));
						}
					});
				} else {
					resultHandler.handle(Future.failedFuture(resEncryptOld.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void clear(Handler<AsyncResult<Void>> resultHandler) {
			delegate_.clear(resultHandler);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void size(Handler<AsyncResult<Integer>> resultHandler) {
			delegate_.size(resultHandler);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void keys(Handler<AsyncResult<Set<K>>> resultHandler) {
			delegate_.keys(resultHandler);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void values(Handler<AsyncResult<List<V>>> resultHandler) {
			delegate_.values(resValues -> {
				if (resValues.succeeded()) {
					List<String> values = resValues.result();
					@SuppressWarnings("rawtypes") List<Future> futures = new ArrayList<>(values.size());
					for (String value : values) {
						Future<V> future = Future.future();
						decrypt_(value, future);
						futures.add(future);
					}
					CompositeFuture.all(futures).setHandler(ar -> {
						if (ar.succeeded()) {
							List<V> result = new ArrayList<>(ar.result().size());
							for (int i = ar.result().size(); 0 < i--;) {
								result.add(ar.result().resultAt(i));
							}
							resultHandler.handle(Future.succeededFuture(result));
						} else {
							resultHandler.handle(Future.failedFuture(ar.cause()));
						}
					});
				} else {
					resultHandler.handle(Future.failedFuture(resValues.cause()));
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override public void entries(Handler<AsyncResult<Map<K, V>>> resultHandler) {
			delegate_.entries(resEntries -> {
				if (resEntries.succeeded()) {
					Map<K, String> entries = resEntries.result();
					List<K> keys = new ArrayList<>(entries.size());
					for (K key : entries.keySet()) keys.add(key);
					@SuppressWarnings("rawtypes") List<Future> futures = new ArrayList<>(keys.size());
					for (K key : keys) {
						Future<V> future = Future.future();
						decrypt_(entries.get(key), future);
						futures.add(future);
					}
					CompositeFuture.all(futures).setHandler(ar -> {
						if (ar.succeeded()) {
							Map<K, V> result = new HashMap<>(ar.result().size());
							for (int i = ar.result().size(); 0 < i--;) {
								result.put(keys.get(i), ar.result().resultAt(i));
							}
							resultHandler.handle(Future.succeededFuture(result));
						} else {
							resultHandler.handle(Future.failedFuture(ar.cause()));
						}
					});
				} else {
					resultHandler.handle(Future.failedFuture(resEntries.cause()));
				}
			});
		}

	}

}
