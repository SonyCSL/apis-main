package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

/**
 * 融通を終了する.
 * @author OES Project
 */
public class DealDeactivation extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealDeactivation.class);

	private boolean isMaster_;
	private Float operationGridVoltageV_;
	private JsonObject newMasterDeal_;
	private String newVoltageReferenceUnitId_;
	private boolean masterSideWillBeFlipped_ = false;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution} 開始時にコピーしたものが渡される.
	 * @param deal 処理対象 DEAL オブジェクト
	 * @param otherDeals 同時に存在している他の DEAL オブジェクトのリスト
	 */
	public DealDeactivation(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}
	/**
	 * インスタンスを生成する.
	 * 他の {@link AbstractDealExecution} の内部状態をそのまま受け継ぐため初期化不要.
	 * @param other 他の abstractdealexecution オブジェクト
	 */
	public DealDeactivation(AbstractDealExecution other) {
		super(other);
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		if (DDCon.Mode.WAIT != masterSideUnitDDConMode_()) {
			prepareDeactivate_(resPrepare -> {
				if (resPrepare.succeeded()) {
					// 電圧リファレンス側のユニットを止める
					deactivateDcdc_(resDeactivateDcdc -> {
						if (resDeactivateDcdc.succeeded()) {
							// 必要に応じて元の Master Deal のフラグを落とす
							moveMasterDeal_(resMoveMasterDeal -> {
								if (resMoveMasterDeal.succeeded()) {
									// DEAL オブジェクトを deactivate 状態にする
									deactivateDeal_(resDeactivateDeal -> {
										if (resDeactivateDeal.succeeded()) {
											// 融通情報削除処理に移行する
											new DealDisposition(this).doExecute(completionHandler);
										} else {
											completionHandler.handle(resDeactivateDeal);
										}
									});
								} else {
									completionHandler.handle(resMoveMasterDeal);
								}
							});
						} else {
							completionHandler.handle(resDeactivateDcdc);
						}
					});
				} else {
					completionHandler.handle(resPrepare);
				}
			});
		} else {
			deactivateDeal_(resDeactivateDeal -> {
				if (resDeactivateDeal.succeeded()) {
					new DealDisposition(this).doExecute(completionHandler);
				} else {
					completionHandler.handle(resDeactivateDeal);
				}
			});
		}
	}

	/**
	 * 停止に向けていろいろ準備する.
	 * @param completionHandler the completion handler
	 */
	private void prepareDeactivate_(Handler<AsyncResult<Void>> completionHandler) {
		isMaster_ = Deal.isMaster(deal_);
		if (isMaster_) {
			// master deal なら別の融通を master deal にする必要があるので決める
			newMasterDeal_ = newMasterDeal_();
			if (newMasterDeal_ != null) {
				if (log.isInfoEnabled()) log.info("need to move master deal; new master deal : " + newMasterDeal_);
				// 電圧リファレンスを移動するかもしれないので
				// 移動先でのグリッド電圧の設定値を決める
				String voltageReferenceTakeOverDvg = Policy.voltageReferenceTakeOverDvg(policy_);
				if ("theoretical".equals(voltageReferenceTakeOverDvg)) {
					// 移動元ユニットでの設定値 ( dvg ) を記録しておく
					operationGridVoltageV_ = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "vdis", "dvg");
					if (operationGridVoltageV_ == null) {
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.vdis.dvg value in voltage reference unit data : " + masterSideUnitData_(), completionHandler);
						return;
					}
				}
				// theoretical でなければ ( つまり actual なら ) 制御時に null を送っておけば勝手に向こうで測定値 ( vg ) を指定してくれるのでヨシ!
			}
		}
		completionHandler.handle(Future.succeededFuture());
	}
	/**
	 * 次の master deal を決める.
	 * @return 次に master deal になる DEAL オブジェクト
	 */
	private JsonObject newMasterDeal_() {
		boolean canFlipMasterSide = canFlipMasterSide_();
		List<String> largeCapacityUnitIds = Policy.largeCapacityUnitIds(policy_);
		String masterDealSelectionStrategy = Policy.masterDealSelectionStrategy(policy_);
		if ("hoge".equals(masterDealSelectionStrategy)) {
			// こんな値は返ってこない
			// いまのところ必ず else に入るためのダミー
			return null;
		} else {
			// "newestDeal" なら
			JsonObject result = null;
			JsonObject result_ = null;
			JsonObject result__ = null;
			LocalDateTime newestActivateDateTime = null;
			LocalDateTime newestActivateDateTime_ = null;
			LocalDateTime newestActivateDateTime__ = null;
			for (JsonObject aDeal : otherDeals_) {
				if (Deal.masterSideUnitMustBeActive(aDeal)) { // 動いてない DEAL は対象外
					LocalDateTime anActivateDateTime = JsonObjectUtil.getLocalDateTime(aDeal, "activateDateTime");
					String aDealMasterSideUnitId = Deal.masterSideUnitId(aDeal, masterSide_);
					DDCon.Mode aDealMasterSideUnitDDConMode = DDCon.modeFromCode(DealExecution.unitDataCache.getString(aDealMasterSideUnitId, "dcdc", "status", "status"));
					if (DDCon.Mode.VOLTAGE_REFERENCE == aDealMasterSideUnitDDConMode) {
						// 電圧リファレンスが参加している融通を最優先で選ぶ → 電圧リファレンスの移動を避けるため
						if (newestActivateDateTime == null || anActivateDateTime.isAfter(newestActivateDateTime)) {
							// より新しければ
							newestActivateDateTime = anActivateDateTime;
							result = aDeal;
						}
					}
					if (result == null && largeCapacityUnitIds != null && !largeCapacityUnitIds.isEmpty()) {
						// 最優先で選ばれているものがまだ見つかってなければ
						if (newestActivateDateTime_ == null || anActivateDateTime.isAfter(newestActivateDateTime_)) {
							// より新しければ
							if (largeCapacityUnitIds.contains(Deal.masterSideUnitId(aDeal, masterSide_))) {
								// 電圧リファレンス側ユニットが大容量ユニットならば
								newestActivateDateTime_ = anActivateDateTime;
								result_ = aDeal;
							} else if (largeCapacityUnitIds.contains(Deal.slaveSideUnitId(aDeal, masterSide_))) {
								// 電圧リファレンス側じゃ無いユニットが大容量ユニットなら
								if (canFlipMasterSide) {
									// 電圧リファレンス側を反転してもよい状況なら
									newestActivateDateTime_ = anActivateDateTime;
									result_ = aDeal;
								}
							}
						}
					}
					if (result == null && result_ == null) {
						// 最優先も次優先もまだ見つかってなければ
						if (newestActivateDateTime__ == null || anActivateDateTime.isAfter(newestActivateDateTime__)) {
							// 単により新しいものを選ぶ
							newestActivateDateTime__ = anActivateDateTime;
							result__ = aDeal;
						}
					}
				}
			}
			if (result != null) {
				// 最優先が見つかった
				newVoltageReferenceUnitId_ = Deal.masterSideUnitId(result, masterSide_);
				return result;
			} else if (result_ != null) {
				// 次優先が見つかった
				if (largeCapacityUnitIds.contains(Deal.masterSideUnitId(result_, masterSide_))) {
					// 反転しない
					newVoltageReferenceUnitId_ = Deal.masterSideUnitId(result_, masterSide_);
				} else {
					// 反転する
					newVoltageReferenceUnitId_ = Deal.slaveSideUnitId(result_, masterSide_);
					masterSideWillBeFlipped_ = true;
					if (log.isInfoEnabled()) log.info("master side will be flipped");
				}
				return result_;
			} else if (result__ != null) {
				// 最優先も次優先も見つからなかった
				if (masterSide_.equals(Policy.masterSide(policy_))) {
					// 現在の電圧リファレンス側方針が POLICY と同じなら
					newVoltageReferenceUnitId_ = Deal.masterSideUnitId(result__, masterSide_);
				} else {
					// 現在の電圧リファレンス側方針が POLICY と反対なら
					if (canFlipMasterSide) {
						// 電圧リファレンス側を反転してもよい状況なら → 反転を戻す
						newVoltageReferenceUnitId_ = Deal.slaveSideUnitId(result__, masterSide_);
						masterSideWillBeFlipped_ = true;
						if (log.isInfoEnabled()) log.info("master side will be flipped");
					} else {
						// 反転は戻せないのでそのまま
						newVoltageReferenceUnitId_ = Deal.masterSideUnitId(result__, masterSide_);
						if (log.isInfoEnabled()) log.info("could not flip master side");
					}
				}
				return result__;
			}
			return null;
		}
	}

	private void deactivateDcdc_(Handler<AsyncResult<Void>> completionHandler) {
		// DEAL オブジェクト中に dcdc/failBeforeDeactivate = true があったら fail させる
		if (testFeature_failIfNeed_("dcdc", "failBeforeDeactivate", completionHandler)) return;
		if (isMaster_) {
			// master deal である
			if (newMasterDeal_ != null) {
				// master deal の移動が発生する → つまり他に実行中の融通があるということ
				if (log.isInfoEnabled()) log.info("new voltage reference unit : " + newVoltageReferenceUnitId_);
				if (masterSideUnitId_().equals(newVoltageReferenceUnitId_)) {
					// 電圧リファレンスの移動は不要
					if (log.isInfoEnabled()) log.info("no need to move voltage reference");
					// 電圧リファレンスユニットのデバイス制御状態のキャッシュを更新しておく
					// DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
					updateUnitDcdcStatus_(masterSideUnitId_(), res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler)); // update cache for master side unit ...
				} else {
					// 電圧リファレンスの移動が必要
					if (canStopDcdc_()) {
						// デバイスを停止できる
						if (log.isInfoEnabled()) log.info("move voltage reference");
						// 移動元ユニットに voltageReferenceWillHandOver
						controlMasterSideUnitDcdc_("voltageReferenceWillHandOver", null, resWillHandOver -> {
							if (resWillHandOver.succeeded()) {
								// prepareDeactivate_() で決めた operationGridVoltageV_ を新しい dvg としてパラメタに仕込んで...
								JsonObject params = new JsonObject().put("gridVoltageV", operationGridVoltageV_);
								// 移動先ユニットに voltageReferenceWillTakeOver
								controlDcdc_(newVoltageReferenceUnitId_, "voltageReferenceWillTakeOver", params, resWillTakeOver -> {
									if (resWillTakeOver.succeeded()) {
										if (log.isInfoEnabled()) log.info("new voltage reference started, stop old voltage reference");
										// 移動元ユニットに voltageReferenceDidHandOver
										controlMasterSideUnitDcdc_("voltageReferenceDidHandOver", null, resDidHandOver -> {
											if (resDidHandOver.succeeded()) {
												// 移動先ユニットに voltageReferenceDidTakeOver
												// 成功した場合に DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
												controlDcdc_(newVoltageReferenceUnitId_, "voltageReferenceDidTakeOver", null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler));
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
						// master deal であり master deal を移動しようとしており電圧リファレンスの移動が必要である
						// つまり電圧リファレンス側は他の融通に参加していないはず ( 他の融通に参加しているなら電圧リファレンスを移動しないですむように master deal を決めたはず )
						// それなのにデバイスを止められない
						// そんなはずがないので GLOBAL ERROR
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "isMaster_ == true && newMasterDeal_ != null && masterSideUnitId_() != newMasterDealMasterSideUnitId && canStopDcdc_() == false ; deal : " + deal_, completionHandler);
					}
				}
			} else {
				// master deal の移動が発生しない → つまり他に実行中の融通はないということ
				if (log.isInfoEnabled()) log.info("stop voltage reference");
				// デバイスを止めちゃう
				// 成功した場合に DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
				controlMasterSideUnitDcdc_(DDCon.Mode.WAIT.name(), null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler));
			}
		} else {
			// master deal ではない
			if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
				// 電圧リファレンスだった場合
				if (canStopDcdc_()) {
					// master deal ではないのに電圧リファレンスだということは他にも融通に参加している ( そのどれかが master deal である ) はず
					// なのでデバイスは止められないはず
					// それなのにデバイスを止められる
					// そんなはずがないので GLOBAL ERROR
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "isMaster_ == false && masterSideUnitDDConMode_() == VR && canStopDcdc_() == true ; deal : " + deal_, completionHandler);
				} else {
					// 電圧リファレンスのまま放置
					if (log.isInfoEnabled()) log.info("no need to control voltage reference");
					// 電圧リファレンスユニットのデバイス制御状態のキャッシュを更新しておく
					// DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
					updateUnitDcdcStatus_(masterSideUnitId_(), res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler)); // update cache for master side unit ...
				}
			} else {
				// 電圧リファレンスではない ( CHARGE または DISCHARGE である ) 場合
				if (canStopDcdc_()) {
					// デバイスを止めてよい
					if (log.isInfoEnabled()) log.info("stop master side unit");
					// デバイスを止めちゃう
					// 成功した場合に DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
					controlMasterSideUnitDcdc_(DDCon.Mode.WAIT.name(), null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler));
				} else {
					// 止めないでグリッド電流値を変更する
					// 新しい電流値を算出し
					float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(masterSideUnitId_()));
					if (log.isInfoEnabled()) log.info("dig : " + JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "param", "dig") + " -> " + newDig);
					// 新しい電流値をパラメタに仕込んで
					JsonObject params = new JsonObject().put("gridCurrentA", newDig);
					// 電圧リファレンス側ユニットに電流値の変更を命令
					// 成功した場合に DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
					controlMasterSideUnitDcdc_("current", params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler));
				}
			}
		}
	}
	/**
	 * デバイスを止めてよいかどうか調べる.
	 * @return 止めてよければ {@code true}
	 */
	private boolean canStopDcdc_() {
		for (JsonObject aDeal : otherDeals_(masterSideUnitId_())) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				// 電圧リファレンス側ユニットが参加する他の融通が一つでも動いていたら止められない
				return false;
			}
		}
		return true;
	}

	/**
	 * master deal の目印を移動する.
	 * @param completionHandler the completion handler
	 */
	private void moveMasterDeal_(Handler<AsyncResult<Void>> completionHandler) {
		if (newMasterDeal_ != null) {
			if (masterSideWillBeFlipped_) {
				// 反転
				flipMasterSide_();
				if (log.isInfoEnabled()) log.info("master side was flipped : " + masterSide_);
			}
			DealUtil.isMaster(vertx_, newMasterDeal_, true, res -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, res, completionHandler));
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * 融通情報を deactivate 状態にする.
	 * @param completionHandler the completion handler
	 */
	private void deactivateDeal_(Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isDeactivated(deal_)) {
			DealUtil.deactivate(vertx_, deal_, referenceDateTimeString_(), resDeactivate -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resDeactivate, completionHandler));
		} else {
			// 止める方向なので不整合はスルーする
			if (log.isInfoEnabled()) log.info("already deactivated");
			completionHandler.handle(Future.succeededFuture());
		}
	}

}
