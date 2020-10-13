package jp.co.sony.csl.dcoes.apis.main.util;

import jp.co.sony.csl.dcoes.apis.common.util.vertx.ApisLoggerFormatter;

/**
 * apis-main 専用のログフォーマット.
 * プログラム識別文字列およびユニット ID が出力される.
 * @author OES Project
 */
public class ApisMainLoggerFormatter extends ApisLoggerFormatter {

	private String UNIT_ID_ = null;

	/**
	 * ログに出力するプログラム識別文字列を返す.
	 * プログラム識別文字列の代わりに「プログラム識別文字:ユニット ID」を生成する.
	 * @return プログラム識別文字列 + ':' + ユニット ID
	 */
	@Override protected String programId() {
		return super.programId() + ':' + unitId();
	}

	/**
	 * ログに出力するユニット ID を返す.
	 * {@code null} なら空文字列を返す.
	 * @return ユニット ID
	 */
	protected String unitId() {
		if (UNIT_ID_ == null) UNIT_ID_ = ApisConfig.unitId();
		return (UNIT_ID_ != null) ? UNIT_ID_ : "";
	}

}
