package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;

/**
 * DCDC システムの emulator 環境向けデバイス制御サービス Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * @author OES Project
 */
public class DcdcEmulatorDeviceControlling extends DcdcDeviceControlling {

	private HttpClient client_;

	/**
	 * {@inheritDoc}
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.connection.emulator.host : emulator 接続ホスト名 [{@link String}]
	 * - CONFIG.connection.emulator.port : emulator 接続ポート [{@link Integer}]
	 */
	@Override protected void init(Handler<AsyncResult<Void>> completionHandler) {
		String host = VertxConfig.config.getString("connection", "emulator", "host");
		Integer port = VertxConfig.config.getInteger("connection", "emulator", "port");
		if (host != null && port != null) {
			client_ = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
			completionHandler.handle(Future.succeededFuture());
		} else {
			completionHandler.handle(Future.failedFuture("no connection.emulator.host and/or connection.emulator.port value in config : " + VertxConfig.config.jsonObject()));
		}
	}
	/**
	 * {@inheritDoc}
	 */
	@Override protected void doSetDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler) {
		send(client_, setModeUri_(mode, gridVoltageV, gridCurrentA, droopRatio), completionHandler);
	}
	/**
	 * {@inheritDoc}
	 */
	@Override protected void doSetDcdcVoltage(Number gridVoltageV, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler) {
		send(client_, setVoltageUri_(gridVoltageV, droopRatio), completionHandler);
	}
	/**
	 * {@inheritDoc}
	 */
	@Override protected void doSetDcdcCurrent(Number gridCurrentA, Handler<AsyncResult<JsonObject>> completionHandler) {
		send(client_, setCurrentUri_(gridCurrentA), completionHandler);
	}

	////

	/**
	 * モード変更 API の URI を取得する.
	 * @param mode 変更するモード
	 * @param voltage 電圧値
	 * @param current 電流値
	 * @param droopRatio ドループ率
	 * @return URI
	 */
	private String setModeUri_(DDCon.Mode mode, Number voltage, Number current, Number droopRatio) {
		return "/set/dcdc/" + ApisConfig.unitId() + "?mode=" + DDCon.codeFromMode(mode) + "&dvg=" + voltage + "&dig=" + current + "&drg=" + droopRatio;
	}
	/**
	 * 電圧値変更 API の URI を取得する.
	 * @param voltage 電圧値
	 * @param droopRatio ドループ率
	 * @return URI
	 */
	private String setVoltageUri_(Number voltage, Number droopRatio) {
		return "/set/dcdc/voltage/" + ApisConfig.unitId() + "?dvg=" + voltage + "&drg=" + droopRatio;
	}
	/**
	 * 電流値変更 API の URI を取得する.
	 * @param current 電流値
	 * @return URI
	 */
	private String setCurrentUri_(Number current) {
		return "/set/dcdc/current/" + ApisConfig.unitId() + "?dig=" + current;
	}

}
