package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * エラー処理の抽象クラス.
 * @author OES Project
 */
public abstract class AbstractErrorAction {

	protected final Vertx vertx_;
	protected final JsonObject policy_;
	protected final JsonArray logMessages_;

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} あるいは {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling} でコピーしたものが渡される.
	 * @param logMessages エラー処理で記録するログメッセージのリスト
	 */
	public AbstractErrorAction(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		vertx_ = vertx;
		policy_ = policy;
		logMessages_ = logMessages;
	}

	/**
	 * エラー処理.
	 * 実際の処理内容はサブクラスの {@link #doAction(Handler)} で実装する.
	 * @param completionHandler the completion handler
	 */
	public void action(Handler<AsyncResult<Void>> completionHandler) {
		doAction(completionHandler);
	}

	/**
	 * エラー処理の実装.
	 * @param completionHandler the completion handler
	 */
	protected abstract void doAction(Handler<AsyncResult<Void>> completionHandler);

}
