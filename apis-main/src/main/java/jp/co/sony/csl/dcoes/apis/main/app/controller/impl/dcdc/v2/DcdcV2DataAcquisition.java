package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataAcquisition;

/**
 * DCDC システムの dcdc_batt_comm 環境向けデータ取得サービス Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * @author OES Project
 */
public class DcdcV2DataAcquisition extends DcdcDataAcquisition {

	/**
	 * インタフェイスバージョン.
	 * メジャーインタフェイスバージョンがここに含まれている場合のみ動作する.
	 * 値は {@value}
	 */
	public static final List<String> INTERFACE_VERSIONS = Arrays.asList("2");

	private HttpClient client_;
	private String dataUri_;
	private String statusUri_;
	private String interfaceVersion_;

	/**
	 * {@inheritDoc}
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.connection.dcdc_controller.host : dcdc_batt_comm 接続ホスト名 [{@link String}]
	 * - CONFIG.connection.dcdc_controller.port : dcdc_batt_comm 接続ポート [{@link Integer}]
	 * インタフェイスバージョンの整合性を確認する. NG なら初期化失敗.
	 */
	@Override protected void init(Handler<AsyncResult<Void>> completionHandler) {
		String host = VertxConfig.config.getString("connection", "dcdc_controller", "host");
		Integer port = VertxConfig.config.getInteger("connection", "dcdc_controller", "port");
		if (host != null && port != null) {
			client_ = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
			dataUri_ = "/all/get";
			statusUri_ = "/dcdc/get/status";
			negotiateInterfaceVersion_(completionHandler);
		} else {
			completionHandler.handle(Future.failedFuture("invalid connection.dcdc_controller.host and/or connection.dcdc_controller.port value in config : " + VertxConfig.config.jsonObject()));
		}
	}
	/**
	 * インタフェイスバージョンの整合性を確認する.
	 * @param completionHandler the completion handler
	 */
	private void negotiateInterfaceVersion_(Handler<AsyncResult<Void>> completionHandler) {
		String versionUri = "/version/get";
		send(client_, versionUri, res -> {
			if (res.succeeded()) {
				JsonObject result = res.result();
				String major_minor = JsonObjectUtil.getString(result, "comm_interface_version");
				if (major_minor != null) {
					int pos = major_minor.indexOf('.');
					if (0 < pos) {
						interfaceVersion_ = major_minor.substring(0, pos);
						if (INTERFACE_VERSIONS.contains(interfaceVersion_)) {
							// なんか準備する
							completionHandler.handle(Future.succeededFuture());
						} else {
							completionHandler.handle(Future.failedFuture("unsupported major interface version : " + interfaceVersion_));
						}
					} else {
						completionHandler.handle(Future.failedFuture("bad interface version : " + major_minor));
					}
				} else {
					completionHandler.handle(Future.failedFuture("no interface version ; result : " + result));
				}
			} else {
				completionHandler.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	/**
	 * {@inheritDoc}
	 * dcdc_batt_comm から取得したデータに対し以下の処理をして返す.
	 * - dcdc_batt_comm から取得したデータを {@code dcdc} にセットする
	 * - {@code dcdc.rsoc} を削除し {@code battery.rsoc} にセットする
	 * - {@code dcdc.battery_operation_status} を削除し {@code battery.battery_operation_status} にセットする
	 * - 現在日時を APIS プログラムの標準フォーマットの文字列で {@code time} にセットする
	 */
	@Override protected void getData(Handler<AsyncResult<JsonObject>> completionHandler) {
		send(client_, dataUri_, res -> {
			if (res.succeeded()) {
				JsonObject dcdc = res.result();
				JsonObject battery = new JsonObject().put("rsoc", dcdc.remove("rsoc")).put("battery_operation_status", dcdc.remove("battery_operation_status"));
				String time = DateTimeUtil.toString(LocalDateTime.now());
				JsonObject result = new JsonObject().put("dcdc", dcdc).put("battery", battery).put("time", time);
				completionHandler.handle(Future.succeededFuture(result));
			} else {
				completionHandler.handle(res);
			}
		});
	}

	/**
	 * {@inheritDoc}
	 */
	@Override protected void getDeviceStatus(Handler<AsyncResult<JsonObject>> completionHandler) {
		send(client_, statusUri_, completionHandler);
	}

}
