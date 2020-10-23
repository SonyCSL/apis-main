package jp.co.sony.csl.dcoes.apis.main.error.handling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.error.action.AskAndWaitForStopDeals;
import jp.co.sony.csl.dcoes.apis.main.error.action.DeactivateGridMaster;
import jp.co.sony.csl.dcoes.apis.main.error.action.ResetLocal;
import jp.co.sony.csl.dcoes.apis.main.error.action.StopLocal;

/**
 * エラー対応の実クラス.
 * - 範囲 : {@link jp.co.sony.csl.dcoes.apis.common.Error.Extent#LOCAL}
 * - 種類 : {@link jp.co.sony.csl.dcoes.apis.common.Error.Category#LOGIC}
 * - 深刻さ : {@link jp.co.sony.csl.dcoes.apis.common.Error.Level#ERROR}
 * - 処理内容 :
 *   1. 融通停止依頼
 *   2. デバイス停止
 *   3. GridMaster 停止
 *   4. リセット
 * @author OES Project
 */
public class LocalLogicErrorsHandling extends AbstractErrorsHandling {

	/**
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} あるいは {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling} でコピーしたものが渡される.
	 * @param errors 処理対象のエラーのリスト
	 */
	public LocalLogicErrorsHandling(Vertx vertx, JsonObject policy, JsonArray errors) {
		super(vertx, policy, errors);
	}

	/**
	 * {@inheritDoc}
	 * 1. 融通停止依頼
	 * 2. デバイス停止
	 * 3. GridMaster 停止
	 * 4. リセット
	 */
	@Override protected void doHandle(Handler<AsyncResult<Void>> completionHandler) {
		new AskAndWaitForStopDeals(vertx_, policy_, logMessages_).action(r -> {
			new StopLocal(vertx_, policy_, logMessages_).action(rr-> {
				new DeactivateGridMaster(vertx_, policy_, logMessages_).action(rrr -> {
					new ResetLocal(vertx_, policy_, logMessages_).action(completionHandler);
				});
			});
		});
	}

}
