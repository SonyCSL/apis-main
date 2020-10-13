package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.json.JsonObject;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;

/**
 * POLICY アクセスツール.
 * 一部しかサポートしていない.
 * @author OES Project
 */
public class Policy {

	private Policy() { }

	/**
	 * 融通ペアのどちら側で電圧リファレンスを立てるかを取得する.
	 * POLICY.gridMaster.voltageReferenceSide で定義したデフォルト値を返す.
	 * @param policy POLICY オブジェクト
	 * @return dischargeUnit : 送電側
	 *         chargeUnit : 受電側. デフォルト
	 * @see jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution#masterSide(io.vertx.core.Vertx, JsonObject, List)
	 */
	public static String masterSide(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "voltageReferenceSide");
		if (!"dischargeUnit".equals(result) && !"chargeUnit".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "gridMaster.voltageReferenceSide '" + result + "' not supported, use 'chargeUnit'");
			result = "chargeUnit";
		}
		return result;
	}

	/**
	 * 融通ペアのどちら側を制御上の基準とするかを取得する.
	 * POLICY.gridMaster.deal.referenceSide で定義した値を返す.
	 * @param policy POLICY オブジェクト
	 * @return dischargeUnit : 送電側
	 *         chargeUnit : 受電側. デフォルト
	 */
	public static String dealReferenceSide(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "deal", "referenceSide");
		if (!"chargeUnit".equals(result) && !"dischargeUnit".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "gridMaster.deal.referenceSide '" + result + "' not supported, use 'chargeUnit'");
			result = "chargeUnit";
		}
		return result;
	}

	/**
	 * GridMaster をどこに立てるかの設定を取得する.
	 * POLICY.gridMaster.gridMasterSelection.strategy で定義した値を返す.
	 * @param policy POLICY オブジェクト
	 * @return anywhere : どこでもよい. 最初に不在に気付いたユニットが自ユニットに立てる
	 *         fixed : POLICY.gridMaster.gridMasterSelection.fixedUnitId で指定したユニットに立てる.
	 *                 POLICY.gridMaster.gridMasterSelection.fixedUnitId の指定がなければデフォルト値に強制変更される.
	 *         voltageReferenceUnit : 電圧リファレンスをしているユニットに立てる.
	 *                                電圧リファレンスがいなければ anywhere と同じ.
	 *                                デフォルト
	 */
	public static String gridMasterSelectionStrategy(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "gridMasterSelection", "strategy");
		if (!"anywhere".equals(result) && !"fixed".equals(result) && !"voltageReferenceUnit".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "gridMaster.gridMasterSelection.strategy '" + result + "' not supported, use 'voltageReferenceUnit'");
			result = "voltageReferenceUnit";
		}
		if ("fixed".equals(result) && gridMasterSelectionFixedUnitId(policy) == null) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "gridMaster.gridMasterSelection.strategy is '" + result + "' but no fixedUnitId specified, use 'voltageReferenceUnit'");
			result = "voltageReferenceUnit";
		}
		return result;
	}
	/**
	 * {@link #gridMasterSelectionStrategy(JsonObject)} が fixed の場合のユニット ID 指定値を取得する.
	 * POLICY.gridMaster.gridMasterSelection.fixedUnitId で定義した値を返す.
	 * @param policy POLICY オブジェクト
	 * @return {@link #gridMasterSelectionStrategy(JsonObject)} が fixed の場合のユニット ID 指定値
	 */
	public static String gridMasterSelectionFixedUnitId(JsonObject policy) {
		return JsonObjectUtil.getString(policy, "gridMaster", "gridMasterSelection", "fixedUnitId");
	}

	/**
	 * Master Deal 選定方針を取得する.
	 * POLICY.gridMaster.masterDealSelection.strategy で定義した値を返す.
	 * @param policy POLICY オブジェクト
	 * @return newestDeal : DEAL.activateDateTime が新しいものを選ぶ. デフォルト
	 */
	public static String masterDealSelectionStrategy(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "masterDealSelection", "strategy");
		if (!"newestDeal".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "gridMaster.masterDealSelection.strategy '" + result + "' not supported, use 'newestDeal'");
			result = "newestDeal";
		}
		return result;
	}

	/**
	 * 大容量ユニットの ID のリストを取得する.
	 * POLICY.largeCapacityUnitIds で定義した値を返す.
	 * @param policy POLICY オブジェクト
	 * @return 大容量ユニットの ID のリスト
	 */
	public static List<String> largeCapacityUnitIds(JsonObject policy) {
		return JsonObjectUtil.getStringList(policy, "largeCapacityUnitIds");
	}

	/**
	 * グリッド電流最適化機能を実行するか否かのフラグを取得する.
	 * POLICY.gridMaster.gridVoltageOptimization.enabled で定義した値を返す.
	 * @param policy POLICY オブジェクト
	 * @return グリッド電流最適化機能を実行するか否かのフラグ
	 */
	public static Boolean gridVoltageOptimizationEnabled(JsonObject policy) {
		return JsonObjectUtil.getBoolean(policy, Boolean.FALSE, "gridMaster", "gridVoltageOptimization", "enabled");
	}

	/**
	 * 電圧リファレンス移動時の移動先でのグリッド電圧設定値の方針を取得する.
	 * @param policy POLICY オブジェクト
	 * @return "theoretical" : 移動元ユニットでの設定値 ( dvg )
	 *         "actual" : 移動先ユニットでの測定値 ( vg )
	 */
	public static String voltageReferenceTakeOverDvg(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "voltageReferenceTakeOverDvg");
		if (!"theoretical".equals(result) && !"actual".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "gridMaster.voltageReferenceTakeOverDvg '" + result + "' not supported, use 'theoretical'");
			result = "theoretical";
		}
		return result;
	}

}
