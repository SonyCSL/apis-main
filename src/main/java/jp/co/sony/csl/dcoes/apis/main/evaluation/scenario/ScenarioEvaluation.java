package jp.co.sony.csl.dcoes.apis.main.evaluation.scenario;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.impl.SimpleScenarioEvaluationImpl;

/**
 * SCENARIO 評価の入り口.
 * 実装は {@link SimpleScenarioEvaluationImpl} のみ.
 * @author OES Project
 */
public class ScenarioEvaluation {

	private ScenarioEvaluation() { }

	/**
	 * 実装のためのインタフェイス.
	 * @author OES Project
	 */
	public interface Impl {
		/**
		 * SCENARIO と自ユニットの状態から融通リクエストを発するかどうか判断する.
		 * completionHandler の {@link AsyncResult#result()} で融通リクエストを受け取る.
		 * リクエストを発する状態でなければ {@code null} が返る.
		 * @param vertx vertx オブジェクト
		 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
		 * @param unitData 自ユニットのユニットデータ
		 * @param completionHandler the completion handler
		 */
		void checkStatus(Vertx vertx, JsonObject scenario, JsonObject unitData, Handler<AsyncResult<JsonObject>> completionHandler);
		/**
		 * 他ユニットから受信した融通リクエストに対し SCENARIO と自ユニットの状態からアクセプトを返すかどうか判断する.
		 * completionHandler の {@link AsyncResult#result()} で融通アクセプトを受け取る.
		 * アクセプトを返す状態でなければ {@code null} が返る.
		 * @param vertx vertx オブジェクト
		 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
		 * @param unitData 自ユニットのユニットデータ
		 * @param request 他ユニットから受信した融通リクエスト
		 * @param completionHandler the completion handler
		 */
		void treatRequest(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, Handler<AsyncResult<JsonObject>> completionHandler);
		/**
		 * 他ユニットが返したアクセプト群に対し SCENARIO と自ユニットの状態からアクセプトを一つ選択する.
		 * completionHandler の {@link AsyncResult#result()} で選択したアクセプトを受け取る.
		 * 融通を作成しようとする状態でなければ {@code null} が返る.
		 * @param vertx vertx オブジェクト
		 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
		 * @param unitData 自ユニットのユニットデータ
		 * @param request 自ユニットが発した融通リクエスト
		 * @param accepts 他ユニットから返ってきたアクセプト群
		 * @param completionHandler the completion handler
		 */
		void chooseAccept(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, List<JsonObject> accepts, Handler<AsyncResult<JsonObject>> completionHandler);
	}

	private static final Impl instance_ = new SimpleScenarioEvaluationImpl();

	/**
	 * 実装オブジェクトの {@link Impl#checkStatus(Vertx, JsonObject, JsonObject, Handler)} を呼ぶ.
	 * @param vertx vertx オブジェクト
	 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
	 * @param unitData 自ユニットのユニットデータ
	 * @param completionHandler the completion handler
	 */
	public static void checkStatus(Vertx vertx, JsonObject scenario, JsonObject unitData, Handler<AsyncResult<JsonObject>> completionHandler) {
		instance_.checkStatus(vertx, scenario, unitData, completionHandler);
	}
	/**
	 * 実装オブジェクトの {@link Impl#treatRequest(Vertx, JsonObject, JsonObject, JsonObject, Handler)} を呼ぶ.
	 * @param vertx vertx オブジェクト
	 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
	 * @param unitData 自ユニットのユニットデータ
	 * @param request 他ユニットから受信した融通リクエスト
	 * @param completionHandler the completion handler
	 */
	public static void treatRequest(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, Handler<AsyncResult<JsonObject>> completionHandler) {
		instance_.treatRequest(vertx, scenario, unitData, request, completionHandler);
	}
	/**
	 * 実装オブジェクトの {@link Impl#chooseAccept(Vertx, JsonObject, JsonObject, JsonObject, List, Handler)} を呼ぶ.
	 * @param vertx vertx オブジェクト
	 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
	 * @param unitData 自ユニットのユニットデータ
	 * @param request 自ユニットが発した融通リクエスト
	 * @param accepts 他ユニットから返ってきたアクセプト群
	 * @param completionHandler the completion handler
	 */
	public static void chooseAccept(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, List<JsonObject> accepts, Handler<AsyncResult<JsonObject>> completionHandler) {
		instance_.chooseAccept(vertx, scenario, unitData, request, accepts, completionHandler);
	}

}
