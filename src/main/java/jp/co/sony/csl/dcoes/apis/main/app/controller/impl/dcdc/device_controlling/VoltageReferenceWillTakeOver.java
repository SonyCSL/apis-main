package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 電圧リファレンスを移動する 4 つの処理のうち 2 番目の処理.
 * 1. {@link VoltageReferenceWillHandOver} : 移動元の準備
 * 2. {@link VoltageReferenceWillTakeOver} : 移動先の起動 ← これ
 * 3. {@link VoltageReferenceDidHandOver} : 移動元の停止
 * 4. {@link VoltageReferenceDidTakeOver} : 移動先の後始末
 * 電圧リファレンスモードへの変更命令を送信する.
 * ドループ率を指定することによりドループ制御を開始する.
 * @author OES Project
 */
public class VoltageReferenceWillTakeOver extends AbstractDcdcDeviceControllingCommand {
	private static final Logger log = LoggerFactory.getLogger(VoltageReferenceWillTakeOver.class);

	private Float gridVoltageV_;
	private Float gridCurrentCapacityA_;
	private Float droopRatio_;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ.
	 *        - gridVoltageV : グリッド電圧値 [{@link Float}]. 任意. {@code null} なら現状の vg 値を使用する
	 */
	public VoltageReferenceWillTakeOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridVoltageV"));
	}
	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ
	 * @param gridVoltageV グリッド電圧値. {@code null} なら現状の vg 値を使用する
	 */
	public VoltageReferenceWillTakeOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridVoltageV) {
		super(vertx, controller, params);
		gridVoltageV_ = gridVoltageV;
	}

	// この処理により動的安全性チェックのスキップを開始する
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return true; }

	// この処理により動的安全性チェックのスキップを終了しない
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return false; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridVoltageV_ == null) {
			// グリッド電圧の指定がなければ現状の測定値を使う
			gridVoltageV_ = DataAcquisition.cache.getFloat("dcdc", "meter", "vg");
			if (log.isInfoEnabled()) log.info("no gridVoltageV parameter given, use dcdc.meter.vg from this unit : " + gridVoltageV_);
		}
		if (gridVoltageV_ != null) {
			gridCurrentCapacityA_ = HwConfigKeeping.gridCurrentCapacityA();
			droopRatio_ = HwConfigKeeping.droopRatio();
			if (gridCurrentCapacityA_ != null && droopRatio_ != null) {
				execute__(completionHandler);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA_ + ", HWCONFIG.droopRatio : " + droopRatio_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters, gridVoltageV : " + gridVoltageV_, completionHandler);
		}
	}
	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		// ドループ率を指定して電圧リファレンスモードにする
		controller_.setDcdcMode(DDCon.Mode.VOLTAGE_REFERENCE, gridVoltageV_, gridCurrentCapacityA_, droopRatio_, completionHandler);
	}

}
