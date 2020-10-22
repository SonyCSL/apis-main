package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * グリッド電圧 and/or グリッド電流の測定値が指定値に対して誤差の範囲内に収まるまで待つ.
 * 規定の時間間隔で規定の回数繰り返す.
 * @author OES Project
 */
public class Checkpoint extends AbstractDcdcDeviceControllingCommand {
	private static final Logger log = LoggerFactory.getLogger(Checkpoint.class);

	private Float gridVoltageV_;
	private Float gridCurrentA_;
	private float gridVoltageAllowanceV_;
	private float gridCurrentAllowanceA_;
	private int retryLimit_;
	private long retryWaitMsec_;
	private boolean toFail_ = false;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ.
	 *        - gridVoltageV : 目標グリッド電圧値 [{@link Float}]. 任意
	 *        - gridCurrentA : 目標グリッド電流値 [{@link Float}]. 任意
	 */
	public Checkpoint(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridVoltageV"), params.getFloat("gridCurrentA"));
	}
	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ
	 * @param gridVoltageV 目標グリッド電圧値. {@code null} 可
	 * @param gridCurrentA 目標グリッド電流値. {@code null} 可
	 */
	public Checkpoint(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridVoltageV, Float gridCurrentA) {
		super(vertx, controller, params);
		gridVoltageV_ = gridVoltageV;
		gridCurrentA_ = gridCurrentA;
	}

	// この処理により動的安全性チェックのスキップを開始しない
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return false; }

	// この処理により動的安全性チェックのスキップを終了しない
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return false; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		Float gridVoltageAllowanceV = PolicyKeeping.cache().getFloat("gridVoltageAllowanceV");
		Float gridCurrentAllowanceA = HwConfigKeeping.gridCurrentAllowanceA();
		Integer retryLimit = PolicyKeeping.cache().getInteger("controller", "dcdc", "checkpoint", "retryLimit");
		Long retryWaitMsec = PolicyKeeping.cache().getLong("controller", "dcdc", "checkpoint", "retryWaitMsec");
		if (gridVoltageAllowanceV != null && gridCurrentAllowanceA != null && retryLimit != null && retryWaitMsec != null) {
			gridVoltageAllowanceV_ = gridVoltageAllowanceV;
			gridCurrentAllowanceA_ = gridCurrentAllowanceA;
			retryLimit_ = retryLimit;
			if (retryLimit_ < 0) {
				// for test ( マイナス値を指定するとその絶対値回数リトライした挙句失敗するようになる )
				retryLimit_ = - retryLimit_;
				toFail_ = true;
			}
			retryWaitMsec_ = retryWaitMsec;
			if (log.isDebugEnabled()) log.debug("grid voltage allowance (V) : " + gridVoltageAllowanceV_ + ", grid current allowance (A) : " + gridCurrentAllowanceA_ + ", retry limit : " + retryLimit_ + ", retry wait (msec) : " + retryWaitMsec_ + ", to fail : " + toFail_);
			execute__(completionHandler);
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.gridVoltageAllowanceV : " + gridVoltageAllowanceV + ", HWCONFIG.gridCurrentAllowanceA : " + gridCurrentAllowanceA + ", POLICY.controller.dcdc.checkpoint.retryLimit : " + retryLimit + ", POLICY.controller.dcdc.checkpoint.retryWaitMsec : " + retryWaitMsec, completionHandler);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		if (log.isDebugEnabled()) log.debug("retryLimit_ : " + retryLimit_);
		// リトライ待ち時間のタイマをセット
		vertx_.setTimer(retryWaitMsec_, timerId -> {
			// デバイスの情報を取得する
			vertx_.eventBus().<JsonObject>send(ServiceAddress.Controller.urgentUnitDeviceStatus(), null, rep -> {
				if (rep.succeeded()) {
					boolean vgResult = true;
					boolean igResult = true;
					JsonObject dcdcResponse = rep.result().body();
					if (gridVoltageV_ != null) {
						// 電圧の目標値があれば電圧をチェック
						Float vg = JsonObjectUtil.getFloat(dcdcResponse, "meter", "vg");
						if (vg != null) {
							float target = gridVoltageV_;
							if (log.isDebugEnabled()) log.debug("vg : " + vg + ", target : " + target + ", allowance : " + gridVoltageAllowanceV_);
							float left = target - gridVoltageAllowanceV_;
							float right = target + gridVoltageAllowanceV_;
							vgResult = (left <= vg && vg <= right);
							if (log.isDebugEnabled()) log.debug(((vgResult) ? "OK" : "NG") + " ( " + left + " <= " + vg + " <= " + right + " )");
						} else {
							vgResult = false;
							ErrorUtil.report(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "no meter.vg value in dcdc status : " + dcdcResponse);
						}
					}
					if (gridCurrentA_ != null) {
						// 電流の目標値があれば電流をチェック
						Float ig = JsonObjectUtil.getFloat(dcdcResponse, "meter", "ig");
						if (ig != null) {
							float target = (ig < 0F) ? - gridCurrentA_ : gridCurrentA_;
							if (log.isDebugEnabled()) log.debug("ig : " + ig + ", target : " + target + ", allowance : " + gridCurrentAllowanceA_);
							float left = target - gridCurrentAllowanceA_;
							float right = target + gridCurrentAllowanceA_;
							igResult = (left <= ig && ig <= right);
							if (log.isDebugEnabled()) log.debug(((igResult) ? "OK" : "NG") + " ( " + left + " <= " + ig + " <= " + right + " )");
						} else {
							igResult = false;
							ErrorUtil.report(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "no meter.ig value in dcdc status : " + dcdcResponse);
						}
					}
					if (vgResult && igResult && !toFail_) {
						// 電圧と電流が OK なら ( そしてテスト機能で失敗を指定されていなければ ) 成功
						succeeded(completionHandler);
					} else {
						// NG なら ( あるいはテスト機能で失敗を指定されていれば )
						if (0 < --retryLimit_) {
							// リトライ回数内であればリトライ
							execute__(completionHandler);
						} else {
							// リトライ回数に達したら失敗
							ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, "checkpoint failed", completionHandler);
						}
					}
				} else {
					if (ReplyFailureUtil.isRecipientFailure(rep)) {
						completionHandler.handle(Future.failedFuture(rep.cause()));
					} else {
						ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), completionHandler);
					}
				}
			});
		});
	}

}
