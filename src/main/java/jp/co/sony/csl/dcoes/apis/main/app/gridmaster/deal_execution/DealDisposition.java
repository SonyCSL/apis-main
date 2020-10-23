package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 融通情報を削除する.
 * @author OES Project
 */
public class DealDisposition extends AbstractDealExecution {
//	private static final Logger log = LoggerFactory.getLogger(DealDisposition.class);

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution} 開始時にコピーしたものが渡される.
	 * @param deal 処理対象 DEAL オブジェクト
	 * @param otherDeals 同時に存在している他の DEAL オブジェクトのリスト
	 */
	public DealDisposition(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}
	/**
	 * インスタンスを生成する.
	 * 他の {@link AbstractDealExecution} の内部状態をそのまま受け継ぐため初期化不要.
	 * @param other 他の abstractdealexecution オブジェクト
	 */
	public DealDisposition(AbstractDealExecution other) {
		super(other);
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		disposeDeal_(completionHandler);
	}

	private void disposeDeal_(Handler<AsyncResult<Void>> completionHandler) {
		vertx_.eventBus().<JsonObject>send(ServiceAddress.Mediator.dealDisposition(), dealId_, repDealDisposition -> {
			if (repDealDisposition.succeeded()) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				if (ReplyFailureUtil.isRecipientFailure(repDealDisposition)) {
					completionHandler.handle(Future.failedFuture(repDealDisposition.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on EventBus", repDealDisposition.cause(), completionHandler);
				}
			}
		});
	}

}
