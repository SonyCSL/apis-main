package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc;

import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;

/**
 * DCDC システム向けデータ応答サービス Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * @author OES Project
 */
public class DcdcDataResponding extends DataResponding {

	/**
	 * {@inheritDoc}
	 * {@link DataAcquisition#cache}{@code .dcdc} を返す.
	 */
	@Override protected JsonObject cachedDeviceStatus() {
		return DataAcquisition.cache.getJsonObject("dcdc");
	}

}
