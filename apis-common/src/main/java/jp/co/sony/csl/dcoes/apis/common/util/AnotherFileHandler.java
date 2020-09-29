package jp.co.sony.csl.dcoes.apis.common.util;

import java.io.IOException;
import java.util.logging.FileHandler;

/**
 * ログのハンドラ.
 * ファイルへの出力を 2 系統にする ( *.log および *.err ) ための名ばかりのサブクラス.
 * ( 他に実現方法があるならそっちが良い ).
 * @author OES Project
 */
public class AnotherFileHandler extends FileHandler {

	/**
	 * インスタンス作成.
	 * @throws SecurityException {@inheritDoc}
	 * @throws IOException {@inheritDoc}
	 */
	public AnotherFileHandler() throws SecurityException, IOException {
		super();
	}

}
