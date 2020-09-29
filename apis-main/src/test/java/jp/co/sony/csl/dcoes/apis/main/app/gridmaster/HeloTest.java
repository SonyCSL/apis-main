package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.main.test.AbstractApisTest;

import org.junit.Test;

/**
 * GridMaster Helo のテスト.
 * @author OES Project
 */
public class HeloTest extends AbstractApisTest {

	/**
	 *  deploy と undeploy が行える.
	 * @param context testcontext オブジェクト
	 */	
	@Test
	public void testDeployAndUnDeploy(TestContext context) {
		vertx.deployVerticle(Helo.class.getName(), context.asyncAssertSuccess(deploymentID -> {
			vertx.undeploy(deploymentID, context.asyncAssertSuccess());
		}));
	}

	/**
	 *  GridMasterに helo を送ると unitId が返ってくる. 
	 * @param context testcontext オブジェクト
	 */
	@Test
	public void testHelo(TestContext context) {
		// 返信されるunitIdを取得
		String unitId = config.getString("unitId");
		context.assertNotNull(unitId);

		vertx.deployVerticle(Helo.class.getName(), context.asyncAssertSuccess(s -> {
			Async async = context.async();
			vertx.eventBus().send(ServiceAddress.GridMaster.helo(), null, r -> {
				if (r.succeeded()) {
					context.assertNotNull(r.result().body());
					context.assertEquals(unitId, String.valueOf(r.result().body()), "返信された値が、期待されたunitIdでない");
					async.complete();	// テスト正常終了
				} else {
					context.fail("送信に失敗");
				}
			});
		}));
	}

}
