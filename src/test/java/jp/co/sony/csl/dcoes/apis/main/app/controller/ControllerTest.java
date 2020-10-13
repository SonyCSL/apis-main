package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.ext.unit.TestContext;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;
import jp.co.sony.csl.dcoes.apis.main.test.AbstractApisTest;

import org.junit.Test;

/**
 * Controller のテスト.
 * @author OES Project
 */
public class ControllerTest extends AbstractApisTest {

	/**
	 *  deployとundeployが行える.
	 * @param context testcontext オブジェクト
	 */	
	@Test
	public void testDeployAndUnDeploy(TestContext context) {
		Factory.initialize(context.asyncAssertSuccess(v -> {
			vertx.deployVerticle(Controller.class.getName(), context.asyncAssertSuccess(deploymentID -> {
				vertx.undeploy(deploymentID, context.asyncAssertSuccess());
			}));
		}));
	}

}
