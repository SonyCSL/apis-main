package jp.co.sony.csl.dcoes.apis.common;

import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;

/**
 * 融通情報を保持する {@link JsonObject} から属性を参照するツール.
 * 融通 ID を生成する処理のみ更新する以外は参照のみ.
 * 更新は主に {@code jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil} で行う.
 * @author OES Project
 */
public class Deal {
	private static final Logger log = LoggerFactory.getLogger(Deal.class);

	/**
	 * 日時情報が無いことを示す日時文字列.
	 * 値は {@value}.
	 */
	public static final String NULL_DATE_TIME_VALUE = "--";

	private Deal() { }

	/**
	 * 融通の方向を示す.
	 * いまのところ {@code jp.co.sony.csl.dcoes.apis.main.app.controller.BatteryCapacityManagement} で使われているだけ.
	 * @author OES Project
	 */
	public enum Direction {
		/**
		 * 送電
		 */
		DISCHARGE,
		/**
		 * 受電
		 */
		CHARGE,
	}
	/**
	 * 融通の方向を示す文字列から {@link Direction} オブジェクトを取得する.
	 * @param value 方向を示す文字列
	 * @return 方向を示す direction オブジェクト
	 */
	public static Direction direction(String value) {
		try {
			return Direction.valueOf(value.toUpperCase());
		} catch (Exception e) {
			log.error(e);
			return null;
		}
	}

	/**
	 * 融通 ID を生成しセット.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 生成した ID 値
	 */
	public static String generateDealId(JsonObject deal) {
		String dealId = UUID.randomUUID().toString();
		deal.put("dealId", dealId);
		return dealId;
	}

	/**
	 * 融通 ID を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return ID 値
	 */
	public static String dealId(JsonObject deal) {
		return deal.getString("dealId");
	}

	/**
	 * 融通の種類を取得.
	 * 融通リクエスト作成側が要求した融通方向.
	 * {@code "charge"} または {@code "discharge"}.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通の種類を示す文字列
	 */
	public static String type(JsonObject deal) {
		return deal.getString("type");
	}

	/**
	 * リクエストユニットの ID を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return リクエストユニットの ID
	 */
	public static String requestUnitId(JsonObject deal) {
		return deal.getString("requestUnitId");
	}
	/**
	 * アクセプトユニットの ID を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return アクセプトユニットの ID
	 */
	public static String acceptUnitId(JsonObject deal) {
		return deal.getString("acceptUnitId");
	}

	/**
	 * 送電ユニットの ID を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 送電ユニットの ID
	 */
	public static String dischargeUnitId(JsonObject deal) {
		return deal.getString("dischargeUnitId");
	}
	/**
	 * 受電ユニットの ID を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 受電ユニットの ID
	 */
	public static String chargeUnitId(JsonObject deal) {
		return deal.getString("chargeUnitId");
	}

	/**
	 * 予定融通電力を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 予定融通電力
	 */
	public static Float dealAmountWh(JsonObject deal) {
		return deal.getFloat("dealAmountWh");
	}

	/**
	 * 送電側ユニットが最も効率が良くなるグリッド電圧を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 送電側ユニットが最も効率が良くなるグリッド電圧
	 */
	public static Float dischargeUnitEfficientGridVoltageV(JsonObject deal) {
		return deal.getFloat("dischargeUnitEfficientGridVoltageV");
	}
	/**
	 * 受電側ユニットが最も効率が良くなるグリッド電圧を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 受電側ユニットが最も効率が良くなるグリッド電圧
	 */
	public static Float chargeUnitEfficientGridVoltageV(JsonObject deal) {
		return deal.getFloat("chargeUnitEfficientGridVoltageV");
	}

	/**
	 * 融通時のグリッド電流を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通時の予定グリッド電流
	 */
	public static Float dealGridCurrentA(JsonObject deal) {
		return deal.getFloat("dealGridCurrentA");
	}
	/**
	 * 電流コンペンセイションのターゲット値を取得.
	 * 当該融通起動前における電圧リファレンスユニットのグリッド電流.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 電流コンペンセイションのターゲット値
	 */
	public static Float compensationTargetVoltageReferenceGridCurrentA(JsonObject deal) {
		return deal.getFloat("compensationTargetVoltageReferenceGridCurrentA");
	}
	/**
	 * 電流コンペンセイション終了後の送電ユニット側グリッド電流値を取得.
	 * 当該融通起動前における電圧リファレンスユニットのグリッド電流.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 電流コンペンセイション終了後の送電ユニット側グリッド電流値
	 */
	public static Float dischargeUnitCompensatedGridCurrentA(JsonObject deal) {
		return deal.getFloat("dischargeUnitCompensatedGridCurrentA");
	}
	/**
	 * 電流コンペンセイション終了後の受電ユニット側グリッド電流値を取得.
	 * 当該融通起動前における電圧リファレンスユニットのグリッド電流.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 電流コンペンセイション終了後の受電ユニット側グリッド電流値
	 */
	public static Float chargeUnitCompensatedGridCurrentA(JsonObject deal) {
		return deal.getFloat("chargeUnitCompensatedGridCurrentA");
	}

	/**
	 * 融通が登録された日時を取得.
	 * リクエスト → アクセプト → 成立 → "登録".
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通が登録された日時を表す文字列
	 */
	public static String createDateTime(JsonObject deal) {
		return deal.getString("createDateTime");
	}
	/**
	 * 融通が起動された日時を取得.
	 * 電圧リファレンス側ユニットを起動制御したタイミング.
	 * もしくはすでに稼働中で当該融通のため追加制御したタイミング.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通が起動された日時を表す文字列
	 */
	public static String activateDateTime(JsonObject deal) {
		return deal.getString("activateDateTime");
	}
	/**
	 * 電圧ランプアップが完了した日時を取得.
	 * 融通が無い状態から最初に起動した融通のみ持つ.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 電圧ランプアップが完了した日時を表す文字列
	 */
	public static String rampUpDateTime(JsonObject deal) {
		return deal.getString("rampUpDateTime");
	}
	/**
	 * 両側ユニットが起動完了した日時を取得.
	 * 電圧リファレンス側ではない側のユニットを起動制御したタイミング.
	 * もしくはすでに稼働中で当該融通のため追加制御したタイミング.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 両側ユニットが起動完了した日時を表す文字列
	 */
	public static String warmUpDateTime(JsonObject deal) {
		return deal.getString("warmUpDateTime");
	}
	/**
	 * 融通開始日時を取得.
	 * 電流コンペンセイションが終了したタイミング.
	 * 融通電力の積算計算はここから始まる.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通開始日時を表す文字列
	 */
	public static String startDateTime(JsonObject deal) {
		return deal.getString("startDateTime");
	}
	/**
	 * 融通終了日時を取得.
	 * 電圧リファレンス側ではない側のユニットを停止制御したタイミング.
	 * もしくは残りの融通のため追加制御したタイミング.
	 * 融通電力の積算計算はここまで.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通終了日時を表す文字列
	 */
	public static String stopDateTime(JsonObject deal) {
		return deal.getString("stopDateTime");
	}
	/**
	 * 両側ユニットが停止完了した日時を取得.
	 * 電圧リファレンス側ユニットを停止制御したタイミング.
	 * もしくは残りの融通のため追加制御したタイミング.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 両側ユニットが停止完了した日時を表す文字列
	 */
	public static String deactivateDateTime(JsonObject deal) {
		return deal.getString("deactivateDateTime");
	}
	/**
	 * 融通を異常終了した日時を取得.
	 * 正常に終了した融通は持たない.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通を異常終了した日時を表す文字列
	 */
	public static String abortDateTime(JsonObject deal) {
		return deal.getString("abortDateTime");
	}

	/**
	 * 融通をリセットした情報を取得.
	 * リセットが発生しなかった融通は持たない.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通をリセットした情報を表す jsonarray オブジェクト.
	 *         要素は以下の属性を持つ {@link JsonObject}.
	 *         - dateTime : リセット日時を表す文字列
	 *         - reason : 理由
	 */
	public static JsonArray resets(JsonObject deal) {
		return JsonObjectUtil.getJsonArray(deal, "reset");
	}
	/**
	 * リセット回数を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return リセット回数
	 */
	public static int numberOfResets(JsonObject deal) {
		JsonArray resets = resets(deal);
		return (resets != null) ? resets.size() : 0;
	}

	/**
	 * 融通を異常終了した情報を取得.
	 * 異常終了しなかった融通は持たない.
	 * 融通そのものの異常終了は一度しか起きないがエラー処理の都合上複数回処理する可能性があるため {@link JsonArray} で保持する.
	 * これとは別に以下の異常終了情報を持つ.
	 * - abortDateTime : 異常終了日時を表す文字列
	 * - abortReason : 理由
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通を異常終了した情報を表す jsonarray オブジェクト.
	 *         要素は以下の属性を持つ {@link JsonObject}.
	 *         - dateTime : 異常終了日時を表す文字列
	 *         - reason : 理由
	 */
	public static JsonArray aborts(JsonObject deal) {
		return JsonObjectUtil.getJsonArray(deal, "abort");
	}
	/**
	 * 異常終了回数を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 異常終了回数
	 */
	public static int numberOfAborts(JsonObject deal) {
		JsonArray aborts = aborts(deal);
		return (aborts != null) ? aborts.size() : 0;
	}

	/**
	 * 融通参加ユニットで起きたエラーにより融通停止要求があった場合の理由を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通停止要求の理由の jsonarray オブジェクト
	 */
	public static JsonArray needToStopReasons(JsonObject deal) {
		return JsonObjectUtil.getJsonArray(deal, "needToStopReasons");
	}

	/**
	 * {@code unitId} で指定するユニットが当該融通の送電側か.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @param unitId 問い合わせるユニット ID.
	 *        {@code null} 可.
	 * @return 送電ユニットなら {@code true}.
	 *         送電ユニットでなければ {@code false}.
	 *         {@code unitId} が {@code null} なら {@code false}.
	 */
	public static boolean isDischargeUnit(JsonObject deal, String unitId) {
		return (unitId != null && unitId.equals(dischargeUnitId(deal)));
	}
	/**
	 * {@code unitId} で指定するユニットが当該融通の受電側か.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @param unitId 問い合わせるユニット ID.
	 *        {@code null} 可.
	 * @return 受電ユニットなら {@code true}.
	 *         受電ユニットでなければ {@code false}.
	 *         {@code unitId} が {@code null} なら {@code false}.
	 */
	public static boolean isChargeUnit(JsonObject deal, String unitId) {
		return (unitId != null && unitId.equals(chargeUnitId(deal)));
	}
	/**
	 * {@code unitId} で指定するユニットが当該融通に参加しているか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @param unitId 問い合わせるユニット ID.
	 *        {@code null} 可.
	 * @return 送電ユニットまたは受電ユニットなら {@code true}.
	 *         いずれでもなければ {@code false}.
	 *         {@code unitId} が {@code null} なら {@code false}.
	 */
	public static boolean isInvolved(JsonObject deal, String unitId) {
		return (unitId != null && (unitId.equals(dischargeUnitId(deal)) || unitId.equals(chargeUnitId(deal))));
	}
	/**
	 * {@code unitId} で指定するユニットが送電受電どちら側か.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @param unitId 問い合わせるユニット ID.
	 *        {@code null} 可.
	 * @return 送電ユニットなら {@link Direction#DISCHARGE}.
	 *         受電ユニットなら {@link Direction#CHARGE}.
	 *         いずれでもなければ {@code null}.
	 *         {@code unitId} が {@code null} なら {@code null}.
	 */
	public static Direction direction(JsonObject deal, String unitId) {
		return (isDischargeUnit(deal, unitId)) ? Direction.DISCHARGE : (isChargeUnit(deal, unitId)) ? Direction.CHARGE : null;
	}

	/**
	 * 電圧リファレンス側のユニット ID を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @param masterSidePolicy 電圧リファレンス側を指定する POLICY 情報.
	 *                         基本は POLICY.gridMaster.voltageReferenceSide.
	 *                         大容量ユニットの設定によっては融通処理中に動的に反転することもある.
	 * @return {@code masterSidePolicy} が {@code "dischargeUnit"} なら送電側ユニット ID.
	 *         そうでなければ受電側ユニット ID.
	 */
	public static String masterSideUnitId(JsonObject deal, String masterSidePolicy) {
		return ("dischargeUnit".equals(masterSidePolicy)) ? dischargeUnitId(deal) : chargeUnitId(deal);
	}
	/**
	 * 電圧リファレンス側でない側のユニット ID を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @param masterSidePolicy 電圧リファレンス側を指定する POLICY 情報.
	 *                         基本は POLICY.gridMaster.voltageReferenceSide.
	 *                         大容量ユニットの設定によっては融通処理中に動的に反転することもある.
	 * @return {@code masterSidePolicy} が {@code "dischargeUnit"} なら受電側ユニット ID.
	 *         そうでなければ送電側ユニット ID.
	 */
	public static String slaveSideUnitId(JsonObject deal, String masterSidePolicy) {
		return ("dischargeUnit".equals(masterSidePolicy)) ? chargeUnitId(deal) : dischargeUnitId(deal);
	}

	/**
	 * {@code unitId} で指定するユニットが電圧リファレンス側のユニットか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @param unitId 問い合わせるユニット ID.
	 *        {@code null} 可.
	 * @param masterSidePolicy 電圧リファレンス側を指定する POLICY 情報.
	 *                         基本は POLICY.gridMaster.voltageReferenceSide.
	 *                         大容量ユニットの設定によっては融通処理中に動的に反転することもある.
	 * @return 電圧リファレンス側なら {@code true}.
	 *         そうでなければ {@code false}.
	 *         {@code unitId} が {@code null} なら {@code false}.
	 */
	public static boolean isMasterSideUnit(JsonObject deal, String unitId, String masterSidePolicy) {
		return (unitId != null && unitId.equals(masterSideUnitId(deal, masterSidePolicy)));
	}
	/**
	 * {@code unitId} で指定するユニットが電圧リファレンス側でない側のユニットか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @param unitId 問い合わせるユニット ID.
	 *        {@code null} 可.
	 * @param masterSidePolicy 電圧リファレンス側を指定する POLICY 情報.
	 *                         基本は POLICY.gridMaster.voltageReferenceSide.
	 *                         大容量ユニットの設定によっては融通処理中に動的に反転することもある.
	 * @return 電圧リファレンス側でない側なら {@code true}.
	 *         そうでなければ {@code false}.
	 *         {@code unitId} が {@code null} なら {@code false}.
	 */
	public static boolean isSlaveSideUnit(JsonObject deal, String unitId, String masterSidePolicy) {
		return (unitId != null && unitId.equals(slaveSideUnitId(deal, masterSidePolicy)));
	}

	/**
	 * {@code unitId} で指定するユニットの電流コンペンセイション終了後のグリッド電流値を取得.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @param unitId 問い合わせるユニット ID.
	 *        {@code null} 可.
	 * @return {@code unitId} で指定するユニットの電流コンペンセイション終了後のグリッド電流値.
	 *         {@code unitId} が参加していなければ {@code null}.
	 *         {@code unitId} が {@code null} なら {@code null}.
	 */
	public static Float compensatedGridCurrentA(JsonObject deal, String unitId) {
		if (isDischargeUnit(deal, unitId)) return dischargeUnitCompensatedGridCurrentA(deal);
		if (isChargeUnit(deal, unitId)) return chargeUnitCompensatedGridCurrentA(deal);
		return null;
	}

	/**
	 * 当該融通が起動済みか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 起動済みなら {@code true}.
	 */
	public static boolean isActivated(JsonObject deal) {
		return (activateDateTime(deal) != null);
	}
	/**
	 * 当該融通が電圧ランプアップ済みか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 電圧ランプアップ済みなら {@code true}.
	 */
	public static boolean isRampedUp(JsonObject deal) {
		return (rampUpDateTime(deal) != null);
	}
	/**
	 * 当該融通が両側ユニット起動済みか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 両側ユニット起動済みなら {@code true}.
	 */
	public static boolean isWarmedUp(JsonObject deal) {
		return (warmUpDateTime(deal) != null);
	}
	/**
	 * 当該融通が融通開始済みか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通開始済みなら {@code true}.
	 */
	public static boolean isStarted(JsonObject deal) {
		return (startDateTime(deal) != null);
	}
	/**
	 * 当該融通が融通終了済みか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通終了済みなら {@code true}.
	 */
	public static boolean isStopped(JsonObject deal) {
		return (stopDateTime(deal) != null);
	}
	/**
	 * 当該融通が両側ユニット停止済みか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 両側ユニット停止済みなら {@code true}.
	 */
	public static boolean isDeactivated(JsonObject deal) {
		return (deactivateDateTime(deal) != null);
	}
	/**
	 * 当該融通が異常終了済みか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 異常終了済みなら {@code true}.
	 */
	public static boolean isAborted(JsonObject deal) {
		return (abortDateTime(deal) != null);
	}

	/**
	 * 当該融通の電圧リファレンス側ユニットが稼働中か.
	 * 起動済みかつ両側ユニット停止済みではない.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 電圧リファレンス側ユニットが稼働中なら {@code true}.
	 */
	public static boolean masterSideUnitMustBeActive(JsonObject deal) {
		return (isActivated(deal) && !isDeactivated(deal));
	}
	/**
	 * 当該融通の電圧リファレンス側でない側ユニットが稼働中か.
	 * 両側ユニット起動済みかつ融通終了済みではない.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 電圧リファレンス側でない側ユニットが稼働中なら {@code true}.
	 */
	public static boolean slaveSideUnitMustBeActive(JsonObject deal) {
		return (isWarmedUp(deal) && !isStopped(deal));
	}

	/**
	 * 当該融通の両側ユニットが稼働中か.
	 * {@link #slaveSideUnitMustBeActive(JsonObject) 当該融通の電圧リファレンス側でない側ユニットが稼働中か} と同じとする.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 両側ユニットが稼働中なら {@code true}.
	 */
	public static boolean bothSideUnitsMustBeActive(JsonObject deal) {
		return slaveSideUnitMustBeActive(deal);
	}
	/**
	 * 当該融通の両側ユニットが停止中か.
	 * {@link #masterSideUnitMustBeActive(JsonObject) 当該融通の電圧リファレンス側ユニットが稼働中か} の反転とする.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 両側ユニットが停止中なら {@code true}.
	 */
	public static boolean bothSideUnitsMustBeInactive(JsonObject deal) {
		return !masterSideUnitMustBeActive(deal);
	}
	/**
	 * 当該融通の制御状態が過渡期か.
	 * {@link #bothSideUnitsMustBeActive(JsonObject) 当該融通の両側ユニットが稼働中} ではなく {@link #bothSideUnitsMustBeInactive(JsonObject) 当該融通の両側ユニットが停止中} でもない状態とする.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 両側ユニットが過渡期なら {@code true}.
	 */
	public static boolean isTransitionalState(JsonObject deal) {
		return (!bothSideUnitsMustBeActive(deal) && !bothSideUnitsMustBeInactive(deal));
	}

	/**
	 * 融通参加ユニットで起きたエラーにより融通停止要求があるか.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 融通停止要求があるなら {@code true}.
	 */
	public static boolean isNeedToStop(JsonObject deal) {
		return (needToStopReasons(deal) != null);
	}

	/**
	 * 当該融通が Master Deal であるか否か.
	 * Master Deal とは電圧リファレンスユニットが含まれる融通 ( のうちの一つ ) であり融通制御に重要な要素.
	 * → まず最初は一番に始めた融通を Master Deal とする.
	 * → 電圧リファレンスは Master Deal の voltageReferenceSide に立てる.
	 * → Master Deal が終わるときには残る融通の中から次の Master Deal を決める.
	 * → 電圧リファレンスは Master Deal につられて移動する.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return Master Deal なら {@code true}.
	 */
	public static boolean isMaster(JsonObject deal) {
		return deal.getBoolean("isMaster", Boolean.FALSE);
	}

	/**
	 * 当該融通が記録しておくべき融通であるか否か.
	 * 成立した融通の中には開始条件にそぐわず実行されずに捨てられるものもある.
	 * Service Center への通知やファイルシステムへの保存の際にはそれらの融通を含める必要はないと考える.
	 * 一度でも起動制御された融通のみ記録しておくべきとする.
	 * @param deal 対象の融通情報 jsonobject オブジェクト
	 * @return 記録するべき融通なら {@code true}.
	 */
	public static boolean isSaveworthy(JsonObject deal) {
		if (activateDateTime(deal) != null && !NULL_DATE_TIME_VALUE.equals(activateDateTime(deal))) return true;
		if (deal.getValue("reset") != null) return true;
		return false;
	}

}
