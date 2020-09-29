package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.UUID;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 全ユニットのユニットデータを収集する Verticle.
 * {@link GridMaster} から起動される.
 * 定期的に全ユニットに対しデータ収集要求をブロードキャストする.
 * 返ってきたユニットデータを受信し保持する.
 * @author OES Project
 */
public class DataCollection extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DataCollection.class);

	/**
	 * データ収集周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_DATA_COLLECTION_PERIOD_MSEC = 5000L;
	/**
	 * HTTP リクエストのタイムアウト時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_DATA_COLLECTION_TIMEOUT_MSEC = 2000L;

	/**
	 * 全ユニットのユニットデータを保持しておくキャッシュ.
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution#unitDataCache} とは独立.
	 */
	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private long dataCollectionTimerId_ = 0L;
	private long lastDataCollectionMillis_ = 0L;
	private boolean stopped_ = false;
	private Future<Void> stopFuture_ = Future.succeededFuture();

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 定期的に全ユニットのユニットデータを収集しキャッシュを更新するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startInternalUnitDatasService_(resInternalUnitDatas -> {
			if (resInternalUnitDatas.succeeded()) {
				dataCollectionTimerHandler_(0L);
				if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
				startFuture.complete();
			} else {
				startFuture.fail(resInternalUnitDatas.cause());
			}
		});
	}

	/**
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * 定期的に全ユニットのユニットデータを収集する処理中に作成する {@link io.vertx.core.eventbus.EventBus} サービスの伝搬中に {@link #stop(Future)} が完了しないよう {@link #stopFuture_} を使って排他制御する.
	 * @param stopFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop(Future<Void> stopFuture) throws Exception {
		stopped_ = true;
		/**
		 * 以下データ収集処理との競合を避けるための細工.
		 * データ収集用アドレスの伝搬中に undeploy が起きると Vert.x 的にエラーになる.
		 * なので伝搬中は stopFuture を complete() しないようにする.
		 */
		// データ収集の都度 stopFuture_ オブジェクトが新しくなるため f で参照しておく
		Future<Void> f = this.stopFuture_;
		// タイムアウトを仕込む
		long timerId = vertx.setTimer(DEFAULT_DATA_COLLECTION_PERIOD_MSEC, t -> {
			if (!f.isComplete()) {
				// まだ complete でなければエラーを出して fail() させる
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "stopFuture_ timed out", f);
			}
		});
		/**
		 * データ収集用アドレスの伝搬中でなければ即座に呼ばれる.
		 * 伝搬中なら伝搬が終わったところで呼ばれる.
		 * あるいはタイムアウトで呼ばれる.
		*/
		f.setHandler(ar -> {
			// 上で仕込んだタイムアウト用タイマをキャンセルする
			vertx.cancelTimer(timerId);
			if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
			stopFuture.complete();
		});
	}

	////

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.GridMaster#urgentUnitDatas()}
	 * 範囲 : ローカル
	 * 処理 : 全ユニットのユニットデータを取得する.
	 * 　　   GridMaster が定期的にリフレッシュしているキャッシュ値ではなく全ユニットに問合せ収集した結果を返す.
	 * 　　   メッセージボディでタイムスタンプが送られその値より直近のデータ収集が新しい場合はデータ収集を実行せずキャッシュを返す.
	 * 　　   収集時ヘッダに urgent 指定をしないため各ユニットで定期的にリフレッシュしてあるキャッシュデータが集まる.
	 * メッセージボディ : これより新しければ収集不要タイムスタンプ [{@link Number}]
	 * メッセージヘッダ : なし
	 * レスポンス : 全ユニットのユニットデータ [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startInternalUnitDatasService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Number>localConsumer(ServiceAddress.GridMaster.urgentUnitDatas(), req -> {
			Number noNeedToRefreshIfNewerThan = req.body();
			if (!cache.isEmpty() && noNeedToRefreshIfNewerThan != null && noNeedToRefreshIfNewerThan.longValue() < lastDataCollectionMillis_) {
				// 何やら時刻が送られてきた場合
				// その時刻よりキャッシュ時刻の方が新しければ新たにデータ収集することなくキャッシュを返す
				if (log.isDebugEnabled()) log.debug("no need to refresh ; noNeedToRefreshIfNewerThan : " + noNeedToRefreshIfNewerThan + " , lastDataCollectionMillis_ : " + lastDataCollectionMillis_);
				req.reply(cache.jsonObject());
			} else {
				// 実際にデータ収集してその結果を返す
				getDatas_(res -> {
					if (res.succeeded()) {
						req.reply(res.result());
					} else {
						req.fail(-1, res.cause().getMessage());
					}
				});
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * データ収集タイマ設定.
	 * 待ち時間は {@code POLICY.gridMaster.dataCollectionPeriodMsec} ( デフォルト値 {@link #DEFAULT_DATA_COLLECTION_PERIOD_MSEC} ).
	 */
	private void setDataCollectionTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_DATA_COLLECTION_PERIOD_MSEC, "gridMaster", "dataCollectionPeriodMsec");
		setDataCollectionTimer_(delay);
	}
	/**
	 * データ収集タイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setDataCollectionTimer_(long delay) {
		dataCollectionTimerId_ = vertx.setTimer(delay, this::dataCollectionTimerHandler_);
	}
	/**
	 * データ収集タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void dataCollectionTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != dataCollectionTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", dataCollectionTimerId_ : " + dataCollectionTimerId_);
			return;
		}
		// 定期的なデータ収集はまあ適当でよいので...
		if (lastDataCollectionMillis_ != 0L) {
			long millisAfterLastDataCollection = (System.currentTimeMillis() - lastDataCollectionMillis_);
			Long period = PolicyKeeping.cache().getLong(DEFAULT_DATA_COLLECTION_PERIOD_MSEC, "gridMaster", "dataCollectionPeriodMsec");
			if (millisAfterLastDataCollection < period) {
				// 前回のデータ収集からタイマ周期時間経過していない場合は差分時間でタイマを再セットし何もせず終わる
				setDataCollectionTimer_(period - millisAfterLastDataCollection);
				return;
			}
		}
		// データ収集し
		getDatas_(res -> {
			// また次のタイマをセットする
			setDataCollectionTimer_();
		});
	}
	private void getDatas_(Handler<AsyncResult<JsonObject>> completionHandler) {
		// データ収集を始めた時刻を仮に覚えておく
		long ts = System.currentTimeMillis(); // TODO : このやり方ちょっといまいちだなぁ
		new DataCollection_().execute_(res -> {
			if (res.succeeded()) {
				// データ収集が終わったら仮に覚えてある "始めた時刻" を正式に覚える
				lastDataCollectionMillis_ = ts;
				// キャッシュしておく
				cache.setJsonObject(res.result());
				if (log.isInfoEnabled()) log.info(res.result().size() + " unit data collected");
			}
			completionHandler.handle(res);
		});
	}

	////

	/**
	 * データ収集処理クラス.
	 * @author OES Project
	 */
	private class DataCollection_ {
		private long timeoutTimerId_ = 0L;
		/**
		 * インスタンス作成.
		 */
		private DataCollection_() {
		}
		private void execute_(Handler<AsyncResult<JsonObject>> completionHandler) {
			// 使い捨ての返信用アドレスを生成する
			String replyAddress = UUID.randomUUID().toString();
			JsonObject result = new JsonObject();
			int numberOfMembers = PolicyKeeping.numberOfMembers();
			// アドレス展開中に Verticle 本体が stop するのを防ぐための小細工 ( stopFuture_ ) を仕込む
			Future<Void> registerFuture = Future.future();
			stopFuture_ = registerFuture;
//			if (log.isInfoEnabled()) log.info("DataCollection:" + replyAddress + " begin");
			// 使い捨てのアドレスで EventBus の口を開く
			MessageConsumer<JsonObject> dataConsumer = vertx.eventBus().<JsonObject>consumer(replyAddress);
			// 受信処理を登録する
			dataConsumer.handler(rep -> {
				// メッセージを受け取ったら
				JsonObject aData = rep.body();
				// ユニット ID を確認して POLICY で定義されているメンバかどうか確認する
				String unitId = JsonObjectUtil.getString(aData, "oesunit", "id");
				boolean isMember = PolicyKeeping.isMember(unitId);
				if (!isMember) {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "unit data received from illegal unit : " + unitId + " ; unit data : " + aData);
				} else {
//					if (log.isInfoEnabled()) log.info("DataCollection:" + replyAddress + " received from : " + unitId);
					result.put(unitId, aData);
					if (numberOfMembers <= result.size()) {
						// POLICY で定義されているメンバの数だけ集まったら即座に終了する
						// タイムアウト用のタイマをキャンセルし
						if (vertx.cancelTimer(timeoutTimerId_)) {
							// 開いた口を閉じる
							dataConsumer.unregister(resUnregister -> {
								if (resUnregister.succeeded()) {
//									if (log.isInfoEnabled()) log.info("DataCollection:" + replyAddress + " end");
									// 結果を返す
									completionHandler.handle(Future.succeededFuture(result));
								} else {
									ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resUnregister.cause(), completionHandler);
								}
							});
						}
					}
				}
			}).exceptionHandler(t -> {
				dataConsumer.unregister(resUnregister -> {
					if (resUnregister.succeeded()) {
						// nop
					} else {
						ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resUnregister.cause());
					}
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, t, completionHandler);
				});
			}).completionHandler(res -> {
				// 口を開け終わった ( EventBus アドレスの伝搬が終わった ) ら
				// EventBus アドレスの伝搬が終わったのでもう Verticle#stop() の阻止を解除する
				registerFuture.complete();
				if (res.succeeded()) {
					// 返信用の使い捨てアドレスを仕込んで
					DeliveryOptions options = new DeliveryOptions().addHeader("replyAddress", replyAddress).addHeader("gridMasterUnitId", ApisConfig.unitId());
//					if (log.isInfoEnabled()) log.info("DataCollection:" + replyAddress + " requested");
					// ユニットデータ収集要求を publish する
					vertx.eventBus().publish(ServiceAddress.Controller.unitDatas(), null, options);
					Long dataCollectionTimeoutMsec = PolicyKeeping.cache().getLong(DEFAULT_DATA_COLLECTION_TIMEOUT_MSEC, "gridMaster", "dataCollectionTimeoutMsec");
					// タイムアウトを仕込む
					// 待ち時間は {@code POLICY.gridMaster.dataCollectionTimeoutMsec} ( デフォルト値 {@link #DEFAULT_DATA_COLLECTION_TIMEOUT_MSEC} ).
					timeoutTimerId_ = vertx.setTimer(dataCollectionTimeoutMsec, t -> {
						// タイムアウトしたら
						// 開いた口を閉じる
						dataConsumer.unregister(resUnregister -> {
							if (resUnregister.succeeded()) {
								if (!result.isEmpty()) {
									// データが一件でも返ってきていたらまあ問題なし
//									log.error("DataCollection:" + replyAddress + " end ( " + result.size() + " )");
									// 結果を返す
									completionHandler.handle(Future.succeededFuture(result));
								} else {
									// 一件も返ってこなければ一大事
//									log.error("DataCollection:" + replyAddress + " end ( 0 )");
									String msg = "no unit data collected";
									if (stopped_) {
										// 実は Verticle が stop していた
										// → 問題なし
										ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
									} else {
										// これは GLOBAL ERROR !
										ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg, completionHandler);
									}
								}
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resUnregister.cause(), completionHandler);
							}
						});
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, res.cause(), completionHandler);
				}
			});
		}
	}

	////

	/**
	 * 電圧リファレンスを担っているユニットの ID を取得する.
	 * 定期的に収集して更新しているデータを元に決定する.
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution#voltageReferenceUnitId()} とは別のデータで判定.
	 * @return 電圧リファレンスがある場合そのユニットの ID.
	 *         なければ {@code null}.
	 *         データがまだキャッシュされていない場合も {@code null}.
	 */
	public static String voltageReferenceUnitId() {
		JsonObject unitData = cache.jsonObject();
		if (unitData != null) {
			for (String aUnitId : unitData.fieldNames()) {
				JsonObject aUnitData = unitData.getJsonObject(aUnitId);
				DDCon.Mode aMode = DDCon.modeFromCode(JsonObjectUtil.getString(aUnitData, "dcdc", "status", "status"));
				if (DDCon.Mode.VOLTAGE_REFERENCE == aMode) {
					if (log.isInfoEnabled()) log.info("voltage reference unit : " + aUnitId);
					return aUnitId;
				}
			}
		} else {
			if (log.isInfoEnabled()) log.info("no unit data");
		}
		if (log.isInfoEnabled()) log.info("no voltage reference unit found");
		return null;
	}

}
