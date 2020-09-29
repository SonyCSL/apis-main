package jp.co.sony.csl.dcoes.apis.main.factory;

import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;

/**
 * Controller サービスまわりのファクトリのインタフェイス.
 * 実装は以下の 3 つ.
 * - {@link jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.emulator.DcdcEmulatorControllerFactory DcdcEmulatorControllerFactory}
 * - {@link jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v1.DcdcV1ControllerFactory DcdcV1ControllerFactory}
 * - {@link jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v2.DcdcV2ControllerFactory DcdcV2ControllerFactory}
 * @author OES Project
 */
public interface ControllerFactory {

	/**
	 * データ取得サービスの実オブジェクトを生成する.
	 * @return {@link DataAcquisition} サブクラスのインスタンス
	 */
	DataAcquisition createDataAcquisition();
	/**
	 * データ応答サービスの実オブジェクトを生成する.
	 * @return {@link DataResponding} サブクラスのインスタンス
	 */
	DataResponding createDataResponding();
	/**
	 * デバイス制御サービスの実オブジェクトを生成する.
	 * @return {@link DeviceControlling} サブクラスのインスタンス
	 */
	DeviceControlling createDeviceControlling();

}
