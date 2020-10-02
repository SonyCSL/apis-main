package jp.co.sony.csl.dcoes.apis.main.app.user.util;

import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;

/**
 * 置き場所が決まっていないやつ置き場.
 * @author OES Project
 */
public class Misc {

	/**
	 * 自ユニットのデバイスにとって最も効率の高いグリッド電圧を取得する.
	 * HWCONFIG.efficientBatteryGridVoltageRatio と {@code data}.dcdc.meter.vb 値から算出する.
	 * @param data ユニットデータ
	 * @return 自ユニットのデバイスにとって最も効率の高いグリッド電圧 [V]. {@code null} あり
	 */
	public static Float efficientGridVoltageV_(JsonObject data) {
		Float ratio = HwConfigKeeping.efficientBatteryGridVoltageRatio();
		Float vb = JsonObjectUtil.getFloat(data, "dcdc", "meter", "vb");
		if (ratio != null && vb != null) {
			return ratio.floatValue() * vb.floatValue();
		}
		return null;
	}

}
