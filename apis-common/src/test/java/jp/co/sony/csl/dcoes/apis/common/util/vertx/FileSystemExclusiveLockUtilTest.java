package jp.co.sony.csl.dcoes.apis.common.util.vertx;

import jp.co.sony.csl.dcoes.apis.common.util.vertx.FileSystemExclusiveLockUtil;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class FileSystemExclusiveLockUtilTest {
	protected Vertx vertx;

	public FileSystemExclusiveLockUtilTest() {
		super();
	}

	@Before public void before(TestContext context) {
		vertx = Vertx.vertx();
	}
	@After public void after(TestContext context) {
		vertx.close();
	}

	@Test public void once(TestContext context) {
		String lockName = "test.once";
		Async async = context.async();
		FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res -> {
			context.assertTrue(res.succeeded());
			context.assertTrue(res.result());
			FileSystemExclusiveLockUtil.unlock(vertx, lockName, false, res2 -> {
				context.assertTrue(res2.succeeded());
				context.assertTrue(res2.result());
				async.complete();
			});
		});
	}

	@Test public void twice(TestContext context) {
		String lockName = "test.twice";
		Async async = context.async();
		FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res -> {
			context.assertTrue(res.succeeded());
			context.assertTrue(res.result());
			FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res2 -> {
				context.assertTrue(res2.succeeded());
				context.assertFalse(res2.result());
				FileSystemExclusiveLockUtil.unlock(vertx, lockName, false, res3 -> {
					context.assertTrue(res3.succeeded());
					context.assertTrue(res3.result());
					FileSystemExclusiveLockUtil.unlock(vertx, lockName, false, res4 -> {
						context.assertTrue(res4.succeeded());
						context.assertFalse(res4.result());
						async.complete();
					});
				});
			});
		});
	}

	@Test public void twiceLoose(TestContext context) {
		String lockName = "test.twice.loose";
		Async async = context.async();
		FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res -> {
			context.assertTrue(res.succeeded());
			context.assertTrue(res.result());
			FileSystemExclusiveLockUtil.lock(vertx, lockName, true, res2 -> {
				context.assertTrue(res2.succeeded());
				context.assertTrue(res2.result());
				FileSystemExclusiveLockUtil.unlock(vertx, lockName, false, res3 -> {
					context.assertTrue(res3.succeeded());
					context.assertTrue(res3.result());
					FileSystemExclusiveLockUtil.unlock(vertx, lockName, true, res4 -> {
						context.assertTrue(res4.succeeded());
						context.assertTrue(res4.result());
						async.complete();
					});
				});
			});
		});
	}

	@Test public void check(TestContext context) {
		String lockName = "test.check";
		Async async = context.async();
		FileSystemExclusiveLockUtil.check(vertx, lockName, res -> {
			context.assertTrue(res.succeeded());
			context.assertFalse(res.result());
			FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res2 -> {
				context.assertTrue(res2.succeeded());
				context.assertTrue(res2.result());
				FileSystemExclusiveLockUtil.check(vertx, lockName, res3 -> {
					context.assertTrue(res3.succeeded());
					context.assertTrue(res3.result());
					FileSystemExclusiveLockUtil.unlock(vertx, lockName, false, res4 -> {
						context.assertTrue(res4.succeeded());
						context.assertTrue(res4.result());
						FileSystemExclusiveLockUtil.check(vertx, lockName, res5 -> {
							context.assertTrue(res5.succeeded());
							context.assertFalse(res5.result());
							async.complete();
						});
					});
				});
			});
		});
	}

	@Test public void reset(TestContext context) {
		String lockName = "test.reset";
		Async async = context.async();
		FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res -> {
			context.assertTrue(res.succeeded());
			context.assertTrue(res.result());
			FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res2 -> {
				context.assertTrue(res2.succeeded());
				context.assertFalse(res2.result());
				FileSystemExclusiveLockUtil.reset(vertx, res3 -> {
					context.assertTrue(res3.succeeded());
					FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res4 -> {
						context.assertTrue(res4.succeeded());
						context.assertTrue(res4.result());
						FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res5 -> {
							context.assertTrue(res5.succeeded());
							context.assertFalse(res5.result());
							async.complete();
						});
					});
				});
			});
		});
	}

//	@Test public void exception(TestContext context) {
//		String lockName = "test.exception";
//		Async async = context.async();
//		try {
//			FileSystemExclusiveLockUtil.lock(vertx, lockName, false, res -> {
//				System.out.println("res.succeeded() : " + res.succeeded());
//				System.out.println("res.result() : " + res.result());
//				throw new RuntimeException("hoge");
//				// この例外はここに見える try-catch ではなく FileSystemExclusiveLockUtil#lock() 中の completionHandler.handle(resAcquireLock); を囲む try-catch に捕まえられてしまう
//				// なのでこのラムダは何度でも無限に呼び出されてしまう
//				// そして二回目以降の res は failed である
//			});
//		} catch (Exception e) {
//			System.err.println("Exception caught : " + e);
//			async.complete();
//		}
//	}
//
//	@Test public void timer(TestContext context) {
//		Async async = context.async();
//		try {
//			long timerId = vertx.setTimer(1000L, id -> {
//				System.out.println("id : " + id);
//				throw new RuntimeException("piyo");
//			});
//			System.out.println("timerId : " + timerId);
//		} catch (Exception e) {
//			System.err.println("Exception caught : " + e);
//			async.complete();
//		}
//	}

}
