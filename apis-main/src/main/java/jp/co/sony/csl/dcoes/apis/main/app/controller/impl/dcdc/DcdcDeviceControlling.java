package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.AbstractDcdcDeviceControllingCommand;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.CHARGE;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.DISCHARGE;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.GridCurrentStepping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.GridVoltageStepping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.Scram;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VOLTAGE_REFERENCE;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceAuthorization;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceDidHandOver;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceDidTakeOver;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceWillHandOver;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceWillTakeOver;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.WAIT;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * DCDC システム向けデバイス制御サービスの親玉 Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * システムの種類に応じて以下の種類がある.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DeviceControlling}
 * @author OES Project
 */
public abstract class DcdcDeviceControlling extends DeviceControlling {
	private static final Logger log = LoggerFactory.getLogger(DcdcDeviceControlling.class);

	/**
	 * DCDC コンバータのモードを設定する.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * @param mode モード. 必須
	 * @param gridVoltageV グリッド電圧値. mode によって要不要あり
	 * @param gridCurrentA グリッド電流値. mode によって要不要あり
	 * @param droopRatio ドループ率. mode が {@link DDCon.Mode#VOLTAGE_REFERENCE} の場合のみ必須
	 * @param completionHandler the completion handler
	 */
	protected abstract void doSetDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * DCDC コンバータのグリッド電圧を設定する.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * @param gridVoltageV グリッド電圧値. 必須
	 * @param droopRatio ドループ率. 必須
	 * @param completionHandler the completion handler
	 */
	protected abstract void doSetDcdcVoltage(Number gridVoltageV, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * DCDC コンバータのグリッド電圧を設定する.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * @param gridCurrentA　グリッド電流値. 必須
	 * @param completionHandler the completion handler
	 */
	protected abstract void doSetDcdcCurrent(Number gridCurrentA, Handler<AsyncResult<JsonObject>> completionHandler);

	/**
	 * {@inheritDoc}
	 */
	@Override protected abstract void init(Handler<AsyncResult<Void>> completionHandler);
	/**
	 * {@inheritDoc}
	 * 単純に {@link WAIT} を実行する.
	 */
	@Override protected void doLocalStopWithExclusiveLock(Handler<AsyncResult<JsonObject>> completionHandler) {
		new WAIT(vertx, this, null).execute(completionHandler);
	}
	/**
	 * {@inheritDoc}
	 * 現在のモードが電圧リファレンスで {@code excludeVoltageReference} が {@code true} なら何もしない.
	 * そうでなければ {@link Scram} を実行する.
	 */
	@Override protected void doScramWithExclusiveLock(boolean excludeVoltageReference, Handler<AsyncResult<JsonObject>> completionHandler) {
		DDCon.Mode mode = DDCon.modeFromCode(DataAcquisition.cache.getString("dcdc", "status", "status"));
		if (log.isInfoEnabled()) log.info("mode : " + mode + ", excludeVoltageReference : " + excludeVoltageReference);
		if (DDCon.Mode.VOLTAGE_REFERENCE == mode && excludeVoltageReference) {
			if (log.isInfoEnabled()) log.info("ignore ...");
			completionHandler.handle(Future.succeededFuture(DataAcquisition.cache.getJsonObject("dcdc")));
		} else {
			new Scram(vertx, this, null).execute(completionHandler);
		}
	}
	/**
	 * {@inheritDoc}
	 * {@code operation.command} と {@code operation.params} に従ってデバイス制御を実行する.
	 * 具体的な処理は {@link #doDcdcControlling_(String, JsonObject, Handler)}.
	 */
	@Override protected void doDeviceControllingWithExclusiveLock(JsonObject operation, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (operation != null) {
			String command = operation.getString("command");
			JsonObject params = operation.getJsonObject("params");
			doDcdcControlling_(command, params, completionHandler);
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "operation is null", completionHandler);
		}
	}
	/**
	 *
	 * {@inheritDoc}
	 * {@code value} で指定したデバイス制御状態を {@link DataAcquisition#cache} にマージする.
	 * - {@code value} を {@link DataAcquisition#cache}{@code .dcdc} にマージ
	 * マージした結果の {@link DataAcquisition#cache}{@code .dcdc} を返す.
	 */
	@Override protected JsonObject mergeDeviceStatus(JsonObject value) {
		DataAcquisition.cache.mergeIn(value, "dcdc");
		return DataAcquisition.cache.getJsonObject("dcdc");
	}

	////

	/**
	 * グリッド電流設定値の正当性をチェックする.
	 * @param mode モード
	 * @param current 電流値
	 * @return 逸脱していたらエラー文字列. 問題なければ {@code null}
	 */
	private String checkCurrentValueRange_(DDCon.Mode mode, Number current) {
		if (current == null) {
			// null は NG
			String message = "current value is null";
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		float value = current.floatValue();
		if (value == 0F) {
			// 0 は OK
			return null;
		} else if (value < 0F) {
			// 負の値は NG
			String message = "current value should not be negative : " + value;
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		Float gridCurrentCapacityA = HwConfigKeeping.gridCurrentCapacityA();
		if (gridCurrentCapacityA == null) {
			// 設定ミスで NG
			String message = "data deficiency; HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA;
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		if (gridCurrentCapacityA < value) {
			// 自ユニットの電流容量オーバーで NG
			String message = "invalid current value : " + value + "; should less than or equal to : " + gridCurrentCapacityA;
			ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		return null;
	}

	////

	/**
	 * デバイス制御.
	 * completionHandler の {@link AsyncResult#result()} で制御後のデバイス制御状態を受け取る.
	 * {@code command} で処理の種類を決めオブジェクトを生成し実行する.
	 * @param command コマンド
	 * @param params 制御パラメタ
	 * @param completionHandler the completion handler
	 */
	private void doDcdcControlling_(String command, JsonObject params, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (command != null) {
			AbstractDcdcDeviceControllingCommand cmd = null;
			switch (command) {
			case "WAIT":
				cmd = new WAIT(vertx, this, params);
				break;
			case "VOLTAGE_REFERENCE":
				cmd = new VOLTAGE_REFERENCE(vertx, this, params);
				break;
			case "CHARGE":
				cmd = new CHARGE(vertx, this, params);
				break;
			case "DISCHARGE":
				cmd = new DISCHARGE(vertx, this, params);
				break;
			case "voltageReferenceAuthorization":
				cmd = new VoltageReferenceAuthorization(vertx, this, params);
				break;
			case "voltageReferenceWillHandOver":
				cmd = new VoltageReferenceWillHandOver(vertx, this, params);
				break;
			case "voltageReferenceWillTakeOver":
				cmd = new VoltageReferenceWillTakeOver(vertx, this, params);
				break;
			case "voltageReferenceDidHandOver":
				cmd = new VoltageReferenceDidHandOver(vertx, this, params);
				break;
			case "voltageReferenceDidTakeOver":
				cmd = new VoltageReferenceDidTakeOver(vertx, this, params);
				break;
			case "scram":
				cmd = new Scram(vertx, this, params);
				break;
			case "voltage":
				cmd = new GridVoltageStepping(vertx, this, params);
				break;
			case "current":
				cmd = new GridCurrentStepping(vertx, this, params);
				break;
			}
			if (cmd != null) {
				cmd.execute(completionHandler);
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "unknown command : " + command, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; command : " + command + ", params : " + params, completionHandler);
		}
	}

	////

	/**
	 * DCDC コンバータのモードを設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcMode(jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon.Mode, Number, Number, Number, Handler)} で実装する.
	 * @param mode モード. 必須
	 * @param gridVoltageV グリッド電圧値. mode によって要不要あり
	 * @param gridCurrentA グリッド電流値. mode によって要不要あり
	 * @param completionHandler the completion handler
	 */
	public void setDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Handler<AsyncResult<JsonObject>> completionHandler) {
		setDcdcMode(mode, gridVoltageV, gridCurrentA, Integer.valueOf(0), completionHandler);
	}
	/**
	 * DCDC コンバータのモードを設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcMode(jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon.Mode, Number, Number, Number, Handler)} で実装する.
	 * @param mode モード. 必須
	 * @param gridVoltageV グリッド電圧値. mode によって要不要あり
	 * @param gridCurrentA グリッド電流値. mode によって要不要あり
	 * @param droopRatio ドループ率. mode が {@link DDCon.Mode#VOLTAGE_REFERENCE} の場合のみ必須
	 * @param completionHandler the completion handler
	 */
	public void setDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (mode != null && gridVoltageV != null && gridCurrentA != null && droopRatio != null) {
			String error = checkCurrentValueRange_(mode, gridCurrentA);
			if (error == null) {
				doSetDcdcMode(mode, gridVoltageV, gridCurrentA, droopRatio, res -> {
					if (res.succeeded()) {
						DDCon.OperationMode targetOperationMode = DDCon.operationModeForMode(mode);
						DDCon.OperationMode resultOperationMode = DDCon.operationModeFromCode(JsonObjectUtil.getString(res.result(), "status", "operationMode"));
						if (targetOperationMode == resultOperationMode) {
							completionHandler.handle(res);
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "target mode : " + mode + "; result operationMode : " + resultOperationMode + "; should be : " + targetOperationMode, completionHandler);
						}
					} else {
						completionHandler.handle(res);
					}
				});
			} else {
				completionHandler.handle(Future.failedFuture(error));
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; mode : " + mode + ", gridVoltageV : " + gridVoltageV + ", gridCurrentA : " + gridCurrentA + ", droopRatio : " + droopRatio, completionHandler);
		}
	}
	/**
	 * DCDC コンバータのグリッド電圧を設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcVoltage(Number, Number, Handler)} で実装する.
	 * @param gridVoltageV グリッド電圧値. 必須
	 * @param completionHandler the completion handler
	 */
	public void setDcdcVoltage(Number gridVoltageV, Handler<AsyncResult<JsonObject>> completionHandler) {
		setDcdcVoltage(gridVoltageV, Integer.valueOf(0), completionHandler);
	}
	/**
	 * DCDC コンバータのグリッド電圧を設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcVoltage(Number, Number, Handler)} で実装する.
	 * @param gridVoltageV グリッド電圧値. 必須
	 * @param droopRatio ドループ率. 必須
	 * @param completionHandler the completion handler
	 */
	public void setDcdcVoltage(Number gridVoltageV, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridVoltageV != null && droopRatio != null) {
			doSetDcdcVoltage(gridVoltageV, droopRatio, completionHandler);
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; gridVoltageV : " + gridVoltageV + ", droopRatio : " + droopRatio, completionHandler);
		}
	}
	/**
	 * DCDC コンバータのグリッド電圧を設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcCurrent(Number, Handler)} で実装する.
	 * @param gridCurrentA　グリッド電流値. 必須
	 * @param completionHandler the completion handler
	 */
	public void setDcdcCurrent(Number gridCurrentA, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridCurrentA != null) {
			String error = checkCurrentValueRange_(null, gridCurrentA);
			if (error == null) {
				doSetDcdcCurrent(gridCurrentA, completionHandler);
			} else {
				completionHandler.handle(Future.failedFuture(error));
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; gridCurrentA : " + gridCurrentA, completionHandler);
		}
	}

}
