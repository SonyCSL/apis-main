package jp.co.sony.csl.dcoes.apis.common.util.vertx;

/**
 * APIS プログラム共通の CONFIG アクセスツール.
 * 一部しかサポートしていない.
 * @author OES Project
 */
public class VertxConfig {

	private VertxConfig() { }

	/**
	 * CONFIG を保持する {@link JsonObjectWrapper} オブジェクト.
	 */
	public static final JsonObjectWrapper config = new JsonObjectWrapper();

	/**
	 * CONFIG からプログラム識別文字列を取得.
	 * {@code CONFIG.programId}.
	 * @return プログラム識別文字列
	 */
	public static String programId() {
		return config.getString("programId");
	}
	/**
	 * CONFIG からコミュニティ識別文字列を取得.
	 * {@code CONFIG.communityId}.
	 * @return コミュニティ識別文字列
	 */
	public static String communityId() {
		return config.getString("communityId");
	}
	/**
	 * CONFIG からクラスタ識別文字列を取得.
	 * {@code CONFIG.clusterId}.
	 * @return クラスタ識別文字列
	 */
	public static String clusterId() {
		return config.getString("clusterId");
	}
	/**
	 * CONFIG から EventBus メッセージ通信の SSL 化および Cluster Wide Map の暗号化設定の有効フラグを読み込む
	 * {@code CONFIG.security.enabled}.
	 * @return 有効フラグ
	 */
	public static boolean securityEnabled() {
		return config.getBoolean(Boolean.FALSE, "security", "enabled");
	}
	/**
	 * CONFIG から EventBus メッセージ通信の SSL 化および Cluster Wide Map の暗号化設定の秘密鍵ファイルのパスを読み込む
	 * {@code CONFIG.security.pemKeyFile}.
	 * @return 秘密鍵ファイルのパス
	 */
	public static String securityPemKeyFile() {
		return config.getString("security", "pemKeyFile");
	}
	/**
	 * CONFIG から EventBus メッセージ通信の SSL 化および Cluster Wide Map の暗号化設定の証明書ファイルのパスを読み込む
	 * {@code CONFIG.security.pemCertFile}.
	 * @return 証明書ファイルのパス
	 */
	public static String securityPemCertFile() {
		return config.getString("security", "pemCertFile");
	}

}
