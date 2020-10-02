package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.StringUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.FileSystemUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * 融通情報をファイルシステムに記録する Verticle.
 * {@link Mediator} Verticle から起動される.
 * 自ユニットが参加する融通情報を定期的にファイルシステムに書き込む.
 * 外部からの要求があったときにも書き込む.
 * @author OES Project
 */
public class DealLogging extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DealLogging.class);

	/**
	 * 融通情報保存周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_DEAL_LOGGING_PERIOD_MSEC = 5000L;
	/**
	 * 融通情報保存パスのフォーマットのデフォルト値.
	 * 値は {@value}.
	 */
	private static final JsonObjectUtil.DefaultString DEFAULT_DEAL_LOG_DIR_FORMAT = new JsonObjectUtil.DefaultString("'" + StringUtil.TMPDIR + "/apis/dealLog/'uuuu'/'MM'/'dd");

	private long dealLoggingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 定期的に融通を記録するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startDealLoggingService_(resDealLogging -> {
			if (resDealLogging.succeeded()) {
				dealLoggingTimerHandler_(0L);
				if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
				startFuture.complete();
			} else {
				startFuture.fail(resDealLogging.cause());
			}
		});
	}

	/**
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#dealLogging()}
	 * 範囲 : グローバル
	 * 処理 : 融通を記録する.
	 * 　　   受け取った融通に自ユニットが参加していたらファイルシステムに保存する.
	 * 　　   受け取った融通に自ユニットが参加していなければスルー.
	 * メッセージボディ : 融通情報 [{@link JsonObject}]
	 * メッセージヘッダ : なし
	 * レスポンス : 受け取った融通に自ユニットが参加していたら自ユニットの ID [{@link String}].
	 * 　　　　　   受け取った融通に自ユニットが参加していなかったら {@code "N/A"}.
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDealLoggingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealLogging(), req -> {
			JsonObject deal = req.body();
			if (deal != null) {
				if (Deal.isInvolved(deal, ApisConfig.unitId()) && Deal.isSaveworthy(deal)) {
					// 自ユニットがその融通に参加しており記録しておくべき内部状態である場合にのみ記録する
					List<JsonObject> deals = new ArrayList<>(1);
					deals.add(deal);
					new DealLogging_(deals, resLogging -> {
						if (resLogging.succeeded()) {
							req.reply(ApisConfig.unitId());
						} else {
							req.fail(-1, resLogging.cause().getMessage());
						}
					}).doLoop_();
				} else {
					req.reply("N/A");
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "deal is null", req);
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * 融通保存タイマ設定.
	 * 待ち時間は {@code POLICY.mediator.dealLoggingPeriodMsec} ( デフォルト値 {@link #DEFAULT_DEAL_LOGGING_PERIOD_MSEC} ).
	 */
	private void setDealLoggingTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_DEAL_LOGGING_PERIOD_MSEC, "mediator", "dealLoggingPeriodMsec");
		setDealLoggingTimer_(delay);
	}
	/**
	 * 融通保存タイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setDealLoggingTimer_(long delay) {
		dealLoggingTimerId_ = vertx.setTimer(delay, this::dealLoggingTimerHandler_);
	}
	/**
	 * 融通保存タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void dealLoggingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != dealLoggingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", dealLoggingTimerId_ : " + dealLoggingTimerId_);
			return;
		}
		doDealLogging_(res -> {
			setDealLoggingTimer_();
		});
	}
	private void doDealLogging_(Handler<AsyncResult<Void>> completionHandler) {
		// 自ユニットが参加している融通を取得
		DealUtil.withUnitId(vertx, ApisConfig.unitId(), resWithUnitId -> {
			if (resWithUnitId.succeeded()) {
				List<JsonObject> deals = resWithUnitId.result();
				if (!deals.isEmpty()) {
					// 記録する必要がないものは除外する
					List<JsonObject> filtered = new ArrayList<>(deals.size());
					for (JsonObject aDeal : deals) {
						if (Deal.isSaveworthy(aDeal)) {
							filtered.add(aDeal);
						}
					}
					deals = filtered;
				}
				if (!deals.isEmpty()) {
					new DealLogging_(deals, completionHandler).doLoop_();
				} else {
					completionHandler.handle(Future.succeededFuture());
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resWithUnitId.cause(), completionHandler);
			}
		});
	}
	private static final DateTimeFormatter LOG_DIR_FORMATTER_;
	static {
		String s = VertxConfig.config.getString(DEFAULT_DEAL_LOG_DIR_FORMAT, "dealLogDirFormat");
		s = StringUtil.fixFilePath(s);
		LOG_DIR_FORMATTER_ = DateTimeFormatter.ofPattern(s);
	}
	/**
	 * 融通情報をファイルシステムに保存するクラス.
	 * @author OES Project
	 */
	private class DealLogging_ {
		private List<JsonObject> deals_;
		private Handler<AsyncResult<Void>> completionHandler_;
		private List<JsonObject> dealsForLoop_;
		/**
		 * インスタンス作成.
		 * @param deals DEAL オブジェクトのリスト
		 * @param completionHandler the completion handler
		 */
		private DealLogging_(List<JsonObject> deals, Handler<AsyncResult<Void>> completionHandler) {
			deals_ = deals;
			completionHandler_ = completionHandler;
			dealsForLoop_ = new ArrayList<>(deals_);
		}
		private void doLoop_() {
			if (dealsForLoop_.isEmpty()) {
				completionHandler_.handle(Future.succeededFuture());
			} else {
				JsonObject aDeal = dealsForLoop_.remove(0);
				writeToFile_(aDeal, resWriteToFile -> {
					doLoop_();
				});
			}
		}
		private void writeToFile_(JsonObject deal, Handler<AsyncResult<Void>> completionHandler) {
			LocalDateTime createDateTime = JsonObjectUtil.getLocalDateTime(deal, "createDateTime");
			String logDir = LOG_DIR_FORMATTER_.format(createDateTime);
			String filename = Deal.dealId(deal);
			String path = logDir + File.separatorChar + filename;
			if (log.isDebugEnabled()) log.debug("path : " + path);
			ensureFile_(logDir, filename, path, resEnsureFile -> {
				if (resEnsureFile.succeeded()) {
					vertx.fileSystem().writeFile(path, Buffer.buffer(deal.encode()), resWriteFile -> {
						if (resWriteFile.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resWriteFile.cause(), completionHandler);
						}
					});
				} else {
					completionHandler.handle(resEnsureFile);
				}
			});
		}
		private void ensureFile_(String logDir, String filename, String path, Handler<AsyncResult<Void>> completionHandler) {
			FileSystemUtil.ensureDirectory(vertx, logDir, resEnsureDir -> {
				if (resEnsureDir.succeeded()) {
					vertx.fileSystem().exists(path, resExists -> {
						if (resExists.succeeded()) {
							if (resExists.result()) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								vertx.fileSystem().createFile(path, resCreate -> {
									if (resCreate.succeeded()) {
										completionHandler.handle(Future.succeededFuture());
									} else {
										// 無いから作ろうとしたら失敗したけど...
										vertx.fileSystem().exists(path, resExistsAgain -> {
											if (resExistsAgain.succeeded()) {
												if (resExistsAgain.result()) {
													// 再確認してみたらあった
													// → 作ろうとしている隙に誰かが作ったに違いない
													// → たぶん OK
													completionHandler.handle(Future.succeededFuture());
												} else {
													// 再確認してみたけどなかった
													// → NG
													ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resCreate.cause(), completionHandler);
												}
											} else {
												ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resExistsAgain.cause(), completionHandler);
											}
										});
									}
								});
							}
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resExists.cause(), completionHandler);
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resEnsureDir.cause(), completionHandler);
				}
			});
		}
	}

}
