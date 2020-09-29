package jp.co.sony.csl.dcoes.apis.common.util.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * ファイルシステム便利機能.
 * @author OES Project
 */
public class FileSystemUtil {
	private static final Logger log = LoggerFactory.getLogger(FileSystemUtil.class);

	private FileSystemUtil() { }

	/**
	 * 指定されたパスでディレクトリ階層を作る.
	 * すでに存在していても成功を返す.
	 * @param vertx vertx インスタンス
	 * @param path パス
	 * @param completionHandler the completion handler
	 */
	public static void ensureDirectory(Vertx vertx, String path, Handler<AsyncResult<Void>> completionHandler) {
		vertx.fileSystem().exists(path, resExists -> {
			if (resExists.succeeded()) {
				if (resExists.result()) {
					// もうある → 成功
					completionHandler.handle(Future.succeededFuture());
				} else {
					// ない
					vertx.fileSystem().mkdirs(path, resMkdirs -> {
						if (resMkdirs.succeeded()) {
							// → 作れた → 成功
							completionHandler.handle(Future.succeededFuture());
						} else {
							// → 作れなかった → 確認してから作るまでにできた可能性があるので再確認
							vertx.fileSystem().exists(path, resExistsAgain -> {
								if (resExistsAgain.succeeded()) {
									if (resExistsAgain.result()) {
										// → あった → 成功
										completionHandler.handle(Future.succeededFuture());
									} else {
										// → やっぱりなかった → エラー
										log.error(resMkdirs.cause());
										completionHandler.handle(Future.failedFuture(resMkdirs.cause()));
									}
								} else {
									log.error(resExistsAgain.cause());
									completionHandler.handle(Future.failedFuture(resExistsAgain.cause()));
								}
							});
						}
					});
				}
			} else {
				log.error(resExists.cause());
				completionHandler.handle(Future.failedFuture(resExists.cause()));
			}
		});
	}

}
