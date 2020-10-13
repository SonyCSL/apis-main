package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
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
import jp.co.sony.csl.dcoes.apis.main.evaluation.safety.GridBranchCurrentCapacity;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

/**
 * 最初の電圧リファレンスを起動する.
 * @author OES Project
 */
public class DealActivation extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealActivation.class);

	private boolean isFirstDeal_ = false;
	private JsonObject currentMasterDeal_;
	private String currentVoltageReferenceUnitId_;
	private Float operationGridVoltageV_;
	private boolean willMoveVoltageReference_ = false;
	private DDCon.Mode oldVoltageReferenceNewMode_;
	private float oldVoltageReferenceGridCurrentA_ = 0F;
	private DDCon.Mode oldMode_;
	private DDCon.Mode newMode_;
	private Float dealGridCurrentA_;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution} 開始時にコピーしたものが渡される.
	 * @param deal 処理対象 DEAL オブジェクト
	 * @param otherDeals 同時に存在している他の DEAL オブジェクトのリスト
	 */
	public DealActivation(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}
	/**
	 * インスタンスを生成する.
	 * 他の {@link AbstractDealExecution} の内部状態をそのまま受け継ぐため初期化不要.
	 * @param other 他の abstractdealexecution オブジェクト
	 */
	public DealActivation(AbstractDealExecution other) {
		super(other);
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		// 制御対象として扱って良いか捨てるか判断する
		String dealValidationError = validateDeal_();
		if (dealValidationError == null) {
			// 電圧リファレンス側ユニットをつけて良いか判断する
			if (canActivate_()) {
				// いろいろ準備する
				prepareActivate_(resPrepare -> {
					if (resPrepare.succeeded()) {
						// 電圧リファレンス側のユニットをつける
						activateDcdc_(resActivateDcdc -> {
							if (resActivateDcdc.succeeded()) {
								// DEAL オブジェクトを activate 状態にする
								activateDeal_(resActivateDeal -> {
									if (resActivateDeal.succeeded()) {
										// 必要に応じて元の Master Deal のフラグを落とす
										moveMasterDeal_(resMoveMasterDeal -> {
											if (resMoveMasterDeal.succeeded()) {
												if (isFirstDeal_) {
													// 最初の融通なら電圧ランプアップ処理に移行する
													new DealRampingUp(this).execute(completionHandler);
												} else {
													// 二番目以降の融通なら電流コンペンセイション処理に移行する
													new DealCompensation(this).execute(completionHandler);
												}
											} else {
												completionHandler.handle(resMoveMasterDeal);
											}
										});
									} else {
										completionHandler.handle(resActivateDeal);
									}
								});
							} else {
								completionHandler.handle(resActivateDcdc);
							}
						});
					} else {
						completionHandler.handle(resPrepare);
					}
				});
			} else {
				// 電圧リファレンス側ユニットをつけて良い状況じゃなければ何もしない
				completionHandler.handle(Future.succeededFuture());
			}
		} else {
			// 捨てる場合は融通破棄処理に移行する
			new DealDispositionWithAbortReason(this, dealValidationError).execute(completionHandler);
		}
	}

	/**
	 * 融通を制御対象として正式に採用するか否か判定する.
	 * NG なら理由を返す.
	 * @return NG なら NG 理由の文字列. OK なら {@code null}
	 */
	private String validateDeal_() {
		// リセット回数制限をチェック
		String error = checkNumberOfResets_();
		if (error == null) {
			// 融通方向をチェック
			error = checkDealDirection_();
		}
		if (error == null) {
			// 電流容量をチェック
			error = checkCurrentCapacity_();
		}
		return error;
	}
	/**
	 * DEAL オブジェクトのリセット回数をチェックする.
	 * 上限を超えていたらエラー文字列を返す.
	 * @return Ng ならエラー文字列. OK なら {@code null}
	 */
	private String checkNumberOfResets_() {
		// リセット上限回数 : POLICY.gridMaster.deal.resetLimit
		Integer resetLimit_ = PolicyKeeping.cache().getInteger("gridMaster", "deal", "resetLimit");
		if (resetLimit_ != null) {
			if (resetLimit_ <= Deal.numberOfResets(deal_)) {
				String msg = "deal reset limit exceeded : " + resetLimit_;
				ErrorUtil.report(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, msg);
				return msg;
			}
		} else {
			String msg = "data deficiency; POLICY.gridMaster.deal.resetLimit : " + PolicyKeeping.cache().getValue("gridMaster", "deal", "resetLimit");
			ErrorUtil.report(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, msg);
			return msg;
		}
		return null;
	}
	/**
	 * 融通の方向をチェックする.
	 * NG ならエラー文字列を返す.
	 * @return NG ならエラー文字列. OK なら {@code null}
	 */
	private String checkDealDirection_() {
		for (JsonObject aDeal : otherDeals_) {
			// 他の融通全てについて
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				// 少なくとも一方のユニットが起動している融通について
				if (Deal.isChargeUnit(aDeal, dischargeUnitId_)) {
					// その融通の受電側がこの融通の送電側なら NG
					String msg = "other charge side deal found on discharge unit : " + dischargeUnitId_ + " ; other deal : " + aDeal;
					if (log.isInfoEnabled()) log.info(msg);
					return msg;
				}
				if (Deal.isDischargeUnit(aDeal, chargeUnitId_)) {
					// その融通の送電側がこの融通の受電側なら NG
					String msg = "other discharge side deal found on charge unit : " + chargeUnitId_ + " ; other deal : " + aDeal;
					if (log.isInfoEnabled()) log.info(msg);
					return msg;
				}
			}
		}
		return null;
	}
	/**
	 * 電流容量をチェックする.
	 * NG ならエラー文字列を返す.
	 * 判定は {@link GridBranchCurrentCapacity#checkNewDeal(Vertx, JsonObject, JsonObject, List)} で.
	 * @return NG ならエラー文字列. OK なら {@code null}
	 */
	private String checkCurrentCapacity_() {
		return GridBranchCurrentCapacity.checkNewDeal(vertx_, policy_, deal_, otherDeals_);
	}

	private boolean canActivate_() {
		// 最初の融通かどうかを判定しておく
		isFirstDeal_ = isFirstDeal_();
		return (isFirstDeal_) ? canActivateFirstDeal_() : canActivateNonFirstDeal_();
	}
	private boolean isFirstDeal_() {
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				// 他に一つでも起動済みの融通があったら false
				return false;
			}
		}
		return true;
	}
	/**
	 * 最初の融通を起動して良いか判定する.
	 * @return OK なら {@code true}
	 */
	private boolean canActivateFirstDeal_() {
		Float gridVoltageV = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "meter", "vg");
		if (gridVoltageV != null) {
			Float minOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "min");
			Float gridVoltageSeparationV = PolicyKeeping.cache().getFloat("gridVoltageSeparationV");
			Float gridUvloMaskV = PolicyKeeping.cache().getFloat("gridUvloMaskV");
			if (minOperationGridVoltageV != null && gridVoltageSeparationV != null && gridUvloMaskV != null) {
				float gridVoltageMaxV = minOperationGridVoltageV + (gridVoltageSeparationV * 2F);
				if (gridVoltageV <= gridVoltageMaxV) {
					// 現在のグリッド電圧が POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV x 2 以下で
					float maskMinV = minOperationGridVoltageV - gridUvloMaskV;
					float maskMaxV = minOperationGridVoltageV + gridUvloMaskV;
					if (gridVoltageV < maskMinV || maskMaxV < gridVoltageV) {
						// POLICY.operationGridVoltageVRange.min - POLICY.gridUvloMaskV 未満 または POLICY.operationGridVoltageVRange.min + POLICY.gridUvloMaskV を超えていたら OK
						return true;
					} else {
						// POLICY.operationGridVoltageVRange.min - POLICY.gridUvloMaskV 以上 POLICY.operationGridVoltageVRange.min + POLICY.gridUvloMaskV 以下なら NG
						if (log.isInfoEnabled()) log.info("can not start voltage reference yet ( grid voltage : " + gridVoltageV + ", should be lower than : " + maskMinV + " or greater than : " + maskMaxV + " ) ...");
					}
				} else {
					// 現在のグリッド電圧が POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV x 2 を超えていたら GLOBAL ERROR
					ErrorUtil.report(vertx_, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.ERROR, "invalid grid voltage : " + gridVoltageV + ", should be lower than or equal to : " + gridVoltageMaxV);
				}
			} else {
				ErrorUtil.report(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency ; POLICY.operationGridVoltageVRange.min : " + minOperationGridVoltageV + "POLICY.gridVoltageSeparationV : " + gridVoltageSeparationV + ", POLICY.gridUvloMaskV : " + gridUvloMaskV);
			}
		} else {
			ErrorUtil.report(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.vg value in master side unit data : " + masterSideUnitData_());
		}
		return false;
	}
	/**
	 * 二番目以降の融通を起動して良いか判定する.
	 * @return OK なら {@code true}
	 */
	private boolean canActivateNonFirstDeal_() {
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.isStarted(aDeal) && !Deal.isDeactivated(aDeal)) {
				// 他の融通の中で「start 済みで deactivate していない」ものが一つでもあれば OK
				return true;
			}
		}
		if (log.isInfoEnabled()) log.info("can not start master side unit yet ( other deal should be ramping up ) ...");
		// 一つもなければ「最初の融通がまだ電圧ランプアップ中」のはずなので NG
		return false;
	}

	/**
	 * 起動に向けていろいろ準備する.
	 * @param completionHandler the completion handler
	 */
	private void prepareActivate_(Handler<AsyncResult<Void>> completionHandler) {
		// 電流コンペンセイションに必要なデータを記録しておくため電圧リファレンスの制御状態を最新にする
		prepareVoltageReference_(resPrepareVoltageReference -> {
			if (resPrepareVoltageReference.succeeded()) {
				// 電圧リファレンス側を反転したり電圧リファレンスを移動したりするための判定や準備をする
				prepareFlipAndMove_();
				// 制御対象ユニットのモードを準備する
				prepareMode_();
				if (willMoveVoltageReference_) {
					// 電圧リファレンスを移動するなら
					// 移動先でのグリッド電圧の設定値を決める
					String voltageReferenceTakeOverDvg = Policy.voltageReferenceTakeOverDvg(policy_);
					if ("theoretical".equals(voltageReferenceTakeOverDvg)) {
						// 移動元ユニットでの設定値 ( dvg ) を記録しておく
						operationGridVoltageV_ = DealExecution.unitDataCache.getFloat(currentVoltageReferenceUnitId_, "dcdc", "vdis", "dvg");
						if (operationGridVoltageV_ == null) {
							ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.vdis.dvg value in voltage reference unit data : " + DealExecution.unitDataCache.getJsonObject(currentVoltageReferenceUnitId_), completionHandler);
							return;
						}
					}
					// theoretical でなければ ( つまり actual なら ) 制御時に null を送っておけば勝手に向こうで測定値 ( vg ) を指定してくれるのでヨシ!
				}
				dealGridCurrentA_ = Deal.dealGridCurrentA(deal_);
				if (dealGridCurrentA_ != null) {
					if (isFirstDeal_ || (willMoveVoltageReference_ && DDCon.Mode.WAIT == masterSideUnitDDConMode_())) {
						// 最初の融通または電圧リファレンス移動が起きしかも電圧リファレンス側が WAIT である ( つまり WAIT → VR の制御となる ) 場合
						float result = 0;
						if ("dischargeUnit".equals(masterSide_)) {
							result = - dealGridCurrentA_;
						} else {
							result = dealGridCurrentA_;
						}
						// 電流コンペンセイションのターゲット値は融通電流そのもの ( 送電側か受電側で負号は異なる )
						deal_.put("compensationTargetVoltageReferenceGridCurrentA", result);
						if (log.isInfoEnabled()) log.info("compensationTargetVoltageReferenceGridCurrentA ( +/- dealGridCurrentA ) : " + result);
						completionHandler.handle(Future.succeededFuture());
					} else {
						// 最初の融通ではなく WAIT → VR の制御でもない場合
						final String targetVoltageReferenceUnitId = (willMoveVoltageReference_) ? masterSideUnitId_() : currentVoltageReferenceUnitId_;
						if (targetVoltageReferenceUnitId != null) {
							if (targetVoltageReferenceUnitId.equals(masterSideUnitId_())) {
								// 電圧リファレンスが移動してくる場合
								Float masterSideGridCurrentA = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "meter", "ig");
								if (masterSideGridCurrentA != null) {
									float result = masterSideGridCurrentA;
									if ("dischargeUnit".equals(masterSide_)) {
										result -= dealGridCurrentA_;
									} else {
										result += dealGridCurrentA_;
									}
									// 電流コンペンセイションのターゲット値は当該融通の電圧リファレンス側ユニットの現在の ig 値に融通電流 ( 送電側か受電側で負号は異なる ) を加算したもの
									deal_.put("compensationTargetVoltageReferenceGridCurrentA", result);
									if (log.isInfoEnabled()) log.info("compensationTargetVoltageReferenceGridCurrentA ( masterSideGridCurrentA +/- dealGridCurrentA ) : " + masterSideGridCurrentA + " , " + dealGridCurrentA_ + " , " + result);
									completionHandler.handle(Future.succeededFuture());
								} else {
									ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.ig value in master side unit data : " + masterSideUnitData_(), completionHandler);
								}
							} else {
								// 電圧リファレンスが移動してこない場合
								Float targetVoltageReferenceUnitGridCurrentA = DealExecution.unitDataCache.getFloat(targetVoltageReferenceUnitId, "dcdc", "meter", "ig");
								if (targetVoltageReferenceUnitGridCurrentA != null) {
									float result = targetVoltageReferenceUnitGridCurrentA;
									// 電流コンペンセイションのターゲット値は現在の電圧リファレンスユニットの現在の ig 値そのもの
									deal_.put("compensationTargetVoltageReferenceGridCurrentA", result);
									if (log.isInfoEnabled()) log.info("compensationTargetVoltageReferenceGridCurrentA ( targetVoltageReferenceUnitGridCurrentA ) : " + result);
									completionHandler.handle(Future.succeededFuture());
								} else {
									ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.ig value in voltage reference unit data : " + DealExecution.unitDataCache.getJsonObject(targetVoltageReferenceUnitId), completionHandler);
								}
							}
						} else {
							ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "no voltage reference found", completionHandler);
						}
					}
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dealGridCurrentA value in deal : " + deal_, completionHandler);
				}
			} else {
				completionHandler.handle(resPrepareVoltageReference);
			}
		});
	}
	/**
	 * 電圧リファレンスの制御状態を最新にする.
	 * @param completionHandler the completion handler
	 */
	private void prepareVoltageReference_(Handler<AsyncResult<Void>> completionHandler) {
		// 電圧リファレンスのユニット ID を探しておく
		currentVoltageReferenceUnitId_ = DealExecution.voltageReferenceUnitId();
		if (currentVoltageReferenceUnitId_ != null) {
			// クラスタ上にすでに電圧リファレンスが存在する場合
			if (log.isInfoEnabled()) log.info("voltage reference unit : " + currentVoltageReferenceUnitId_);
			if (Deal.isInvolved(deal_, currentVoltageReferenceUnitId_)) {
				// この融通のどちらかがすでに電圧リファレンスの場合
				if (log.isDebugEnabled()) log.debug("no need to update voltage reference unit status");
				// 融通処理の最初に両ユニットのデータを更新したので何もする必要がない
				completionHandler.handle(Future.succeededFuture());
			} else {
				// この融通の両ユニットとも電圧リファレンスではない場合
				if (log.isDebugEnabled()) log.debug("update voltage reference unit status...");
				// 電圧リファレンスユニットのデバイス制御状態のキャッシュを更新しておく
				updateUnitDcdcStatus_(currentVoltageReferenceUnitId_, completionHandler);
			}
		} else {
			// まだどこにも電圧リファレンスが存在しない場合
			if (log.isInfoEnabled()) log.info("no voltage reference unit found");
			// なにもする必要がない
			completionHandler.handle(Future.succeededFuture());
		}
	}
	/**
	 * 電圧リファレンス側を反転したり電圧リファレンスを移動したりするための判定や準備をする.
	 */
	private void prepareFlipAndMove_() {
		List<String> largeCapacityUnitIds = Policy.largeCapacityUnitIds(policy_);
		if (largeCapacityUnitIds != null && !largeCapacityUnitIds.isEmpty()) {
			// 大容量ユニットの設定がある場合
			if (isFirstDeal_) {
				// 最初の融通の場合
				if (!largeCapacityUnitIds.contains(masterSideUnitId_()) && largeCapacityUnitIds.contains(slaveSideUnitId_())) {
					// どちらかのユニットが大容量ユニットの場合
					// 電圧リファレンス側を反転させるかどうか判定する
					if (tryToFlipMasterSide_()) {
						if (log.isInfoEnabled()) log.info("master side was flipped : " + masterSide_);
					} else {
						if (log.isWarnEnabled()) log.warn("could not flip master side");
					}
				}
			} else {
				// 二番目以降の融通の場合
				if (currentVoltageReferenceUnitId_ != null && !largeCapacityUnitIds.contains(currentVoltageReferenceUnitId_)) {
					// 現在の電圧リファレンスが大容量ユニットではない場合
					if (log.isInfoEnabled()) log.info("voltage reference unit is not a large capacity unit");
					if (largeCapacityUnitIds.contains(masterSideUnitId_())) {
						// この融通の電圧リファレンス側ユニットが大容量ユニットの場合
						// 電圧リファレンスの移動が発生します
						willMoveVoltageReference_ = true;
						if (log.isInfoEnabled()) log.info("will move voltage reference");
					} else if (largeCapacityUnitIds.contains(slaveSideUnitId_())) {
						// この融通の電圧リファレンス側ユニットが大容量ユニットではなく
						// この融通の電圧リファレンス側ではない方のユニットが大容量ユニットの場合
						// 電圧リファレンス側を反転させるかどうか判定する
						if (tryToFlipMasterSide_()) {
							if (log.isInfoEnabled()) log.info("master side was flipped : " + masterSide_);
							// 反転できた場合にも
							// 電圧リファレンスの移動が発生します
							willMoveVoltageReference_ = true;
							if (log.isInfoEnabled()) log.info("will move voltage reference");
						} else {
							if (log.isWarnEnabled()) log.warn("could not flip master side");
						}
					}
				} else {
					// まだ電圧リファレンスが存在しない, もしくは現在の電圧リファレンスが大容量ユニットである場合
					// なにもしない
					if (log.isInfoEnabled()) log.info("no voltage reference unit or voltage reference unit is a large capacity unit");
				}
				if (willMoveVoltageReference_) {
					// 電圧リファレンスの移動が発生することになった場合
					// 現在の master deal を覚えておく
					currentMasterDeal_ = masterDeal_();
					// 現在の電圧リファレンスの新しいモード ( 電圧リファレンス移動後のモード ) を決めておく
					for (JsonObject aDeal : otherDeals_(currentVoltageReferenceUnitId_)) {
						// 他の融通全てについて
						if (oldVoltageReferenceNewMode_ == null || Deal.slaveSideUnitMustBeActive(aDeal)) {
							// まだ候補が出ていない若くは電圧リファレンスでない側が点いている場合 ( これ結果は同じだけど "両端ユニットが点いている場合" なのかもしれない )
							// → もう制御が始まっている融通が優先だけど無ければそうでない融通から判定する ということ
							if (Deal.isDischargeUnit(aDeal, currentVoltageReferenceUnitId_)) {
								// 現在の電圧リファレンスが送電側ユニットの場合 DISCHARGE
								oldVoltageReferenceNewMode_ = DDCon.Mode.DISCHARGE;
							} else {
								// 現在の電圧リファレンスが受電側ユニットの場合 CHARGE
								oldVoltageReferenceNewMode_ = DDCon.Mode.CHARGE;
							}
						}
					}
					Float currentVoltageReferenceIg = DealExecution.unitDataCache.getFloat(currentVoltageReferenceUnitId_, "dcdc", "meter", "ig");
					if (oldVoltageReferenceNewMode_ != null && currentVoltageReferenceIg != null) {
						// 現在の電圧リファレンスの新しいモードが決まっていたらそのユニットでのグリッド電流測定値を記録しておく
						oldVoltageReferenceGridCurrentA_ = currentVoltageReferenceIg;
						if (log.isInfoEnabled()) log.info("old voltage reference will be : " + oldVoltageReferenceNewMode_ + " ( " + oldVoltageReferenceGridCurrentA_ + " )");
					} else {
						if (log.isWarnEnabled()) log.warn("failed to move voltage reference ; oldVoltageReferenceNewMode_ : " + oldVoltageReferenceNewMode_ + ", oldVoltageReferenceGridCurrentA_ : " + oldVoltageReferenceGridCurrentA_);
					}
				}
			}
		}
	}
	/**
	 * モードを準備する.
	 */
	private void prepareMode_() {
		// まず現在のモードを記録する
		oldMode_ = masterSideUnitDDConMode_();
		if (isFirstDeal_ || willMoveVoltageReference_) {
			// 最初の融通か電圧リファレンスの移動が発生する場合は VOLTAGE_REFERENCE
			newMode_ = DDCon.Mode.VOLTAGE_REFERENCE;
		} else {
			if (DDCon.Mode.WAIT == oldMode_) {
				// 現在 WAIT なら
				// 送電側なら DISCHARGE 受電側なら CHARGE
				newMode_ = ("dischargeUnit".equals(masterSide_)) ? DDCon.Mode.DISCHARGE : DDCon.Mode.CHARGE;
			} else {
				// どちらでもない ( DISCHARGE か CHARGE のはず ) ならそのまま
				newMode_ = oldMode_;
			}
		}
		if (log.isInfoEnabled()) log.info("old mode : " + oldMode_ + ", new mode : " + newMode_);
	}

	/**
	 * 電圧リファレンス側のユニットを制御する.
	 * @param completionHandler the completion handler
	 */
	private void activateDcdc_(Handler<AsyncResult<Void>> completionHandler) {
		// DEAL オブジェクト中に dcdc/failBeforeActivate = true があったら fail させる
		if (testFeature_failIfNeed_("dcdc", "failBeforeActivate", completionHandler)) return;
		if (willMoveVoltageReference_) {
			// 電圧リファレンスを移動する場合
			if (log.isInfoEnabled()) log.info("move voltage reference");
			// 移動元ユニットに voltageReferenceWillHandOver
			controlDcdc_(currentVoltageReferenceUnitId_, "voltageReferenceWillHandOver", null, resWillHandOver -> {
				if (resWillHandOver.succeeded()) {
					// prepareActivate_() で決めた operationGridVoltageV_ を新しい dvg としてパラメタに仕込んで...
					JsonObject params = new JsonObject().put("gridVoltageV", operationGridVoltageV_);
					// 移動先ユニットに voltageReferenceWillTakeOver
					controlMasterSideUnitDcdc_("voltageReferenceWillTakeOver", params, resWillTakeOver -> {
						if (resWillTakeOver.succeeded()) {
							if (log.isInfoEnabled()) log.info("new voltage reference started, retire old voltage reference");
							// prepareActivate_() で決めた oldVoltageReferenceNewMode_ と oldVoltageReferenceGridCurrentA_ をパラメタに仕込んで...
							params.clear().put("mode", oldVoltageReferenceNewMode_.name()).put("gridCurrentA", Math.abs(oldVoltageReferenceGridCurrentA_));
							// 移動元ユニットに voltageReferenceDidHandOver
							controlDcdc_(currentVoltageReferenceUnitId_, "voltageReferenceDidHandOver", params, resDidHandOver -> {
								if (resDidHandOver.succeeded()) {
									// 移動先ユニットに voltageReferenceDidTakeOver
									// 成功した場合に DEAL オブジェクト中に dcdc/failBeforeActivate = true があったら fail させる
									controlMasterSideUnitDcdc_("voltageReferenceDidTakeOver", null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterActivate", completionHandler));
								} else {
									completionHandler.handle(resDidHandOver);
								}
							});
						} else {
							completionHandler.handle(resWillTakeOver);
						}
					});
				} else {
					completionHandler.handle(resWillHandOver);
				}
			});
		} else {
			// 電圧リファレンスを移動しない場合
			if (DDCon.Mode.VOLTAGE_REFERENCE == oldMode_) {
				// もともと電圧リファレンスだった場合
				if (log.isInfoEnabled()) log.info("no need to control voltage reference");
				// DEAL オブジェクト中に dcdc/failAfterActivate = true があったら fail させる
				if (testFeature_failIfNeed_("dcdc", "failAfterActivate", completionHandler)) return;
				// 何もする必要なし
				completionHandler.handle(Future.succeededFuture());
			} else if (DDCon.Mode.WAIT == oldMode_) {
				// 元が WAIT だった場合
				if (log.isInfoEnabled()) log.info("start master side unit");
				// 当該融通の融通電流値をパラメタに仕込んで
				JsonObject params = new JsonObject().put("gridCurrentA", dealGridCurrentA_);
				// 電圧リファレンス側ユニットに prepareMode_() で決めた newMode_ を命令
				// 成功した場合に DEAL オブジェクト中に dcdc/failAfterActivate = true があったら fail させる
				controlMasterSideUnitDcdc_(newMode_.name(), params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterActivate", completionHandler));
			} else {
				// もともとが電圧リファレンスでも WAIT でもない ( DISCHARGE か CHARGE のはず ) 場合
				// 新しい電流値を算出し
				float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(masterSideUnitId_())) + dealGridCurrentA_;
				if (log.isInfoEnabled()) log.info("dig : " + JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "param", "dig") + " -> " + newDig);
				// 新しい電流値をパラメタに仕込んで
				JsonObject params = new JsonObject().put("gridCurrentA", newDig);
				// 電圧リファレンス側ユニットに電流値の変更を命令
				// 成功した場合に DEAL オブジェクト中に dcdc/failAfterActivate = true があったら fail させる
				controlMasterSideUnitDcdc_("current", params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterActivate", completionHandler));
			}
		}
	}

	/**
	 * master deal の目印を移動する.
	 * @param completionHandler the completion handler
	 */
	private void moveMasterDeal_(Handler<AsyncResult<Void>> completionHandler) {
		if (willMoveVoltageReference_) {
			// 電圧リファレンスを移動した場合
			// 移動元の DEAL オブジェクトから master deal の印を外す
			DealUtil.isMaster(vertx_, currentMasterDeal_, false, res -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, res, completionHandler));
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * 融通情報を activate 完了状態にする.
	 * @param completionHandler the completion handler
	 */
	private void activateDeal_(Handler<AsyncResult<Void>> completionHandler) {
		DealUtil.activate(vertx_, deal_, referenceDateTimeString_(), resActivateDeal -> {
			if (resActivateDeal.succeeded()) {
				// 最初の融通か電圧リファレンスを移動した場合
				// 当該 DEAL オブジェクトを master deal にする
				DealUtil.isMaster(vertx_, deal_, isFirstDeal_ || willMoveVoltageReference_, resIsMaster -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resIsMaster, completionHandler));
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx_, resActivateDeal.cause(), completionHandler);
			}
		});
	}

}
