package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * HWCONFIG を管理する Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * @author OES Project
 */
public class HwConfigKeeping extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(HwConfigKeeping.class);

	/**
	 * ファイルシステムから HWCONFIG を読み込む周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC = 5000L;

	/**
	 * HWCONFIG を保持しておくキャッシュ.
	 */
	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private String localFilePath_;
	private long localFileReadingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.hwConfigFile : HWCONFIG ファイルのパス
	 * タイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		localFilePath_ = VertxConfig.config.getString("hwConfigFile");
		if (log.isInfoEnabled()) log.info("hwConfigFile : " + localFilePath_);
		if (log.isInfoEnabled()) log.info("hwConfigFile.defaultRefreshingPeriodMsec : " + LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC);

		localFileReadingTimerHandler_(0L);
		if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
		startFuture.complete();
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
	 * ファイルシステムから HWCONFIG を読み込むタイマ設定.
	 * 待ち時間は {@code HWCONFIG.refreshingPeriodMsec} ( デフォルト値 {@link #LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC} ).
	 */
	private void setLocalFileReadingTimer_() {
		Long delay = cache.getLong(LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setLocalFileReadingTimer_(delay);
	}
	/**
	 * ファイルシステムから HWCONFIG を読み込むタイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setLocalFileReadingTimer_(long delay) {
		localFileReadingTimerId_ = vertx.setTimer(delay, this::localFileReadingTimerHandler_);
	}
	/**
	 * ファイルシステムから HWCONFIG を読み込むタイマ処理.
	 * @param timerId タイマ ID
	 */
	private void localFileReadingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != localFileReadingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", localFileReadingTimerId_ : " + localFileReadingTimerId_);
			return;
		}
		doReadLocalFile_(resRead -> {
			if (resRead.succeeded()) {
				// キャッシュしておく
				cache.setJsonObject(resRead.result());
			} else {
				// 読み込めなかったら
				if (cache.isNull()) {
					// キャッシュが空なら ( 動けないので ) エラーにする
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, resRead.cause());
				} else {
					// キャッシュがあったら ( 動けるので ) スルー
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, resRead.cause());
				}
			}
			setLocalFileReadingTimer_();
		});
	}

	private void doReadLocalFile_(Handler<AsyncResult<JsonObject>> completionHandler) {
		vertx.fileSystem().readFile(localFilePath_, resFile -> {
			if (resFile.succeeded()) {
				JsonObjectUtil.toJsonObject(resFile.result(), resToJsonObject -> {
					if (resToJsonObject.succeeded()) {
						JsonObject jsonObject = resToJsonObject.result();
						completionHandler.handle(Future.succeededFuture(jsonObject));
					} else {
						completionHandler.handle(resToJsonObject);
					}
				});
			} else {
				completionHandler.handle(Future.failedFuture(resFile.cause()));
			}
		});
	}

	////

	/**
	 * HWCONFIG キャッシュからバッテリ容量を取得する.
	 * @return バッテリ容量 [Wh]. {@code null} の可能性あり
	 */
	public static Float batteryNominalCapacityWh() {
		return cache.getFloat("batteryNominalCapacityWh");
	}
	/**
	 * HWCONFIG キャッシュからグリッド電流容量を取得する.
	 * @return グリッド電流容量 [A]. {@code null} の可能性あり
	 */
	public static Float gridCurrentCapacityA() {
		return cache.getFloat("gridCurrentCapacityA");
	}
	/**
	 * HWCONFIG キャッシュからグリッド電流エラー許容値を取得する.
	 * @return グリッド電流エラー許容値 [A]. {@code null} の可能性あり
	 */
	public static Float gridCurrentAllowanceA() {
		return cache.getFloat("gridCurrentAllowanceA");
	}
	/**
	 * HWCONFIG キャッシュから電圧リファレンス移動時のドループ率を取得する.
	 * @return ドループ率. {@code null} の可能性あり
	 */
	public static Float droopRatio() {
		return cache.getFloat("droopRatio");
	}
	/**
	 * HWCONFIG キャッシュから最も効率の良いバッテリ電圧とグリッド電圧の比を取得する.
	 * @return 最も効率の良いバッテリ電圧とグリッド電圧の比. {@code null} の可能性あり
	 */
	public static Float efficientBatteryGridVoltageRatio() {
		return cache.getFloat("efficientBatteryGridVoltageRatio");
	}

	/**
	 * HWCONFIG キャッシュから静的安全性チェックで用いる上限下限値のセットを取得する.
	 * @return 静的安全性チェックで用いる上限下限値のセット {@link JsonObject}. {@code null} の可能性あり
	 */
	public static JsonObject safetyRange() {
		return cache.getJsonObject("safety", "range");
	}
	/**
	 * HWCONFIG キャッシュから静的安全性チェックで用いる上限下限値をパスで取得する.
	 * @param keys パス
	 * @return パスで指定した静的安全性チェックで用いる上限下限値 {@link JsonObject}. {@code null} の可能性あり
	 */
	public static JsonObject safetyRange(String... keys) {
		return JsonObjectUtil.getJsonObject(safetyRange(), keys);
	}

}
