package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 電圧リファレンス側じゃない方のユニットを起動し電流コンペンセイション動作を実行する.
 * @author OES Project
 */
public class DealCompensation extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealCompensation.class);

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution} 開始時にコピーしたものが渡される.
	 * @param deal 処理対象 DEAL オブジェクト
	 * @param otherDeals 同時に存在している他の DEAL オブジェクトのリスト
	 */
	public DealCompensation(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}
	/**
	 * インスタンスを生成する.
	 * 他の {@link AbstractDealExecution} の内部状態をそのまま受け継ぐため初期化不要.
	 * @param other 他の abstractdealexecution オブジェクト
	 */
	public DealCompensation(AbstractDealExecution other) {
		super(other);
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		if (DDCon.Mode.WAIT != masterSideUnitDDConMode_()) {
			// 電圧リファレンスじゃない側のユニットをつける
			warmUpDcdc_(resWarmUpDcdc -> {
				if (resWarmUpDcdc.succeeded()) {
					// DEAL オブジェクトを warmUp 状態にする
					warmUpDeal_(resWarmUpDeal -> {
						if (resWarmUpDeal.succeeded()) {
							// 電流コンペンセイションを実行する
							doCompensate_(resCompensate -> {
								if (resCompensate.succeeded()) {
									if (log.isInfoEnabled()) log.info("deal compensated");
									// コンペンセイション実行後の融通電流値を DEAL オブジェクトに記録する
									saveCompensatedGridCurrentA_(resSave -> {
										if (resSave.succeeded()) {
											// DEAL オブジェクトを start 状態にする
											DealUtil.start(vertx_, deal_, referenceDateTimeString_(), resStart -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resStart, completionHandler));
										} else {
											completionHandler.handle(resSave);
										}
									});
								} else {
									completionHandler.handle(resCompensate);
								}
							});
						} else {
							completionHandler.handle(resWarmUpDeal);
						}
					});
				} else {
					completionHandler.handle(resWarmUpDcdc);
				}
			});
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "invalid master side unit status; unit : " + masterSideUnitId_() + ", mode : " + masterSideUnitDDConMode_(), completionHandler);
		}
	}

	/**
	 * 電圧リファレンス側じゃない方のユニットを制御する.
	 * @param completionHandler the completion handler
	 */
	private void warmUpDcdc_(Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isWarmedUp(deal_)) {
			// DEAL オブジェクト中に dcdc/failBeforeWarmUp = true があったら fail させる
			if (testFeature_failIfNeed_("dcdc", "failBeforeWarmUp", completionHandler)) return;
			Float dealGridCurrentA = Deal.dealGridCurrentA(deal_);
			if (dealGridCurrentA != null) {
				if (DDCon.Mode.WAIT == slaveSideUnitDDConMode_()) {
					// WAIT だったら
					if (log.isInfoEnabled()) log.info("start slave side unit");
					// CHARGE か DISCHARGE か決めて
					DDCon.Mode slaveSideUnitNewDDConMode = ("dischargeUnit".equals(masterSide_)) ? DDCon.Mode.CHARGE : DDCon.Mode.DISCHARGE;
					// 融通電流値をパラメタに仕込んで
					JsonObject params = new JsonObject().put("gridCurrentA", dealGridCurrentA);
					// モード変更を命令
					// 成功した場合に DEAL オブジェクト中に dcdc/failAfterWarmUp = true があったら fail させる
					controlSlaveSideUnitDcdc_(slaveSideUnitNewDDConMode.name(), params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterWarmUp", completionHandler));
				} else {
					// WAIT じゃなかったら
					// 現在参加中の他の融通情報から融通電流を合計し
					// さらに当該融通の融通電流を加え
					float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(slaveSideUnitId_())) + dealGridCurrentA;
					if (log.isInfoEnabled()) log.info("dig : " + JsonObjectUtil.getFloat(slaveSideUnitData_(), "dcdc", "param", "dig") + " -> " + newDig);
					// 電流値をパラメタに仕込んで
					JsonObject params = new JsonObject().put("gridCurrentA", newDig);
					// 電流値の変更を命令
					// 成功した場合に DEAL オブジェクト中に dcdc/failAfterWarmUp = true があったら fail させる
					controlSlaveSideUnitDcdc_("current", params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterWarmUp", completionHandler));
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dealGridCurrentA value in deal : " + deal_, completionHandler);
			}
		} else {
			if (log.isInfoEnabled()) log.info("already warmed up");
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * 融通情報を warmUp 完了状態にする.
	 * @param completionHandler the completion handler
	 */
	private void warmUpDeal_(Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isWarmedUp(deal_)) {
			DealUtil.warmUp(vertx_, deal_, referenceDateTimeString_(), res -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, res, completionHandler));
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * コンペンセイション実行後の融通電流値を DEAL オブジェクトに記録する
	 * @param completionHandler the completion handler
	 */
	private void saveCompensatedGridCurrentA_(Handler<AsyncResult<Void>> completionHandler) {
		// 両側ユニットのデバイス制御状態を取得する
		Future<Void> dischargeFuture = Future.future();
		Future<Void> chargeFuture = Future.future();
		updateUnitDcdcStatus_(dischargeUnitId_, dischargeFuture);
		updateUnitDcdcStatus_(chargeUnitId_, chargeFuture);
		CompositeFuture.<Void, Void>all(dischargeFuture, chargeFuture).setHandler(ar -> {
			if (ar.succeeded()) {
				// 両端ユニットのグリッド電流測定値を取得する
				Float dischargeUnitIg = JsonObjectUtil.getFloat(dischargeUnitData_, "dcdc", "meter", "ig");
				Float chargeUnitIg = JsonObjectUtil.getFloat(chargeUnitData_, "dcdc", "meter", "ig");
				if (dischargeUnitIg != null && chargeUnitIg != null) {
					if (log.isInfoEnabled()) log.info("discharge unit ig : " + dischargeUnitIg + ", charge unit ig : " + chargeUnitIg);
					// 両端ユニットそれぞれ現在参加中の他の融通の融通電流を合計し
					float dischargeUnitSumOfOtherDealCompensatedGridCurrentA = sumOfOtherDealCompensatedGridCurrentAs_(dischargeUnitId_);
					float chargeUnitSumOfOtherDealCompensatedGridCurrentA = sumOfOtherDealCompensatedGridCurrentAs_(chargeUnitId_);
					// 両端ユニットそれぞれ現在のグリッド電流から他の融通の融通電流の合計を引くと
					float dischargeUnitCompensatedGridCurrentA = dischargeUnitIg - dischargeUnitSumOfOtherDealCompensatedGridCurrentA;
					float chargeUnitCompensatedGridCurrentA = chargeUnitIg - chargeUnitSumOfOtherDealCompensatedGridCurrentA;
					// それが当該融通における融通電流になるので DEAL オブジェクトに記録する
					deal_.put("dischargeUnitCompensatedGridCurrentA", dischargeUnitCompensatedGridCurrentA);
					deal_.put("chargeUnitCompensatedGridCurrentA", chargeUnitCompensatedGridCurrentA);
					if (log.isInfoEnabled()) log.info("discharge unit compensated deal ig : " + dischargeUnitCompensatedGridCurrentA + ", charge unit compensated deal ig : " + chargeUnitCompensatedGridCurrentA);
					completionHandler.handle(Future.succeededFuture());
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.ig value in discharge and/or charge unit data : " + dischargeUnitData_ + ", " + chargeUnitData_, completionHandler);
				}
			} else {
				completionHandler.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	/**
	 * 電流コンペンセイションを実行する.
	 * @param completionHandler the completion handler
	 */
	private void doCompensate_(Handler<AsyncResult<Void>> completionHandler) {
		// DEAL オブジェクト中に dcdc/failBeforeCompensate = true があったら fail させる
		if (testFeature_failIfNeed_("dcdc", "failBeforeCompensate", completionHandler)) return;
		// もろもろ準備
		Integer limitOfTrials = PolicyKeeping.cache().getInteger("gridMaster", "currentCompensation", "limitOfTrials");
		Float driftAllowanceA = PolicyKeeping.cache().getFloat("gridMaster", "currentCompensation", "driftAllowanceA");
		if (limitOfTrials != null && driftAllowanceA != null) {
			Float compensationTargetVoltageReferenceGridCurrentA = Deal.compensationTargetVoltageReferenceGridCurrentA(deal_);
			if (compensationTargetVoltageReferenceGridCurrentA != null) {
				if (log.isInfoEnabled()) log.info("compensationTargetVoltageReferenceGridCurrentA : " + compensationTargetVoltageReferenceGridCurrentA);
				if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
					// 電圧リファレンス側が電圧リファレンスなら → つまり当該融通の一方が電圧リファレンスなら
					if (masterSide_.equals(referenceSide_)) {
						// 電圧リファレンス側がリファレンス側なら → コンペンセイションは必要
						if ("dischargeUnit".equals(referenceSide_)) {
							// 送電側がリファレンス側なので ( つまり同時に送電側が電圧リファレンスなので )
							// 送電側ユニットを電圧リファレンスに, 受電側ユニットを調整ユニットに指定して電流コンペンセイションを実行
							// 成功した場合で DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA, compensationTargetVoltageReferenceGridCurrentA, dischargeUnitId_, chargeUnitId_, false).execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate", completionHandler));
						} else {
							// 受電側がリファレンス側なので ( つまり同時に受電側が電圧リファレンスなので )
							// 受電側ユニットを電圧リファレンスに, 送電側ユニットを調整ユニットに指定して電流コンペンセイションを実行
							// 成功した場合で DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA, compensationTargetVoltageReferenceGridCurrentA, chargeUnitId_, dischargeUnitId_, true).execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate", completionHandler));
						}
					} else {
						// 当該融通の一方が電圧リファレンス, かつ
						// 電圧リファレンス側じゃない方がリファレンス側なら
						// 電圧リファレンス側じゃない方で電流を指定した結果が "正" なのでこれで OK ( つまりコンペンセイション処理は不要 )
						// DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
						if (testFeature_failIfNeed_("dcdc", "failAfterCompensate", completionHandler)) return;
						completionHandler.handle(Future.succeededFuture());
					}
				} else {
					// 電圧リファレンス側が電圧リファレンスでないなら → つまり電圧リファレンスは別のところにある
					JsonObject masterDeal = masterDeal_();
					// master deal を見つけて...
					if (masterDeal != null) {
						if ("dischargeUnit".equals(referenceSide_)) {
							// 送電側がリファレンス側なので
							// master deal の電圧リファレンス側ユニットを電圧リファレンスに, 受電側ユニットを調整ユニットに指定して電流コンペンセイションを実行
							// 成功した場合で DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA, compensationTargetVoltageReferenceGridCurrentA, Deal.masterSideUnitId(masterDeal, masterSide_), chargeUnitId_, false).execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate", completionHandler));
						} else {
							// 受電側がリファレンス側なので
							// master deal の電圧リファレンス側ユニットを電圧リファレンスに, 送電側ユニットを調整ユニットに指定して電流コンペンセイションを実行
							// 成功した場合で DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA, compensationTargetVoltageReferenceGridCurrentA, Deal.masterSideUnitId(masterDeal, masterSide_), dischargeUnitId_, true).execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate", completionHandler));
						}
					} else {
						// master deal がないはずがないので GLOBAL ERROR
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "no master deal found", completionHandler);
					}
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no compensationTargetVoltageReferenceGridCurrentA value in deal : " + deal_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; limitOfTrials : " + limitOfTrials + ", driftAllowanceA : " + driftAllowanceA + " in POLICY.gridMaster.currentCompensation values", completionHandler);
		}
	}
	/**
	 * 電流コンペンセイション処理を実行するクラス.
	 * @author OES Project
	 */
	private class CurrentCompensationExecutor_ {
		private int limitOfTrials_;
		private float driftAllowanceA_;
		private float targetValue_;
		private String voltageReferenceUnitId_;
		private String adjusterUnitId_;
		private boolean minus_;
		private boolean toFail_ = false;
		/**
		 * インスタンスを生成する.
		 * @param limitOfTrials 試行回数
		 * @param driftAllowanceA 誤差 [A]
		 * @param targetValue 電圧リファレンスユニットでのグリッド電流の目標値 [A]
		 * @param voltageReferenceUnitId 電圧リファレンスユニットの ID
		 * @param adjusterUnitId 調整ユニットの ID
		 * @param minus 指定値を調整する際のプラスマイナス
		 * TODO : minus パラメタは不要なのでは ?
		 *        調整ユニットが送電側か受電側かで決まる ?
		 *        それとも電圧リファレンスユニットが送電側か受電側かで決まる ?
		 */
		private CurrentCompensationExecutor_(int limitOfTrials, float driftAllowanceA, float targetValue, String voltageReferenceUnitId, String adjusterUnitId, boolean minus) {
			limitOfTrials_ = limitOfTrials;
			driftAllowanceA_ = driftAllowanceA;
			targetValue_ = targetValue;
			voltageReferenceUnitId_ = voltageReferenceUnitId;
			adjusterUnitId_ = adjusterUnitId;
			minus_ = minus;
			if (limitOfTrials_ < 0) {
				// for test ( マイナス値を指定するとその絶対値回数リトライした挙句失敗するようになる )
				limitOfTrials_ = - limitOfTrials_;
				toFail_ = true;
			}
			if (log.isDebugEnabled()) log.debug("limit of trials : " + limitOfTrials_ + ", voltage reference unit : " + voltageReferenceUnitId_ + ", adjuster unit : " + adjusterUnitId_ + ", target value : " + targetValue_ + ", drift allowance : " + driftAllowanceA_ + ", minus : " + minus_ + ", to fail : " + toFail_);
		}
		/**
		 * 電流コンペンセイション処理を実行する.
		 * @param completionHandler the completion handler
		 */
		private void execute_(Handler<AsyncResult<Void>> completionHandler) {
			if (log.isDebugEnabled()) log.debug("limitOfTrials_ : " + limitOfTrials_);
			// 電圧リファレンスユニットのデバイス状態をリフレッシュ
			updateUnitDcdcStatus_(voltageReferenceUnitId_, resVoltageReferenceUnitDcdcStatus -> {
				if (resVoltageReferenceUnitDcdcStatus.succeeded()) {
					Float currentValue = DealExecution.unitDataCache.getFloat(voltageReferenceUnitId_, "dcdc", "meter", "ig");
					if (currentValue != null) {
						if (log.isDebugEnabled()) log.debug("voltage reference unit ig : " + currentValue + ", target : " + targetValue_ + ", allowance : " + driftAllowanceA_);
						// 電圧リファレンスユニットのグリッド電流の測定値と目標値の差を算出
						float diff = Math.abs(currentValue - targetValue_);
						if (log.isDebugEnabled()) log.debug("Math.abs(ig - target) : " + diff + ", allowance : " + driftAllowanceA_);
						if (diff <= driftAllowanceA_ && !toFail_) {
							// 差が誤差範囲以下なら ( そしてテスト機能で失敗を指定されていなければ ) 成功
							if (log.isDebugEnabled()) log.debug("OK ( " + diff + " <= " + driftAllowanceA_ + " )");
							completionHandler.handle(Future.succeededFuture());
						} else {
							//  差が誤差範囲を超えていたら ( あるいはテスト機能で失敗を指定されていれば )
							if (log.isDebugEnabled()) log.debug("NG ( " + diff + " <= " + driftAllowanceA_ + " )");
							if (0 < limitOfTrials_--) {
								// リトライ回数内であればリトライ
								Float dig = DealExecution.unitDataCache.getFloat(adjusterUnitId_, "dcdc", "param", "dig");
								if (dig != null) {
									if (log.isDebugEnabled()) log.debug("adjuster unit dig : " + dig);
									// 調整ユニットのグリッド電流の指定値を調節
									if (minus_) {
										dig -= currentValue - targetValue_;
									} else {
										dig += currentValue - targetValue_;
									}
									if (log.isDebugEnabled()) log.debug("adjuster unit new dig : " + dig);
									// 新しいグリッド電流値をパラメタに仕込んで
									JsonObject params = new JsonObject().put("gridCurrentA", dig);
									// 調整ユニットに電流値の変更を命令
									controlDcdc_(adjusterUnitId_, "current", params, resControlDcdc -> {
										if (resControlDcdc.succeeded()) {
											// 再試行
											execute_(completionHandler);
										} else {
											completionHandler.handle(resControlDcdc);
										}
									});
								} else {
									ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.param.dig value in adjuster unit data : " + DealExecution.unitDataCache.getJsonObject(adjusterUnitId_), completionHandler);
								}
							} else {
								// リトライ回数に達したら失敗
								ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.WARN, "current compensation failed", completionHandler);
							}
						}
					} else {
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.ig value in voltage reference unit data : " + DealExecution.unitDataCache.getJsonObject(voltageReferenceUnitId_), completionHandler);
					}
				} else {
					completionHandler.handle(Future.failedFuture(resVoltageReferenceUnitDcdcStatus.cause()));
				}
			});
		}
	}

}
