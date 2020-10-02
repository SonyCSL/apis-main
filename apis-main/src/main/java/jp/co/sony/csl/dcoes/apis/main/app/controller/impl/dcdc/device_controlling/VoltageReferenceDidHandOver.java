package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 電圧リファレンスを移動する 4 つの処理のうち 3 番目の処理.
 * 1. {@link VoltageReferenceWillHandOver} : 移動元の準備
 * 2. {@link VoltageReferenceWillTakeOver} : 移動先の起動
 * 3. {@link VoltageReferenceDidHandOver} : 移動元の停止 ← これ
 * 4. {@link VoltageReferenceDidTakeOver} : 移動先の後始末
 * 融通の状態により以下のモードに移行する.
 * - 受電
 * - 送電
 * - 停止 ( {@link GridCurrentStepping} でグリッド電流を段階的に落としてから停止する
 * @author OES Project
 */
public class VoltageReferenceDidHandOver extends AbstractDcdcDeviceControllingCommand {
//	private static final Logger log = LoggerFactory.getLogger(VoltageReferenceDidHandOver.class);

	private String mode_;
	private Float gridCurrentA_;
	private Float operationGridVoltageV_;
	private Float maxOperationGridVoltageV_;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ.
	 *        - mode         : 次のモード [{@link String}]. {@code null} なら WAIT
	 *        - gridCurrentA : グリッド電流値 [{@link Float}]. mode が DISCHARGE または CHARGE なら必須
	 */
	public VoltageReferenceDidHandOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, JsonObjectUtil.getString(params, "mode"), JsonObjectUtil.getFloat(params, "gridCurrentA"));
	}
	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ
	 * @param mode 次のモード. {@code null} なら WAIT
	 * @param gridCurrentA グリッド電流値. mode が DISCHARGE または CHARGE なら {@code null} 不可
	 */
	public VoltageReferenceDidHandOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, String mode, Float gridCurrentA) {
		super(vertx, controller, params);
		mode_ = mode;
		gridCurrentA_ = gridCurrentA;
	}

	// この処理により動的安全性チェックのスキップを開始しない
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return false; }

	// この処理により動的安全性チェックのスキップを終了する
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return true; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		operationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageV");
		maxOperationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "max");
		if (operationGridVoltageV_ != null && maxOperationGridVoltageV_ != null) {
			// どのモードに移行するか決める
			// 指定がなければ WAIT とする
			// 指定があるけどどれだかわからない場合はエラーにする
			DDCon.Mode mode = (mode_ != null) ? DDCon.mode(mode_) : DDCon.Mode.WAIT;
			if (mode != null) {
				switch (mode) {
				case DISCHARGE:
					execute_DISCHARGE_(completionHandler);
					break;
				case CHARGE:
					execute_CHARGE_(completionHandler);
					break;
				case WAIT:
					execute_WAIT_(completionHandler);
					break;
				case VOLTAGE_REFERENCE:
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "invalid mode : " + mode, completionHandler);
					break;
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "invalid mode in params : " + mode_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency ; POLICY.operationGridVoltageV : " + operationGridVoltageV_ + ", POLICY.operationGridVoltageVRange.max : " + maxOperationGridVoltageV_, completionHandler);
		}
	}

	private void execute_DISCHARGE_(Handler<AsyncResult<JsonObject>> completionHandler) {
		// 送電モードにする
		controller_.setDcdcMode(DDCon.Mode.DISCHARGE, maxOperationGridVoltageV_, gridCurrentA_, completionHandler);
	}
	private void execute_CHARGE_(Handler<AsyncResult<JsonObject>> completionHandler) {
		// 受電モードにする
		controller_.setDcdcMode(DDCon.Mode.CHARGE, operationGridVoltageV_, gridCurrentA_, completionHandler);
	}
	private void execute_WAIT_(Handler<AsyncResult<JsonObject>> completionHandler) {
		// グリッド電流値を 0 まで落としてから
		new GridCurrentStepping(vertx_, controller_, params_, 0F).execute(res -> {
			// 成否にかかわらず WAIT にする
			controller_.setDcdcMode(DDCon.Mode.WAIT, operationGridVoltageV_, 0F, completionHandler);
		});
	}

}
