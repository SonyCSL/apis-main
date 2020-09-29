package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.EncryptedClusterWideMapUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * POLICY を管理する Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * @author OES Project
 */
public class PolicyKeeping extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(PolicyKeeping.class);

	/**
	 * ファイルシステムから POLICY を読み込む際のタイムアウト時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long LOCAL_FILE_DEFAULT_READ_TIMEOUT_MSEC = 60000L;
	/**
	 * ファイルシステムから POLICY を読み込む周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC = 5000L;
	/**
	 * サービスセンタから POLICY を取得する周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC = 60000L;

	/**
	 * ファイルシステムの POLICY を保持しておくキャッシュ.
	 */
	private static final JsonObjectWrapper localFileCache_ = new JsonObjectWrapper();
	/**
	 * サービスセンタの POLICY を保持しておくキャッシュ.
	 */
	private static final JsonObjectWrapper controlCenterCache_ = new JsonObjectWrapper();

	/**
	 * POLICY を保持しておくキャッシュを取得する.
	 * サービスセンタのキャッシュがあればそちらを優先する.
	 * なければローカルファイルのキャッシュを返す.
	 * @return POLICY のキャッシュ.
	 */
	public static JsonObjectWrapper cache() {
		return (!controlCenterCache_.isNull()) ? controlCenterCache_ : localFileCache_;
	}

	private String localFilePath_;
	private boolean controlCenterEnabled_ = false;
	private String controlCenterAccount_;
	private String controlCenterPassword_;
	private long localFileReadingTimerId_ = 0L;
	private long controlCenterAccessingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.policyFile : POLICY ファイルのパス
	 * - CONFIG.controlCenter.enabled : サービスセンタ機能を使用するかフラグ [{@link Boolean}]
	 * - CONFIG.controlCenter.account : サービスセンタの認証アカウント
	 * - CONFIG.controlCenter.password : サービスセンタ認証パスワード ( MD5 ハッシュ済み文字列 )
	 * ファイルが読み込めるか確認する.
	 * POLICY の内容がクラスタ内で一致しているか確認する.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * ローカルファイルを定期的に再読み込みするタイマを起動する.
	 * サービスセンタ機能を使用する場合, 定期的にアクセスするタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		localFilePath_ = VertxConfig.config.getString("policyFile");
		controlCenterEnabled_ = VertxConfig.config.getBoolean(Boolean.TRUE, "controlCenter", "enabled");
		if (controlCenterEnabled_) {
			controlCenterAccount_ = VertxConfig.config.getString("controlCenter", "account");
			controlCenterPassword_ = VertxConfig.config.getString("controlCenter", "password");
		}
		if (log.isInfoEnabled()) log.info("policyFile : " + localFilePath_);
		if (log.isInfoEnabled()) log.info("policyFile.defaultRefreshingPeriodMsec : " + LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC);
		if (log.isInfoEnabled()) log.info("controlCenter.enabled : " + controlCenterEnabled_);
		if (log.isInfoEnabled()) log.info("controlCenter.account : " + controlCenterAccount_);
		if (log.isInfoEnabled()) log.info("controlCenter.defaultRefreshingPeriodMsec : " + CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC);

		// 初期化処理 ( POLICY が読み込めるかを確認する )
		init_(resInit -> {
			if (resInit.succeeded()) {
				startPolicyService_(resPolicy -> {
					if (resPolicy.succeeded()) {
						localFileReadingTimerHandler_(0L);
						// サービスセンタの機能を使用するならタイマを仕込む
						if (controlCenterEnabled_) {
							controlCenterAccessingTimerHandler_(0L);
						}
						if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
						startFuture.complete();
					} else {
						startFuture.fail(resPolicy.cause());
					}
				});
			} else {
				startFuture.fail(resInit.cause());
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
	 * 起動時の初期化処理.
	 * ファイルシステムから POLICY を読み込む.
	 * 読み込みに失敗したり一定時間内に完了しなかったらアウト.
	 * @param completionHandler the completion handler
	 */
	private void init_(Handler<AsyncResult<Void>> completionHandler) {
		Boolean[] handled = new Boolean[1];
		Long localFileReadTimeoutMsec_ = VertxConfig.config.getLong(LOCAL_FILE_DEFAULT_READ_TIMEOUT_MSEC, "policyFileReadTimeoutMsec");
		vertx.setTimer(localFileReadTimeoutMsec_, timerId -> {
			if (handled[0] == null) {
				// 読み込み処理が完了する前にタイムアウトが起きた
				handled[0] = Boolean.TRUE;
				completionHandler.handle(Future.failedFuture("POLICY read timed out : " + localFileReadTimeoutMsec_ + "ms"));
			}
		});
		doReadLocalFile_(resRead -> {
			if (handled[0] == null) {
				// タイムアウトが起きる前に読み込み処理が完了した
				handled[0] = Boolean.TRUE;
				if (resRead.succeeded()) {
					// キャッシュしておく
					localFileCache_.setJsonObject(resRead.result());
					// クラスタ内で POLICY の内容が同じことを保証する
					checkClusterPolicy_(localFileCache_.jsonObject(), completionHandler);
				} else {
					completionHandler.handle(Future.failedFuture(resRead.cause()));
				}
			}
		});
	}
	private static final String MAP_NAME = PolicyKeeping.class.getName();
	private static final String MAP_KEY_POLICY = "policy";
	/**
	 * クラスタ内で POLICY の内容が同じことを保証する.
	 * @param policy POLICY オブジェクト
	 * @param completionHandler the completion handler
	 */
	private void checkClusterPolicy_(JsonObject policy, Handler<AsyncResult<Void>> completionHandler) {
		String myValue = policy.encode();
		EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				AsyncMap<String, String> theMap = resMap.result();
				// "無ければ置く" 操作を試してみる
				theMap.putIfAbsent(MAP_KEY_POLICY, myValue, resPutIfAbsent -> {
					if (resPutIfAbsent.succeeded()) {
						String existingValue = resPutIfAbsent.result();
						if (existingValue == null) {
							// 置けた ( クラスタ内で最初に起動した )
							// → OK
							completionHandler.handle(Future.succeededFuture());
						} else {
							// 置けなかった ( もうあった )
							if (existingValue.equals(myValue)) {
								// クラスタ上の値と自分の値が同じ
								// → OK
								completionHandler.handle(Future.succeededFuture());
							} else {
								// クラスタ上の値と異なる
								// → NG
								completionHandler.handle(Future.failedFuture("my POLICY is different from cluster's"));
							}
						}
					} else {
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
	 * アドレス : {@link ServiceAddress#policy()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットの POLICY を取得する.
	 * 　　   定期的にリフレッシュされているキャッシュ内容を返す.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : POLICY 情報 [{@link JsonObject}]
	 * @param completionHandler the completion handler
	 */
	private void startPolicyService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.policy(), req -> {
			JsonObject jsonObject = cache().jsonObject();
			req.reply(jsonObject);
		}).completionHandler(completionHandler);
	}

	////

	/**
	 * ファイルシステムから POLICY を読み込むタイマ設定.
	 * 待ち時間は {@code POLICY.refreshingPeriodMsec} ( デフォルト値 {@link #LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC} ).
	 */
	private void setLocalFileReadingTimer_() {
		Long delay = localFileCache_.getLong(LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setLocalFileReadingTimer_(delay);
	}
	/**
	 * ファイルシステムから POLICY を読み込むタイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setLocalFileReadingTimer_(long delay) {
		localFileReadingTimerId_ = vertx.setTimer(delay, this::localFileReadingTimerHandler_);
	}
	/**
	 * ファイルシステムから POLICY を読み込むタイマ処理.
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
				localFileCache_.setJsonObject(resRead.result());
			} else {
				// 読み込めなかったら
				if (localFileCache_.isNull()) {
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
	 * サービスセンタから POLICY を取得するタイマ設定.
	 * 待ち時間は {@code POLICY.refreshingPeriodMsec} ( デフォルト値 {@link #CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC} ).
	 */
	private void setControlCenterAccessingTimer_() {
		Long delay = controlCenterCache_.getLong(CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setControlCenterAccessingTimer_(delay);
	}
	/**
	 * サービスセンタから POLICY を取得するタイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setControlCenterAccessingTimer_(long delay) {
		controlCenterAccessingTimerId_ = vertx.setTimer(delay, this::controlCenterAccessingTimerHandler_);
	}
	/**
	 * サービスセンタから POLICY を取得するタイマ処理.
	 * @param timerId タイマ ID
	 */
	private void controlCenterAccessingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != controlCenterAccessingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", controlCenterAccessingTimerId_ : " + controlCenterAccessingTimerId_);
			return;
		}
		DeliveryOptions options = new DeliveryOptions().addHeader("account", controlCenterAccount_).addHeader("password", controlCenterPassword_);
		vertx.eventBus().<JsonObject>send(ServiceAddress.ControlCenterClient.policy(), null, options, resPolicy -> {
			if (resPolicy.succeeded()) {
				// キャッシュしておく
				controlCenterCache_.setJsonObject(resPolicy.result().body());
			} else {
				ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", resPolicy.cause());
			}
			setControlCenterAccessingTimer_();
		});
	}

	////

	/**
	 * POLICY キャッシュからクラスタ参加ユニットの ID のリストを取得する.
	 * @return クラスタ参加ユニットの ID のリスト. {@code null} の可能性あり
	 */
	public static List<String> memberUnitIds() {
		return cache().getStringList("memberUnitIds");
	}

	/**
	 * POLICY キャッシュに定義されたクラスタ参加ユニットの数を取得する.
	 * @return クラスタ参加ユニット数. 定義がなければ null
	 */
	public static int numberOfMembers() {
		List<String> memberUnitIds = memberUnitIds();
		return (memberUnitIds != null) ? memberUnitIds.size() : 0;
	}

	/**
	 * unitId で指定された ID が POLICY キャッシュに参加ユニットとして定義されているか確認する.
	 * @param unitId 確認するユニットの ID
	 * @return 定義されていれば true. そうでなければ false
	 */
	public static boolean isMember(String unitId) {
		if (unitId != null) {
			List<String> memberUnitIds = memberUnitIds();
			return (memberUnitIds != null && memberUnitIds.contains(unitId));
		}
		return false;
	}

}
