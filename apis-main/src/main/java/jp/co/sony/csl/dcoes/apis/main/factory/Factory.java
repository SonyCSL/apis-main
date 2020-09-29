package jp.co.sony.csl.dcoes.apis.main.factory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.emulator.DcdcEmulatorFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v1.DcdcV1Factory;
import jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v2.DcdcV2Factory;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;

/**
 * システム制御まわりのファクトリのファクトリ.
 * {@link ApisConfig#systemType()} に応じた実ファクトリの実ファクトリを生成する.
 * 実装は以下の 3 つ.
 * - {@link DcdcEmulatorFactory}
 * - {@link DcdcV1Factory}
 * - {@link DcdcV2Factory}
 * 実ファクトリが生成するのはいまのところ {@link ControllerFactory} のみだが例えば AC 制御が入ってきたら増えるはず.
 * @author OES Project
 */
public abstract class Factory {
	private static final Logger log = LoggerFactory.getLogger(Factory.class);

	/**
	 * {@link ControllerFactory} サブクラスのシングルトンを保持する.
	 */
	private ControllerFactory controllerFactory_;

	/**
	 * インスタンスを生成する.
	 * Controller ファクトリを生成し保持する.
	 */
	protected Factory() {
		controllerFactory_ = createControllerFactory();
	}

	/**
	 * Controller サービスまわりのファクトリの実オブジェクトを取得する.
	 * @return {@link ControllerFactory} サブクラスのオブジェクト
	 */
	public ControllerFactory controllerFactory() {
		return controllerFactory_;
	}

	/**
	 * {@link ControllerFactory} サブクラスのオブジェクトを生成する.
	 * 実ファクトリが実装する.
	 * @return {@link ControllerFactory} サブクラスのオブジェクト
	 */
	protected abstract ControllerFactory createControllerFactory();

	////

	/**
	 * {@link Factory} サブクラスのシングルトンを保持する.
	 */
	private static Factory instance_;

	/**
	 * 全体のファクトリの実オブジェクトを取得する.
	 * @return {@link Factory} サブクラスのオブジェクト
	 */
	public static Factory factory() {
		return instance_;
	}

	/**
	 * ファクトリまわり全体を初期化する.
	 * CONFIG.systemType に応じたサブクラスのオブジェクトを生成し {@link #instance_} に保持する.
	 * 生成に失敗したら fail.
	 * @param completionHandler the completion handler
	 */
	public static void initialize(Handler<AsyncResult<Void>> completionHandler) {
		if (ApisConfig.systemType() != null) {
			switch (ApisConfig.systemType()) {
			case "dcdc_emulator":
				instance_ = new DcdcEmulatorFactory();
				break;
			case "dcdc_v1":
				instance_ = new DcdcV1Factory();
				break;
			case "dcdc_v2":
				instance_ = new DcdcV2Factory();
				break;
			}
		}
		if (instance_ != null) {
			if (log.isInfoEnabled()) log.info("initialized");
			completionHandler.handle(Future.succeededFuture());
		} else {
			completionHandler.handle(Future.failedFuture("initialize failed ; CONFIG.systemType : " + ApisConfig.systemType()));
		}
	}

}
