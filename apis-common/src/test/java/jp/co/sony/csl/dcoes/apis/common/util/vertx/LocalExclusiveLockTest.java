package jp.co.sony.csl.dcoes.apis.common.util.vertx;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class LocalExclusiveLockTest {
	protected Vertx vertx;

	public LocalExclusiveLockTest() {
		super();
	}

	@Before public void before(TestContext context) {
		vertx = Vertx.vertx();
	}
	@After public void after(TestContext context) {
		vertx.close();
	}

	@Test public void releaseTwice(TestContext context) {
		LocalExclusiveLock exclusiveLock = new LocalExclusiveLock(LocalExclusiveLockTest.class.getName());
		Async async = context.async();
		exclusiveLock.acquire(vertx, res -> {
			context.assertTrue(res.succeeded());
			if (res.succeeded()) {
				LocalExclusiveLock.Lock lock = res.result();
				System.out.println("will release once...");
				lock.release();
				System.out.println("will release twice...");
				lock.release();
				async.complete();
			} else {
				context.fail(res.cause());
			}
		});
	}

	@Test public void flood(TestContext context) {
		int size = 10;
		Async async = context.async(size);
		List<Object> count = new ArrayList<>(size);
		LocalExclusiveLock exclusiveLock = new LocalExclusiveLock(LocalExclusiveLockTest.class.getName());
		for (int i = 0; i < size; i++) {
			System.out.println("i : " + i);
			exclusiveLock.acquire(vertx, res -> {
				context.assertTrue(res.succeeded());
				if (res.succeeded()) {
					LocalExclusiveLock.Lock result = res.result();
					System.out.println("lock acquired");
					vertx.setTimer(100L, v -> {
						result.release();
						System.out.println("lock released");
						count.add(Boolean.TRUE);
						System.out.println("count : " + count.size());
						if (size <= count.size()) {
							async.complete();
						}
					});
				} else {
					context.fail(res.cause());
				}
			});
		}
	}

	@Test public void reset(TestContext context) {
		int size = 10;
		Async async = context.async();
		List<Object> count = new ArrayList<>(size);
		LocalExclusiveLock exclusiveLock = new LocalExclusiveLock(LocalExclusiveLockTest.class.getName());
		for (int i = 0; i < size; i++) {
			System.out.println("i : " + i);
			exclusiveLock.acquire(vertx, res -> {
				if (res.succeeded()) {
					LocalExclusiveLock.Lock result = res.result();
					System.out.println("lock acquired");
					vertx.setTimer(100L, v -> {
						result.release();
						System.out.println("lock released");
						count.add(Boolean.TRUE);
						System.out.println("count : " + count.size());
						if (size <= count.size()) {
							async.complete();
						} else if (size / 2 <= count.size()) {
							System.out.println("doing reset...");
							exclusiveLock.reset(vertx);
						}
					});
				} else {
					System.out.println("lock failed : " + res.cause());
					count.add(Boolean.FALSE);
					System.out.println("count : " + count.size());
					if (size <= count.size()) {
						async.complete();
					}
				}
			});
		}
	}

	@Test public void block(TestContext context) {
		Async async = context.async();
		LocalExclusiveLock exclusiveLock = new LocalExclusiveLock(LocalExclusiveLockTest.class.getName());
		exclusiveLock.acquire(vertx, res -> {
			if (res.succeeded()) {
				LocalExclusiveLock.Lock result = res.result();
				System.out.println("lock acquired");
				vertx.setTimer(13000L, v -> {
					result.release();
					System.out.println("lock released");
					vertx.setTimer(5000L, vv -> {
						System.out.println("done");
						async.complete();
					});
				});
			} else {
				context.fail(res.cause());
			}
		});
	}

	@Test public void priority(TestContext context) {
		int size = 10;
		Async async = context.async(size);
		List<Object> count = new ArrayList<>(size);
		LocalExclusiveLock exclusiveLock = new LocalExclusiveLock(LocalExclusiveLockTest.class.getName());
		for (int i = 0; i < size; i++) {
			System.out.println("i : " + i);
			exclusiveLock.acquire(vertx, res -> {
				context.assertTrue(res.succeeded());
				if (res.succeeded()) {
					LocalExclusiveLock.Lock result = res.result();
					System.out.println("unprivileged lock acquired");
					vertx.setTimer(100L, v -> {
						result.release();
						System.out.println("unprivileged lock released");
						count.add(Boolean.TRUE);
						System.out.println("count : " + count.size());
						if (size <= count.size()) {
							async.complete();
						}
					});
				} else {
					context.fail(res.cause());
				}
			});
		}
		System.out.println("i : privileged");
		exclusiveLock.acquire(vertx, true, res -> {
			context.assertTrue(res.succeeded());
			if (res.succeeded()) {
				LocalExclusiveLock.Lock result = res.result();
				System.out.println("privileged lock acquired");
				vertx.setTimer(500L, v -> {
					result.release();
					System.out.println("privileged lock released");
				});
			} else {
				context.fail(res.cause());
			}
		});
	}

}
