package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;

/**
 * DCDC システム向けデータ取得サービスの親玉 Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * システムの種類に応じて以下の種類がある.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DataAcquisition}
 * @author OES Project
 */
public abstract class DcdcDataAcquisition extends DataAcquisition {

	/**
	 * {@inheritDoc}
	 */
	@Override protected abstract void init(Handler<AsyncResult<Void>> completionHandler);
	/**
	 * {@inheritDoc}
	 */
	@Override protected abstract void getData(Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * {@inheritDoc}
	 */
	@Override protected abstract void getDeviceStatus(Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * {@inheritDoc}
	 * {@code value} で指定したデバイス制御状態を {@link #cache} にマージする.
	 * - {@code value.status} を {@link #cache}{@code .dcdc.status} にマージ
	 * - {@code value.meter} を {@link #cache}{@code .dcdc.meter} にマージ
	 * マージした結果の {@link #cache}{@code .dcdc} を返す.
	 */
	@Override protected JsonObject mergeDeviceStatus(JsonObject value) {
		cache.mergeIn(JsonObjectUtil.getJsonObject(value, "status"), "dcdc", "status");
		cache.mergeIn(JsonObjectUtil.getJsonObject(value, "meter"), "dcdc", "meter");
		return cache.getJsonObject("dcdc");
	}

}
