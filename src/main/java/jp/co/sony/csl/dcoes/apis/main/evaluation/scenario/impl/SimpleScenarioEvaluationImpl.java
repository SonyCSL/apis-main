package jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;

/**
 * SCENARIO 評価の実クラス.
 * @author OES Project
 */
public class SimpleScenarioEvaluationImpl extends AbstractScenarioEvaluationImpl {
	private static final Logger log = LoggerFactory.getLogger(SimpleScenarioEvaluationImpl.class);

	/**
	 * {@inheritDoc}
	 */
	@Override public void checkStatus(Vertx vertx, JsonObject scenario, JsonObject unitData, Handler<AsyncResult<JsonObject>> completionHandler) {
		String batteryStatus = batteryStatus(scenario, unitData);
		if (batteryStatus != null) {
			// SCENARIO サブセットから現在のバッテリステータスでリクエストを出すかどうかを判断する
			JsonObject availableRequest = JsonObjectUtil.getJsonObject(scenario, "request", batteryStatus);
			if (availableRequest != null && !availableRequest.isEmpty()) {
				// 出すべきリクエストは "charge" か "discharge" かを決める
				// またその時の条件も取得する
				String type = null;
				JsonObject condition = null;
				if ((condition = availableRequest.getJsonObject("charge")) != null) {
					type = "charge";
				} else if ((condition = availableRequest.getJsonObject("discharge")) != null) {
					type = "discharge";
				}
				if (condition != null) {
					// リクエストを出す
					Integer limitWh = condition.getInteger("limitWh");
					if (limitWh != null) {
						Integer remainingWh = JsonObjectUtil.getInteger(unitData, "apis", "remaining_capacity_wh");
						if (log.isDebugEnabled()) log.debug("remainingWh : " + remainingWh);
						if (remainingWh != null) {
							// 現在のバッテリ残量と limitWh との差分がリクエスト量になる
							int requestingWh = ("charge".equals(type)) ? limitWh - remainingWh : remainingWh - limitWh;
							if (0 < requestingWh) {
								JsonObject request = new JsonObject();
								request.put("type", type);
								request.put("amountWh", requestingWh);
								Float pointPerWh = condition.getFloat("pointPerWh");
								if (pointPerWh != null) request.put("pointPerWh", pointPerWh);
								String pairUnitId = condition.getString("pairUnitId");
								if (pairUnitId != null) request.put("pairUnitId", pairUnitId);
								if (log.isDebugEnabled()) log.debug("request created : " + request);
								completionHandler.handle(Future.succeededFuture(request));
							} else if (requestingWh < 0) {
								if (log.isWarnEnabled()) log.warn("requestingWh calculated as minus value; type : " + type + ", limitWh : " + limitWh + ", remainingWh : " + remainingWh);
								completionHandler.handle(Future.failedFuture("requestingWh calculated as minus value; type : " + type + ", limitWh : " + limitWh + ", remainingWh : " + remainingWh));
							} else {
								completionHandler.handle(Future.succeededFuture());
							}
						} else {
							if (log.isWarnEnabled()) log.warn("no apis.remaining_capacity_wh value in unitData");
							completionHandler.handle(Future.failedFuture("no apis.remaining_capacity_wh value in unitData"));
						}
					} else {
						if (log.isWarnEnabled()) log.warn("no request." + batteryStatus + '.' + type + ".limitWh value in scenario");
						completionHandler.handle(Future.failedFuture("no request." + batteryStatus + '.' + type + ".limitWh value in scenario"));
					}
				} else {
					if (log.isDebugEnabled()) log.debug("no action in request." + batteryStatus + " object in scenario");
					completionHandler.handle(Future.failedFuture("no request." + batteryStatus + '.' + type + " object in scenario"));
				}
			} else {
				if (log.isDebugEnabled()) log.debug("no request." + batteryStatus + " object in scenario");
				completionHandler.handle(Future.succeededFuture());
			}
		} else {
			if (log.isWarnEnabled()) log.warn("could not identify batteryStatus");
			completionHandler.handle(Future.failedFuture("could not identify batteryStatus"));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void treatRequest(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, Handler<AsyncResult<JsonObject>> completionHandler) {
		String requestPairUnitId = request.getString("pairUnitId");
		if (requestPairUnitId == null || requestPairUnitId.equals(ApisConfig.unitId())) {
			// pairUnitId が指定されていないか自ユニットだったら
			String requestType = request.getString("type");
			if (requestType != null) {
				String type = ("charge".equals(requestType)) ? "discharge" : (("discharge".equals(requestType)) ? "charge" : null);
				if (type != null) {
					String batteryStatus = batteryStatus(scenario, unitData);
					if (batteryStatus != null) {
						// SCENARIO サブセットから現在のバッテリステータスとリクエスト種類に対しアクセプトを返すかどうか判断する
						JsonObject condition = JsonObjectUtil.getJsonObject(scenario, "accept", batteryStatus, type);
						if (condition != null) {
							// アクセプトを返す
							String pairUnitId = condition.getString("pairUnitId");
							if (pairUnitId == null || pairUnitId.equals(request.getString("unitId"))) {
								// SCENARIO サブセット中のアクセプト条件に pairUnitId が指定されていないかそれがリクエストユニットだったら
								Integer limitWh = condition.getInteger("limitWh");
								if (limitWh != null) {
									Integer remainingWh = JsonObjectUtil.getInteger(unitData, "apis", "remaining_capacity_wh");
									if (log.isDebugEnabled()) log.debug("remainingWh : " + remainingWh);
									if (remainingWh != null) {
										// 現在のバッテリ残量と limitWh との差分がアクセプト量になる
										int acceptingWh = ("charge".equals(type)) ? limitWh - remainingWh : remainingWh - limitWh;
										if (0 < acceptingWh) {
											if (log.isDebugEnabled()) log.debug("accepting amount : " + acceptingWh);
											JsonObject accept = new JsonObject();
											accept.put("type", type);
											accept.put("amountWh", acceptingWh);
											Float pointPerWh = condition.getFloat("pointPerWh");
											if (pointPerWh != null) accept.put("pointPerWh", pointPerWh);
											if (pairUnitId != null) accept.put("pairUnitId", pairUnitId);
											if (log.isDebugEnabled()) log.debug("accept created : " + accept);
											completionHandler.handle(Future.succeededFuture(accept));
										} else if (acceptingWh < 0) {
											if (log.isWarnEnabled()) log.warn("acceptingWh calculated as minus value; type : " + type + ", limitWh : " + limitWh + ", remainingWh : " + remainingWh);
											completionHandler.handle(Future.failedFuture("acceptingWh calculated as minus value; type : " + type + ", limitWh : " + limitWh + ", remainingWh : " + remainingWh));
										} else {
											completionHandler.handle(Future.succeededFuture());
										}
									} else {
										if (log.isWarnEnabled()) log.warn("no apis.remaining_capacity_wh value in unitData");
										completionHandler.handle(Future.failedFuture("no apis.remaining_capacity_wh value in unitData"));
									}
								} else {
									if (log.isWarnEnabled()) log.warn("no accept." + batteryStatus + '.' + type + ".limitWh value in scenario");
									completionHandler.handle(Future.failedFuture("no accept." + batteryStatus + '.' + type + ".limitWh value in scenario"));
								}
							} else {
								// SCENARIO サブセット中のアクセプト条件に pairUnitId が指定されていてそれがリクエストユニットでなければアクセプトは返さない
								if (log.isDebugEnabled()) log.debug("pairUnitId in SCENARIO : " + pairUnitId + ", request unitId : " + request.getString("unitId"));
								completionHandler.handle(Future.succeededFuture());
							}
						} else {
							if (log.isDebugEnabled()) log.debug("no accept." + batteryStatus + '.' + type + " object in scenario");
							completionHandler.handle(Future.succeededFuture());
						}
					} else {
						if (log.isWarnEnabled()) log.warn("could not identify batteryStatus");
						completionHandler.handle(Future.failedFuture("could not identify batteryStatus"));
					}
				} else {
					if (log.isWarnEnabled()) log.warn("illegal type in request : " + requestType);
					completionHandler.handle(Future.failedFuture("illegal type in request : " + requestType));
				}
			} else {
				if (log.isWarnEnabled()) log.warn("no type value in request");
				completionHandler.handle(Future.failedFuture("no type value in request"));
			}
		} else {
			// pairUnitId が指定されていて自ユニットでなければアクセプトは返さない
			if (log.isDebugEnabled()) log.debug("pairUnitId in request : " + requestPairUnitId + ", not me");
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void chooseAccept(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, List<JsonObject> accepts, Handler<AsyncResult<JsonObject>> completionHandler) {
		String requestPairUnitId = request.getString("pairUnitId");
		if (requestPairUnitId != null) {
			// 自分がリクエストに pairUnitId を指定していたらそれ以外のユニットからのアクセプトは取り除く
			List<JsonObject> filteredAccepts = new ArrayList<>(1);
			for (JsonObject anAccept : accepts) {
				if (requestPairUnitId.equals(anAccept.getString("unitId"))) {
					filteredAccepts.add(anAccept);
				}
			}
			accepts = filteredAccepts;
		}
		// アクセプト側が指定する pairUnitId は無視する
		String acceptSelectionStrategy = JsonObjectUtil.getString(scenario, "acceptSelection", "strategy");
		if (!"amount".equals(acceptSelectionStrategy) && !"pointAndAmount".equals(acceptSelectionStrategy)) {
			if (log.isWarnEnabled()) log.warn("acceptSelection.strategy '" + acceptSelectionStrategy + "' not supported, use 'amount'");
			acceptSelectionStrategy = "amount";
		}
		if ("amount".equals(acceptSelectionStrategy)) {
			// アクセプト選択方針 ( SCENARIO.acceptSelection.strategy ) が "amount" なら
			// アクセプト中の融通電力量が最も大きいものを選択する
			JsonObject accept = null;
			int maxAmountWh = 0;
			for (JsonObject anAccept : accepts) {
				Integer anAmountWh = anAccept.getInteger("amountWh");
				if (anAmountWh != null) {
					if (maxAmountWh < anAmountWh) {
						maxAmountWh = anAmountWh;
						accept = anAccept;
					}
				} else {
					if (log.isWarnEnabled()) log.warn("no amountWh in accept : " + anAccept);
				}
			}
			completionHandler.handle(Future.succeededFuture(accept));
		} else if ("pointAndAmount".equals(acceptSelectionStrategy)) {
			// アクセプト選択方針 ( SCENARIO.acceptSelection.strategy ) が "pointAndAmount" なら
			String requestType = request.getString("type");
			Float requestPointPerWh = request.getFloat("pointPerWh");
			if ("charge".equals(requestType)) {
				// リクエストの種類が "charge" だったら
				// アクセプト中のポイントがリクエストのポイント以下かつ最も小さい ( 同じなら融通電力量が最も大きい ) ものを選択する
				// → 決めた値段より安く買いたい, そしてその中でも一番安いところから買いたい
				JsonObject accept = null;
				float minPointPerWh = -1;
				int maxAmountWh = 0;
				for (JsonObject anAccept : accepts) {
					Float aPointPerWh = anAccept.getFloat("pointPerWh");
					if (aPointPerWh != null) {
						if (aPointPerWh <= requestPointPerWh) {
							Integer anAmountWh = anAccept.getInteger("amountWh");
							if (anAmountWh != null) {
								if (minPointPerWh < 0 || aPointPerWh < minPointPerWh) {
									minPointPerWh = aPointPerWh;
									maxAmountWh = anAmountWh;
									accept = anAccept;
								} else if (aPointPerWh == minPointPerWh) {
									if (maxAmountWh < anAmountWh) {
										maxAmountWh = anAmountWh;
										accept = anAccept;
									}
								}
							} else {
								if (log.isWarnEnabled()) log.warn("no amountWh in accept : " + anAccept);
							}
						}
					} else {
						if (log.isWarnEnabled()) log.warn("no pointPerWh in accept : " + anAccept);
					}
				}
				completionHandler.handle(Future.succeededFuture(accept));
			} else if ("discharge".equals(requestType)) {
				// リクエストの種類が "discharge" だったら
				// アクセプト中のポイントがリクエストのポイント以上かつ最も大きい ( 同じなら融通電力量が最も大きい ) ものを選択する
				// → 決めた値段より高く売りたい, そしてその中でも一番高いところに売りたい
				JsonObject accept = null;
				float maxPointPerWh = -1;
				int maxAmountWh = 0;
				for (JsonObject anAccept : accepts) {
					Float aPointPerWh = anAccept.getFloat("pointPerWh");
					if (aPointPerWh != null) {
						if (requestPointPerWh <= aPointPerWh) {
							Integer anAmountWh = anAccept.getInteger("amountWh");
							if (anAmountWh != null) {
								if (maxPointPerWh < 0 || maxPointPerWh < aPointPerWh) {
									maxPointPerWh = aPointPerWh;
									maxAmountWh = anAmountWh;
									accept = anAccept;
								} else if (aPointPerWh == maxPointPerWh) {
									if (maxAmountWh < anAmountWh) {
										maxAmountWh = anAmountWh;
										accept = anAccept;
									}
								}
							} else {
								if (log.isWarnEnabled()) log.warn("no amountWh in accept : " + anAccept);
							}
						}
					} else {
						if (log.isWarnEnabled()) log.warn("no pointPerWh in accept : " + anAccept);
					}
				}
				completionHandler.handle(Future.succeededFuture(accept));
			} else {
				if (log.isWarnEnabled()) log.warn("illegal type in request : " + requestType);
				completionHandler.handle(Future.failedFuture("illegal type in request : " + requestType));
			}
		} else {
			if (log.isWarnEnabled()) log.warn("could never happen");
			completionHandler.handle(Future.failedFuture("could never happen"));
		}
	}

}
