package jp.co.sony.csl.dcoes.apis.main.util;

import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;

/**
 * apis-main 専用の CONFIG アクセスツール.
 * ごく一部しかサポートしていない.
 * @author OES Project
 */
public class ApisConfig {

	private ApisConfig() { }

	/**
	 * CONFIG からユニット ID を取得.
	 * {@code CONFIG.unitId}.
	 * @return ユニット ID
	 */
	public static String unitId() {
		return VertxConfig.config.getString("unitId");
	}
	/**
	 * CONFIG からユニット名を取得.
	 * {@code CONFIG.unitName}.
	 * @return ユニット名
	 */
	public static String unitName() {
		return VertxConfig.config.getString("unitName");
	}
	/**
	 * CONFIG からシリアル番号文字列を取得.
	 * {@code CONFIG.serialNumber}.
	 * @return シリアル番号文字列
	 */
	public static String serialNumber() {
		return VertxConfig.config.getString("serialNumber");
	}

	/**
	 * CONFIG からシステムの種類を示す文字列を取得.
	 * {@code CONFIG.systemType}.
	 * 値は以下のいずれか.
	 * - {@code dcdc_emulator} : emulator
	 * - {@code dcdc_v1}       : dcdc_controller ＆ EMU-Driver
	 * - {@code dcdc_v2}       : dcdc_batt_comm
	 * @return システムの種類を示す文字列
	 */
	public static String systemType() {
		return VertxConfig.config.getString("systemType");
	}

	/**
	 * CONFIG からバッテリ容量管理機能の有効フラグを取得.
	 * {@code CONFIG.batteryCapacityManagement.enabled}.
	 * バッテリ容量管理機能を有効にするか否か.
	 * Gateway 運用など一つのバッテリを複数の apis-main で共有している場合に他の apis-main とバッテリ電流容量を協調管理する.
	 * 現状は協調管理する apis-main が全て同一コンピュータ上にある必要がある ( ファイルシステムを用いて協調管理するため ).
	 * デフォルト : {@code false}
	 * @return 有効フラグ
	 */
	public static Boolean isBatteryCapacityManagementEnabled() {
		return VertxConfig.config.getBoolean(Boolean.FALSE, "batteryCapacityManagement", "enabled");
	}

}
