package jp.co.sony.csl.dcoes.apis.common;

/**
 * APIS プログラムのツール中で発生するエラーを扱うための例外.
 * ツールでエラーを発しても流れがわからなくなる.
 * 専用の例外にして戻し呼び出し元で処理する.
 * 具体的には
 * {@code jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil#log(Error.Category, Error.Extent, Error.Level, String)}
 * や
 * {@code jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil#logAndFail(Error.Category, Error.Extent, Error.Level, Throwable, Handler)}
 * から使われる.
 * @author OES Project
 */
// こっちは FailureException かな ...
public class ErrorException extends Exception {

	private static final long serialVersionUID = 1L;

	public final String unitId;
	public final Error.Category category;
	public final Error.Extent extent;
	public final Error.Level level;

	/**
	 * インスタンス作成.
	 * @param unitId エラー生成ユニット ID
	 * @param category エラーの category オブジェクト
	 * @param extent エラーの extent オブジェクト
	 * @param level エラーの level オブジェクト
	 * @param message エラーメッセージ
	 */
	private ErrorException(String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message) {
		super(message, null, true, false);
		this.unitId = unitId;
		this.category = category;
		this.extent = extent;
		this.level = level;
	}

	/**
	 * インスタンス作成.
	 * @param unitId エラー生成ユニット ID
	 * @param category エラーの category オブジェクト
	 * @param extent エラーの extent オブジェクト
	 * @param level エラーの level オブジェクト
	 * @param message エラーメッセージ
	 * @return errorexception オブジェクト
	 */
	public static ErrorException create(String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message) {
		return new ErrorException(unitId, category, extent, level, message);
	}

}
