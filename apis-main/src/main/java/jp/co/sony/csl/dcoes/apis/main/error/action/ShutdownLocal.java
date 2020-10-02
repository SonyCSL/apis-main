package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * エラー処理の実クラス.
 * 自ユニットをシャットダウンする.
 * @author OES Project
 */
public class ShutdownLocal extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(ShutdownLocal.class);

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} あるいは {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling} でコピーしたものが渡される.
	 * @param logMessages エラー処理で記録するログメッセージのリスト
	 */
	public ShutdownLocal(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		if (log.isInfoEnabled()) log.info("sending shutdown message to this unit ...");
		vertx_.eventBus().send(ServiceAddress.shutdownLocal(), null, repShutdownLocal -> {
			if (repShutdownLocal.succeeded()) {
				if (log.isInfoEnabled()) log.info("done");
				completionHandler.handle(Future.succeededFuture());
			} else {
				log.error("... failed");
				if (ReplyFailureUtil.isRecipientFailure(repShutdownLocal)) {
					// 失敗しても向こう側の処理での失敗ならここで何もすることはない
					completionHandler.handle(Future.failedFuture(repShutdownLocal.cause()));
				} else {
					// それ以外の失敗は FATAL にしてしまう
					ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, repShutdownLocal.cause(), completionHandler);
				}
			}
		});
	}

}
