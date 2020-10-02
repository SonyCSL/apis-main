package jp.co.sony.csl.dcoes.apis.common;

/**
 * {@link io.vertx.core.eventbus.EventBus} アドレスの一元管理.
 * @author OES Project
 */
public class ServiceAddress {

	private ServiceAddress() { }

	/**
	 * クラスタ内に同一 ID を持つユニットが重複しないための仕組みで使用するアドレス.
	 * 範囲 : グローバル
	 * 処理 : クラスタ内に同一 ID を持つユニットが重複しないための仕組み.
	 * 　　   メッセージボディが空なら自ユニットのユニット ID を返す.
	 * 　　   メッセージボディがあり自分の {@link io.vertx.core.AbstractVerticle#deploymentID()} と一致したらスルー ( 自分なので ).
	 * 　　   メッセージボディがあり自分の {@link io.vertx.core.AbstractVerticle#deploymentID()} と一致しなかったら FATAL エラー ( 他に同じ ID のユニットがいるということなので ).
	 * メッセージボディ : 送信元 {@link jp.co.sony.csl.dcoes.apis.main.app.Helo} Verticle の {@link io.vertx.core.AbstractVerticle#deploymentID()} [{@link String}]
	 * メッセージヘッダ : なし
	 * レスポンス : メッセージボディが空なら自ユニットの ID [{@link String}].
	 * 　　　　　   それ以外は返さない.
	 * @param unitId ユニット ID
	 * @return アドレス文字列
	 */
	public static String helo(String unitId) {
		return "apis." + unitId + ".helo";
	}
	/**
	 * 自ユニットの POLICY を取得するアドレス.
	 * 範囲 : ローカル
	 * 処理 : 自ユニットの POLICY を取得する.
	 * 　　   定期的にリフレッシュされているキャッシュ内容を返す.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : POLICY 情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
	 * @return アドレス文字列
	 */
	public static String policy() {
		return "apis.policy";
	}
	/**
	 * グローバル融通モードを set/get するアドレス.
	 * 範囲 : グローバル
	 * 処理 : グローバル融通モードを set/get する.
	 * 　　   値は以下のいずれか.
	 * 　　   - "autonomous"
	 * 　　   - "heteronomous"
	 * 　　   - "stop"
	 * 　　   - "manual"
	 * メッセージボディ :
	 * 　　　　　　　　   set の場合 : 設定するグローバル融通モード [{@link String}]
	 * 　　　　　　　　   get の場合 : なし
	 * メッセージヘッダ : {@code "command"}
	 * 　　　　　　　　   - {@code "set"} : グローバル融通モードを変更する
	 * 　　　　　　　　   - {@code "get"} : グローバル融通モードを取得する
	 * レスポンス :
	 * 　　　　　   set の場合 : 自ユニットの ID [{@link String}]
	 * 　　　　　   get の場合 : 現在のグローバル融通モード [{@link String}]
	 * 　　　　　   エラーが起きたら fail.
	 * @return アドレス文字列
	 */
	public static String operationMode() {
		return "apis.operationMode";
	}
	/**
	 * エラーを pub/sub するアドレス.
	 * アドレス : {@link ServiceAddress#error()}
	 * 範囲 : グローバル
	 * 処理 : エラーを受信する.
	 * 　　   - GridMaster : グローバルエラーのみ保持する.
	 * 　　   - User : 自ユニットのローカルエラーのみ保持する.
	 * メッセージボディ : エラー情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
	 * メッセージヘッダ : なし
	 * レスポンス : なし
	 * @return アドレス文字列
	 */
	public static String error() {
		return "apis.error";
	}
	/**
	 * 自ユニットのリセットのためのアドレス.
	 * 範囲 : ローカル
	 * 処理 : 自ユニットをリセットする.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @return アドレス文字列
	 */
	public static String resetLocal() {
		return "apis.reset.local";
	}
	/**
	 * 全体リセットのためのアドレス.
	 * 範囲 : グローバル
	 * 処理 : 全ユニットおよびクラスタに参加する全プログラムをリセットする.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @return アドレス文字列
	 */
	public static String resetAll() {
		return "apis.reset.all";
	}
	/**
	 * 自ユニットのシャットダウンのためのアドレス.
	 * 範囲 : ローカル
	 * 処理 : シャットダウンする.
	 * 　　   実際の処理は {@link jp.co.sony.csl.dcoes.apis.common.util.vertx.AbstractStarter#shutdown_()}.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : {@code "ok"}
	 * @return アドレス文字列
	 */
	public static String shutdownLocal() {
		return "apis.shutdown.local";
	}
	/**
	 * 全体シャットダウンのためのアドレス.
	 * 範囲 : グローバル
	 * 処理 : シャットダウンする.
	 * 　　   実際の処理は {@link jp.co.sony.csl.dcoes.apis.common.util.vertx.AbstractStarter#shutdown_()}.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : {@code "ok"}
	 * @return アドレス文字列
	 */
	public static String shutdownAll() {
		return "apis.shutdown.all";
	}
	/**
	 * ID を指定した単体シャットダウンのためのアドレス.
	 * 範囲 : グローバル
	 * 処理 : シャットダウンする.
	 * 　　   実際の処理は {@link ServiceAddress#shutdownLocal()} を呼び出す.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : {@code "ok"}
	 * 　　　　　   エラーが起きたら fail.
	 * @param unitId 対象ユニットの ID
	 * @return アドレス文字列
	 */
	public static String shutdown(String unitId) {
		return "apis." + unitId + ".shutdown";
	}
	/**
	 * UDP マルチキャストログ出力のレベルを変更するためのアドレス.
	 * 範囲 : グローバル
	 * 処理 : UDP マルチキャストログ出力のレベルを変更する
	 * メッセージボディ : ログレベル [{@link String}]
	 * 　　　　　　　　   指定がなければ初期状態に戻す
	 * メッセージヘッダ : なし
	 * レスポンス : 成功したら {@code "ok"}
	 * 　　　　　   エラーが起きたら fail.
	 * @return アドレス文字列
	 */
	public static String multicastLogHandlerLevel() {
		return "apis.multicastLogHandlerLevel";
	}
	/**
	 * Controller サービスで使用する {@link io.vertx.core.eventbus.EventBus} アドレスの一元管理.
	 * @author OES Project
	 */
	public static class Controller {
		/**
		 * 自ユニットのユニットデータを取得するためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : 自ユニットのユニットデータを取得する.
		 * 　　   キャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : ユニットデータ [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String urgentUnitData() {
			return "apis.Controller.data.urgent";
		}
		/**
		 * 自ユニットのユニットデータを取得するためのアドレス.
		 * アドレス : {@link ServiceAddress.Controller#unitData()}
		 * 範囲 : ローカル
		 * 処理 : 自ユニットのユニットデータを取得する.
		 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
		 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
		 * メッセージボディ : なし
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
		 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
		 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
		 * レスポンス : ユニットデータ [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String unitData() {
			return "apis.Controller.data";
		}
		/**
		 * ID を指定したユニットデータ取得のためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : ID で指定したユニットのユニットデータを取得する.
		 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
		 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
		 * 　　   ヘッダに gridMasterUnitId 指定が必要であり GridMaster インタロック値と一致する必要がある.
		 * メッセージボディ : なし
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
		 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
		 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
		 * 　　　　　　　　   - {@code "gridMasterUnitId"} : GridMaster ユニット ID
		 * レスポンス : ユニットデータ [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @param unitId 対象ユニットの ID
		 * @return アドレス文字列
		 */
		public static String unitData(String unitId) {
			return "apis." + unitId + ".Controller.data";
		}
		/**
		 * GridMaster が全ユニットのユニットデータを収集するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 指定されたアドレスに対し自ユニットのユニットデータを送る.
		 * 　　   GridMaster のデータ収集処理で使用する.
		 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
		 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
		 * 　　   ヘッダに gridMasterUnitId 指定が必要であり GridMaster インタロック値と一致する必要がある.
		 * 　　   ヘッダにデータを送り返すアドレス replyAddress 指定が必要である.
		 * メッセージボディ : なし
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
		 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
		 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
		 * 　　　　　　　　   - {@code "gridMasterUnitId"} : GridMaster ユニット ID
		 * 　　　　　　　　   - {@code "replyAddress"} : データを送り返すアドレス
		 * レスポンス : なし
		 * @return アドレス文字列
		 */
		public static String unitDatas() {
			return "apis.Controller.data.all";
		}
		/**
		 * 自ユニットのデバイス制御状態を取得するためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : 自ユニットのデバイス制御状態を取得する.
		 * 　　   キャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : デバイス制御状態 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String urgentUnitDeviceStatus() {
			return "apis.Controller.device.status.urgent";
		}
		/**
		 * 自ユニットのデバイス制御状態を取得するためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : 自ユニットのデバイス制御状態を取得する.
		 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
		 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
		 * メッセージボディ : なし
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
		 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
		 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
		 * レスポンス : デバイス制御状態 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String unitDeviceStatus() {
			return "apis.Controller.device.status";
		}
		/**
		 * ID を指定したデバイス制御状態の取得のためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : ID で指定したユニットのデバイス制御状態を取得する.
		 * 　　   ヘッダに urgent 指定があればキャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
		 * 　　   そうでなければ定期的にリフレッシュしてあるキャッシュデータを返す.
		 * 　　   ヘッダに gridMasterUnitId 指定が必要であり GridMaster インタロック値と一致する必要がある.
		 * メッセージボディ : なし
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "urgent"} : 緊急フラグ
		 * 　　　　　　　　     - {@code "true"} : その場でデバイスに問合せフレッシュなデータを返す
		 * 　　　　　　　　     - {@code "false"} : 定期的にリフレッシュしてあるキャッシュデータを返す
		 * 　　　　　　　　   - {@code "gridMasterUnitId"} : GridMaster ユニット ID
		 * レスポンス : デバイス制御状態 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @param unitId 対象ユニットの ID
		 * @return アドレス文字列
		 */
		public static String unitDeviceStatus(String unitId) {
			return "apis." + unitId + ".Controller.device.status";
		}
		/**
		 * ID を指定したユニット制御のためのアドレス.
		 * アドレス : {@link ServiceAddress.Controller#deviceControlling(String)}
		 * 範囲 : グローバル
		 * 処理 : ID で指定したユニットのデバイスを制御する.
		 * 　　   制御の内容はメッセージボディで送る.
		 * 　　   ヘッダに gridMasterUnitId 指定が必要であり GridMaster インタロック値と一致する必要がある.
		 * 　　   具体的な処理は子クラスで実装する.
		 * メッセージボディ : コマンドおよびパラメタ [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "gridMasterUnitId"} : GridMaster ユニット ID
		 * レスポンス : デバイス制御状態 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @param unitId 対象ユニットの ID
		 * @return アドレス文字列
		 */
		public static String deviceControlling(String unitId) {
			return "apis." + unitId + ".Controller.control.device";
		}
		/**
		 * 自ユニットのデバイスを停止するためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : 自ユニットのデバイスを停止する.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : デバイス制御状態 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String stopLocal() {
			return "apis.Controller.stop.local";
		}
		/**
		 * 緊急停止のためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 全ユニットのデバイスを緊急停止する.
		 * 　　   緊急処理のため GridMaster インタロックは不要.
		 * メッセージボディ : なし
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "excludeVoltageReference"} : 電圧リファレンス除外フラグ
		 * 　　　　　　　　     - {@code "true"} : 自ユニットが電圧リファレンスならスルーする
		 * 　　　　　　　　     - {@code "false"} : 自ユニットが電圧リファレンスでも停止命令を送信する
		 * レスポンス : デバイス制御状態 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String scram() {
			return "apis.Controller.scram";
		}
		/**
		 * バッテリ容量管理機能 ( 新しい融通が可能か否かの確認 ) のためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : バッテリ容量と現在の融通状態を確認し新しい融通が可能か否か判定する.
		 * 　　   Gateway 運用など一つのバッテリを複数の apis-main で共有している場合に他の apis-main とバッテリ電流容量を協調管理する.
		 * 　　   現状は協調管理する apis-main が全て同一コンピュータ上にある必要がある ( ファイルシステムを用いて協調管理するため ).
		 * メッセージボディ : 融通方向 [{@link String}]
		 * 　　　　　　　　   - {@code "DISCHARGE"} : 送電
		 * 　　　　　　　　   - {@code "CHARGE"} : 受電
		 * メッセージヘッダ : なし
		 * レスポンス : 可またはバッテリ容量管理機能が無効なら {@link Boolean#TRUE}
		 * 　　　　　   不可なら {@link Boolean#FALSE}
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String batteryCapacityTesting() {
			return "apis.Controller.batteryCapacity.test";
		}
		/**
		 * バッテリ容量管理機能 ( 融通枠の確保と解放 ) のためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : 融通枠を確保/解放する.
		 * 　　   Gateway 運用など一つのバッテリを複数の apis-main で共有している場合に他の apis-main とバッテリ電流容量を協調管理する.
		 * 　　   現状は協調管理する apis-main が全て同一コンピュータ上にある必要がある ( ファイルシステムを用いて協調管理するため ).
		 * メッセージボディ : 融通情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * メッセージヘッダ : {@code "command"}
		 * 　　　　　　　　   - {@code "acquire"} : 融通枠を確保する
		 * 　　　　　　　　   - {@code "release"} : 融通枠を解放する
		 * レスポンス : 成功またはバッテリ容量管理機能が無効なら {@link Boolean#TRUE}
		 * 　　　　　   失敗なら {@link Boolean#FALSE}
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String batteryCapacityManaging() {
			return "apis.Controller.batteryCapacity.manage";
		}
	}
	/**
	 * User サービスで使用する {@link io.vertx.core.eventbus.EventBus} アドレスの一元管理.
	 * @author OES Project
	 */
	public static class User {
		/**
		 * 自ユニットの SCENARIO を取得するアドレス.
		 * APIS 標準フォーマット ( {@code uuuu/MM/dd-HH:mm:ss} ) で日時を指定する.
		 * 範囲 : ローカル
		 * 処理 : 自ユニットの SCENARIO を取得する.
		 * 　　   定期的にリフレッシュされているキャッシュから指定した時刻における設定を返す.
		 * メッセージボディ : 日時 [{@link String}]
		 * 　　　　　　　　   APIS 標準フォーマット ( uuuu/MM/dd-HH:mm:ss )
		 * メッセージヘッダ : なし
		 * レスポンス : 指定した時刻における SCENARIO サブセット [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * 　　　　　   見つからない場合は fail.
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String scenario() {
			return "apis.User.scenario";
		}
		/**
		 * ID を指定したローカル融通モードを set/get するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 自ユニットのローカル融通モードを set/get する.
		 * 　　   値は以下のいずれか.
		 * 　　   - {@code null}
		 * 　　   - "heteronomous"
		 * 　　   - "stop"
		 * メッセージボディ :
		 * 　　　　　　　　   set の場合 : 設定するローカル融通モード [{@link String}]
		 * 　　　　　　　　   get の場合 : なし
		 * メッセージヘッダ : {@code "command"}
		 * 　　　　　　　　   - {@code "set"} : ローカル融通モードを変更する
		 * 　　　　　　　　   - {@code "get"} : ローカル融通モードを取得する
		 * レスポンス :
		 * 　　　　　   set の場合 : 自ユニットの ID [{@link String}]
		 * 　　　　　   get の場合 : 現在のローカル融通モード [{@link String}]
		 * 　　　　　   エラーが起きたら fail.
		 * @param unitId 対象ユニットの ID
		 * @return アドレス文字列
		 */
		public static String operationMode(String unitId) {
			return "apis." + unitId + ".operationMode";
		}
		/**
		 * 他ユニットからのリクエストを処理するためのアドレス.
		 * Mediator サービスが中継するためこんな名前.
		 * 範囲 : ローカル
		 * 処理 : 他ユニットからのリクエストを処理する.
		 * メッセージボディ : リクエスト [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * メッセージヘッダ : なし
		 * レスポンス : リクエストに対するアクセプト情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * 　　　　　   アクセプトしなかった場合は {@code null}
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String mediatorRequest() {
			return "apis.User.mediatorRequest";
		}
		/**
		 * 自ユニットが発したリクエストに対するアクセプト群を処理するためのアドレス.
		 * Mediator サービスが中継するためこんな名前.
		 * 範囲 : ローカル
		 * 処理 : 自ユニットが発したリクエストに対するアクセプト群を処理する.
		 * メッセージボディ : リクエストおよびアクセプト群 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * 　　　　　　　　   - {@code "request"} : リクエスト情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * 　　　　　　　　   - {@code "accepts"} : アクセプト情報のリスト [{@link io.vertx.core.json.JsonArray JsonArray}]
		 * メッセージヘッダ : なし
		 * レスポンス : 選択結果のアクセプト情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * 　　　　　   アクセプトが選ばれなかった場合は {@code null}
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String mediatorAccepts() {
			return "apis.User.mediatorAccepts";
		}
		/**
		 * ID を指定したユニットのエラー有無を確認するためのアドレス.
		 * アドレス : {@link ServiceAddress.User#errorTesting(String)}
		 * 範囲 : グローバル
		 * 処理 : ローカルエラーの有無を確認する.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : ローカルエラーの有無 [{@link Boolean}]
		 * @param unitId 対象ユニットの ID
		 * @return アドレス文字列
		 */
		public static String errorTesting(String unitId) {
			return "apis." + unitId + ".User.error.test";
		}
	}
	/**
	 * Mediator サービスで使用する {@link io.vertx.core.eventbus.EventBus} アドレスの一元管理.
	 * @author OES Project
	 */
	public static class Mediator {
		/**
		 * User サービスが発したリクエストを処理するためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : 自ユニットからリクエストを受け取り融通交渉を実行する.
		 * メッセージボディ : リクエスト情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * メッセージヘッダ : なし
		 * レスポンス : deploymentID [{@link String}]
		 * @return アドレス文字列
		 */
		public static String internalRequest() {
			return "apis.Mediator.internalRequest";
		}
		/**
		 * 他ユニットからのリクエストを処理するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 他ユニットからリクエストを受け取り指定されたアドレスに対しアクセプトを送る.
		 * 　　   アクセプトしない場合は送らない.
		 * 　　   アクセプト作成は User サービスへ中継する.
		 * メッセージボディ : リクエスト情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "replyAddress"} : アクセプトを送り返すアドレス
		 * レスポンス : なし
		 * @return アドレス文字列
		 */
		public static String externalRequest() {
			return "apis.Mediator.externalRequest";
		}
		/**
		 * 融通情報リストを取得するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 融通情報リストを取得する.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : 共有メモリに管理されている融通情報リスト [{@link io.vertx.core.json.JsonArray JsonArray}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String deals() {
			return "apis.Mediator.deals";
		}
		/**
		 * 成立した融通を登録するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 融通情報を登録する.
		 * メッセージボディ : 融通情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * メッセージヘッダ : なし
		 * レスポンス : 作成された融通情報 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   失敗したら fail.
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String dealCreation() {
			return "apis.Mediator.deal.create";
		}
		/**
		 * 融通を削除するためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : 融通情報を削除する.
		 * メッセージボディ : 融通 ID [{@link String}]
		 * メッセージヘッダ : なし
		 * レスポンス : 削除された融通情報 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String dealDisposition() {
			return "apis.Mediator.deal.dispose";
		}
		/**
		 * 融通停止要求のためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 融通停止要求を送信する.
		 * メッセージボディ : 停止要求 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * 　　　　　　　　   - {@code "dealId"} : 融通 ID
		 * 　　　　　　　　   - {@code "reasons"} : 理由リスト [{@link io.vertx.core.json.JsonArray JsonArray}]
		 * メッセージヘッダ : なし
		 * レスポンス : 融通 ID [{@link String}]
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String dealNeedToStop() {
			return "apis.Mediator.deal.needToStop";
		}
		/**
		 * 融通を記録するためのアドレス.
		 * 範囲 : グローバル.
		 * 処理 : 融通を記録する.
		 * 　　   - apis-main : 受け取った融通に自ユニットが参加していたらファイルシステムに保存する.
		 * 　　                 受け取った融通に自ユニットが参加していなければスルー.
		 * 　　   - apis-ccc : Service Center に対し融通情報を通知する.
		 * メッセージボディ : 融通情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * メッセージヘッダ : なし
		 * レスポンス :
		 * 　　　　　   - apis-main : 受け取った融通に自ユニットが参加していたら自ユニットの ID [{@link String}].
		 * 　　　　　                 受け取った融通に自ユニットが参加していなかったら {@code "N/A"}.
		 * 　　　　　   - apis-ccc : 通知機能が有効なら {@code "ok"}.
		 * 　　　　　                通知機能が無効なら {@code "N/A"}.
		 * 　　　　　   - エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String dealLogging() {
			return "apis.Mediator.deal.log";
		}
		/**
		 * GridMaster インタロック機能のためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : GridMaster インタロックを確保/解放する.
		 * メッセージボディ : GridMaster ユニット ID [{@link String}]
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "command"}
		 * 　　　　　　　　     - {@code "acquire"} : インタロックを確保する
		 * 　　　　　　　　     - {@code "release"} : インタロックを解放する
		 * レスポンス : 自ユニットの ID [{@link String}]
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String gridMasterInterlocking() {
			return "apis.Mediator.interlock.gridMaster";
		}
		/**
		 * ID を指定した融通インタロック機能のためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 融通インタロックを確保/解放する.
		 * メッセージボディ : 融通情報 [{@link io.vertx.core.json.JsonObject JsonObject}]
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "command"}
		 * 　　　　　　　　     - {@code "acquire"} : インタロックを確保する
		 * 　　　　　　　　     - {@code "release"} : インタロックを解放する
		 * レスポンス : 自ユニットの ID [{@link String}]
		 * 　　　　　   エラーが起きたら fail.
		 * @param unitId 対象ユニットの ID
		 * @return アドレス文字列
		 */
		public static String dealInterlocking(String unitId) {
			return "apis." + unitId + ".Mediator.interlock.deal";
		}
		/**
		 * 適切なユニットに GridMaster を立てておく機能のためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : GridMaster の存在メンテナンス.
		 * 　　   存在の確認.
		 * 　　   存在場所の確認.
		 * 　　   適切なユニットへの移動.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : GridMaster が存在すユニットの ID [{@link String}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String gridMasterEnsuring() {
			return "apis.Mediator.gridMaster.ensure";
		}
		/**
		 * ID を指定した GridMaster 起動のためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : GridMaster を起動する.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : 自ユニットの ID [{@link String}].
		 * 　　　　　   エラーが起きたら fail.
		 * @param unitId 対象ユニットの ID
		 * @return アドレス文字列
		 */
		public static String gridMasterActivation(String unitId) {
			return "apis." + unitId + ".Mediator.gridMaster.activate";
		}
		/**
		 * ID を指定した GridMaster 停止のためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : GridMaster を停止する.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : 自ユニットの ID [{@link String}].
		 * 　　　　　   エラーが起きたら fail.
		 * @param unitId 対象ユニットの ID
		 * @return アドレス文字列
		 */
		public static String gridMasterDeactivation(String unitId) {
			return "apis." + unitId + ".Mediator.gridMaster.deactivate";
		}
	}
	/**
	 * GridMaster サービスで使用する {@link io.vertx.core.eventbus.EventBus} アドレスの一元管理.
	 * @author OES Project
	 */
	public static class GridMaster {
		/**
		 * クラスタ内に GridMaster が重複しないための仕組みで使用するアドレス.
		 * 範囲 : グローバル
		 * 処理 : GridMaster が重複しないための仕組み.
		 * 　　   メッセージボディが空なら自ユニットのユニット ID を返す ( GM ユニット ID の問合せなので ).
		 * 　　   メッセージボディがあり自分の {@link io.vertx.core.AbstractVerticle#deploymentID()} と一致したらスルー ( 自分なので ).
		 * 　　   メッセージボディがあり自分の {@link io.vertx.core.AbstractVerticle#deploymentID()} と一致しなかったらグローバルエラー ( 他に GM がいるということなので ).
		 * メッセージボディ : 送信元 Verticle の {@link io.vertx.core.AbstractVerticle#deploymentID()} [{@link String}]
		 * メッセージヘッダ : なし
		 * レスポンス : メッセージボディが空なら自ユニットの ID [{@link String}].
		 * 　　　　　   それ以外は返さない.
		 * @return アドレス文字列
		 */
		public static String helo() {
			return "apis.GridMaster.helo";
		}
		/**
		 * GridMaster が定期的にリフレッシュしてキャッシュしている全ユニットのユニットデータを取得するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 全ユニットのユニットデータを取得する.
		 * 　　   GridMaster が定期的にリフレッシュしているキャッシュ値を返す.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : 全ユニットのユニットデータ [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String unitDatas() {
			return "apis.GridMaster.data.all";
		}
		/**
		 * GridMaster が全ユニットのユニットデータを今すぐ収集するためのアドレス.
		 * アドレス : {@link ServiceAddress.GridMaster#urgentUnitDatas()}
		 * 範囲 : ローカル
		 * 処理 : 全ユニットのユニットデータを取得する.
		 * 　　   GridMaster が定期的にリフレッシュしているキャッシュ値ではなく全ユニットに問合せ収集した結果を返す.
		 * 　　   メッセージボディでタイムスタンプが送られその値より直近のデータ収集が新しい場合はデータ収集を実行せずキャッシュを返す.
		 * 　　   収集時ヘッダに urgent 指定をしないため各ユニットで定期的にリフレッシュしてあるキャッシュデータが集まる.
		 * メッセージボディ : これより新しければ収集不要タイムスタンプ [{@link Number}]
		 * メッセージヘッダ : なし
		 * レスポンス : 全ユニットのユニットデータ [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String urgentUnitDatas() {
			return "apis.GridMaster.data.all.urgent";
		}
		/**
		 * GridMaster から全ユニットの ID リストを取得するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : 全ユニットの ID リストを取得する.
		 * 　　   ID リストは POLICY から取得する.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : 全ユニットの ID リスト [{@link io.vertx.core.json.JsonArray JsonArray}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String unitIds() {
			return "apis.GridMaster.unitId.all";
		}
		/**
		 * グローバルエラーの有無を確認するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : グローバルエラーの有無を確認する.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : グローバルエラーの有無 [{@link Boolean}]
		 * @return アドレス文字列
		 */
		public static String errorTesting() {
			return "apis.GridMaster.error.test";
		}
		/**
		 * GridMaster を停止するためのアドレス.
		 * 範囲 : ローカル
		 * 処理 : GridMaster を停止する.
		 * メッセージボディ : なし
		 * メッセージヘッダ : なし
		 * レスポンス : 自ユニットの ID [{@link String}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String undeploymentLocal() {
			return "apis.GridMaster.undeploy.local";
		}
	}

	/**
	 * apis-ccc の ControlCenterClient サービスで使用する {@link io.vertx.core.eventbus.EventBus} アドレスの一元管理.
	 * @author OES Project
	 */
	public static class ControlCenterClient {
		/**
		 * Service Center から SCENARIO を取得するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : Service Center から SCENARIO を取得する.
		 * 　　   account, password, unitId が必要.
		 * メッセージボディ : なし
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "account"} : アカウント
		 * 　　　　　　　　   - {@code "password"} : パスワード
		 * 　　　　　　　　   - {@code "unitId"} : ユニット ID
		 * レスポンス : 取得した SCENARIO 情報 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String scenario() {
			return "apis.ControlCenterClient.scenario";
		}
		/**
		 * Service Center から POLICY を取得するためのアドレス.
		 * 範囲 : グローバル
		 * 処理 : Service Center から POLICY を取得する.
		 * 　　   account, password, unitId が必要.
		 * メッセージボディ : なし
		 * メッセージヘッダ :
		 * 　　　　　　　　   - {@code "account"} : アカウント
		 * 　　　　　　　　   - {@code "password"} : パスワード
		 * 　　　　　　　　   - {@code "unitId"} : ユニット ID
		 * レスポンス : 取得した POLICY 情報 [{@link io.vertx.core.json.JsonObject JsonObject}].
		 * 　　　　　   エラーが起きたら fail.
		 * @return アドレス文字列
		 */
		public static String policy() {
			return "apis.ControlCenterClient.policy";
		}
	}

}
