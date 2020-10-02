package jp.co.sony.csl.dcoes.apis.main.error.handling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * エラー対応の抽象クラス.
 * @author OES Project
 */
public abstract class AbstractErrorsHandling {
//	private static final Logger log = LoggerFactory.getLogger(AbstractErrorsHandling.class);

	protected Vertx vertx_;
	protected JsonObject policy_;
	protected JsonArray errors_;
	protected JsonArray logMessages_;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} あるいは {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling} でコピーしたものが渡される.
	 * @param errors 処理対象のエラーのリスト
	 */
	public AbstractErrorsHandling(Vertx vertx, JsonObject policy, JsonArray errors) {
		vertx_ = vertx;
		policy_ = policy;
		errors_ = errors;
		logMessages_ = logMessages_(errors_);
	}

	/**
	 * エラー対応.
	 * 実際の処理内容はサブクラスの {@link #doHandle(Handler)} で実装する.
	 * @param completionHandler the completion handler
	 */
	public void handle(Handler<AsyncResult<Void>> completionHandler) {
		doHandle(completionHandler);
	}

	/**
	 * エラー {@link JsonObject} のリストからログに出力するためのメッセージのリストを取得する.
	 * @param errors エラー {@link JsonObject} のリスト
	 * @return エラーに対応するログ出力メッセージのリスト
	 */
	private JsonArray logMessages_(JsonArray errors) {
		JsonArray result = new JsonArray();
		for (Object anError : errors) {
			if (anError instanceof JsonObject) {
				String aLogMessage = Error.logMessage((JsonObject) anError);
				// 重複を排除する
				if (!result.contains(aLogMessage)) {
					result.add(aLogMessage);
				}
			} else {
				ErrorUtil.report(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "error object is not an instance of JsonObject : " + anError);
			}
		}
		return result;
	}

	/**
	 * エラー対応の実装.
	 * @param completionHandler the completion handler
	 */
	protected abstract void doHandle(Handler<AsyncResult<Void>> completionHandler);

}
