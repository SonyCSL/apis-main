package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * デバイスのグリッド電流指定を規定のステップで段階的に変化させる.
 * 終了後 {@link Checkpoint} を呼ぶ.
 * @author OES Project
 */
public class GridCurrentStepping extends AbstractDcdcDeviceControllingCommand {
//	private static final Logger log = LoggerFactory.getLogger(GridCurrentStepping.class);

	private Float gridCurrentA_;
	private Float gridCurrentStepA_;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ.
	 *        - gridCurrentA : グリッド電流値 [{@link Float}]. 必須
	 */
	public GridCurrentStepping(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridCurrentA"));
	}
	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ
	 * @param gridCurrentA グリッド電流値. {@code null} 不可
	 */
	public GridCurrentStepping(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridCurrentA) {
		super(vertx, controller, params);
		gridCurrentA_ = gridCurrentA;
	}

	// この処理により動的安全性チェックのスキップを開始しない
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return false; }

	// この処理により動的安全性チェックのスキップを終了しない
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return false; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridCurrentA_ != null) {
			gridCurrentStepA_ = PolicyKeeping.cache().getFloat("gridCurrentStepA");
			if (gridCurrentStepA_ != null) {
				execute__(completionHandler);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.gridCurrentStepA : " + gridCurrentStepA_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters, gridCurrentA : " + gridCurrentA_, completionHandler);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		Float oldValue = DataAcquisition.cache.getFloat("dcdc", "param", "dig");
		if (oldValue != null) {
			float diff = gridCurrentA_ - oldValue;
			if (gridCurrentStepA_ < Math.abs(diff)) {
				// 現在の指定値と目標値との差がステップ値より大きいので段階的に指定する
				float newValue = (oldValue < gridCurrentA_) ? oldValue + gridCurrentStepA_ : oldValue - gridCurrentStepA_;
				// 現在の指定値プラス ( またはマイナス ) ステップ値を算出し指定する
				controller_.setDcdcCurrent(newValue, resSet -> {
					if (resSet.succeeded()) {
						// 繰り返す
						execute__(completionHandler);
					} else {
						completionHandler.handle(resSet);
					}
				});
			} else {
				// 現在の指定値と目標値との差がステップ値より小さいので目標値を直接指定する
				controller_.setDcdcCurrent(gridCurrentA_, resSet -> {
					if (resSet.succeeded()) {
						// 測定値が指定値に近くのを待つ処理に移行する
						new Checkpoint(vertx_, controller_, params_, null, gridCurrentA_).execute(completionHandler);
					} else {
						completionHandler.handle(resSet);
					}
				});
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "no dcdc.param.dig value in unit data : " + DataAcquisition.cache.jsonObject(), completionHandler);
		}
	}

}
