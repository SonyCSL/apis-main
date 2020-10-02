package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 他ユニットからのリクエストを処理する Verticle.
 * {@link Mediator} Verticle から起動される.
 * User サービスに中継する.
 * @author OES Project
 */
public class ExternalRequestHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(ExternalRequestHandling.class);

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startExternalRequestHandlingService_(resExternalRequestHandling -> {
			if (resExternalRequestHandling.succeeded()) {
				if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
				startFuture.complete();
			} else {
				log.error(resExternalRequestHandling.cause());
				startFuture.fail(resExternalRequestHandling.cause());
			}
		});
	}

	/**
	 * 停止時に呼び出される.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#externalRequest()}
	 * 範囲 : グローバル
	 * 処理 : 他ユニットからリクエストを受け取り指定されたアドレスに対しアクセプトを送る.
	 * 　　   アクセプトしない場合は送らない.
	 * 　　   アクセプト作成は User サービスへ中継する.
	 * メッセージボディ : リクエスト情報 [{@link JsonObject}]
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "replyAddress"} : アクセプトを送り返すアドレス
	 * レスポンス : なし
	 * @param completionHandler the completion handler
	 */
	private void startExternalRequestHandlingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.externalRequest(), req -> {
			if (!StateHandling.isInOperation()) return;
			String replyAddress = req.headers().get("replyAddress");
			if (replyAddress != null) {
				JsonObject request = req.body();
				if (log.isDebugEnabled()) log.debug("request received : " + request);
				if (request != null) {
					String requestUnitId = request.getString("unitId");
					if (requestUnitId != null) {
						if (!ApisConfig.unitId().equals(requestUnitId)) {
							if (PolicyKeeping.isMember(requestUnitId)) {
								vertx.eventBus().<JsonObject>send(ServiceAddress.User.mediatorRequest(), request, rep -> {
									if (rep.succeeded()) {
										JsonObject accept = rep.result().body();
										if (accept != null) {
											Integer dealAmountMinWh = PolicyKeeping.cache().getInteger(0, "mediator", "deal", "amountMinWh");
											Integer amountWh = accept.getInteger("amountWh", 0);
											if (dealAmountMinWh < amountWh) {
												// 自ユニットからのアクセプトの融通電力量 ( amountWh ) が最低値 ( POLICY.mediator.deal.amountMinWh ) より大きければアクセプトを返す
												accept.put("dealGridCurrentA", PolicyKeeping.cache().getFloat(0F, "mediator", "deal", "gridCurrentA"));
												accept.put("unitId", ApisConfig.unitId());
												// 返信用アドレスにアクセプトを送信する
												vertx.eventBus().send(replyAddress, accept);
												if (log.isDebugEnabled()) log.debug("accept sent back to " + requestUnitId + " : " + accept);
											} else {
												if (log.isDebugEnabled()) log.debug("accept amount : " + amountWh + " ; less than dealAmountMinWh : " + dealAmountMinWh);
											}
										}
									} else {
										if (ReplyFailureUtil.isRecipientFailure(rep)) {
											// nop
										} else if (ReplyFailureUtil.isTimeout(rep)) {
											ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, rep.cause());
										} else {
											ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, rep.cause());
										}
									}
								});
							} else {
								ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "request received from illegal unit : " + requestUnitId + "; request : " + request);
							}
						} else {
							if (log.isTraceEnabled()) log.trace("this is my request");
						}
					} else {
						ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "no unitId in request : " + request);
					}
				} else {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "request is null");
				}
			} else {
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "no replyAddress in request header : " + req.headers());
			}
		}).completionHandler(completionHandler);
	}

}
