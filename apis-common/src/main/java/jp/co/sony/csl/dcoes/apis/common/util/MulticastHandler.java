package jp.co.sony.csl.dcoes.apis.common.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.XMLFormatter;

/**
 * ログを UDP マルチキャストで送信するハンドラ.
 * 全体的に {@link java.util.logging.SocketHandler} あたりを参考にした.
 * @author OES Project
 */
public class MulticastHandler extends Handler {

	private String groupAddress;
	private int port;
	private SocketAddress sendAddress;
	private DatagramSocket sock;

	/**
	 * インスタンス作成.
	 * @throws IOException {@link #connect_()}
	 * @throws IllegalStateException {@link #configure_()} 失敗
	 */
	public MulticastHandler() throws IOException {
//		sealed = false;
		try {
			configure_();
		} catch (Exception e) {
			System.err.println("MulticastHandler : failed to configure");
			throw new IllegalStateException(e);
		}
		try {
			connect_();
		} catch (IOException e) {
			System.err.println("MulticastHandler : failed to create socket");
			throw e;
		}
//		sealed = true;
	}

	/**
	 * インスタンス作成.
	 * @param groupAddress マルチキャストグループアドレス
	 * @param port ポート
	 * @throws IOException {@link #connect_()}
	 * @throws IllegalStateException {@link #configure_()} 失敗
	 */
	public MulticastHandler(String groupAddress, int port) throws IOException {
//		sealed = false;
		try {
			configure_();
		} catch (Exception e) {
			System.err.println("MulticastHandler : failed to configure");
			throw new IllegalStateException(e);
		}
		this.groupAddress = groupAddress;
		this.port = port;
		try {
			connect_();
		} catch (IOException e) {
			System.err.println("MulticastHandler : failed to create socket");
			throw e;
		}
//		sealed = true;
	}

	/**
	 * 初期化.
	 * プロパティから設定を読み込む.
	 * @throws IllegalArgumentException port と groupAddress の値がおかしい
	 */
	private void configure_() {
		String cname = getClass().getName();
		setLevel(JulUtil.getLevelProperty(cname + ".level", Level.ALL));
		setFilter(JulUtil.getFilterProperty(cname +".filter", null));
		setFormatter(JulUtil.getFormatterProperty(cname +".formatter", new XMLFormatter()));
		try {
			setEncoding(JulUtil.getStringProperty(cname +".encoding", null));
		} catch (UnsupportedEncodingException e) {
			try {
				setEncoding(null);
			} catch (UnsupportedEncodingException e2) {
				// doing a setEncoding with null should always work.
				// assert false;
			}
		}
		port = JulUtil.getIntProperty(cname + ".port", 0);
		groupAddress = JulUtil.getStringProperty(cname + ".groupAddress", null);
		if (port == 0) {
			throw new IllegalArgumentException("Bad port: " + port);
		}
		if (groupAddress == null) {
			throw new IllegalArgumentException("Null group address: " + groupAddress);
		}
	}
	/**
	 * 接続する.
	 * @throws IOException {@link DatagramSocket#DatagramSocket()}
	 */
	private synchronized void connect_() throws IOException {
		sendAddress = new InetSocketAddress(groupAddress, port);
		sock = new DatagramSocket();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public synchronized void publish(LogRecord record) {
		if (!isLoggable(record)) {
			return;
		}
		if (sock == null) {
			return;
		}
		String msg;
		try {
			msg = getFormatter().format(record);
		} catch (Exception e) {
			reportError(null, e, ErrorManager.FORMAT_FAILURE);
			return;
		}
		String encoding = getEncoding();
		byte[] data;
		try {
			if (encoding == null) {
				data = msg.getBytes();
			} else {
				data = msg.getBytes(encoding);
			}
		} catch (Exception e) {
			reportError(null, e, ErrorManager.FORMAT_FAILURE);
			return;
		}
		try {
			DatagramPacket packet = new DatagramPacket(data, data.length, sendAddress);
			sock.send(packet);
		} catch (Exception e) {
			reportError(null, e, ErrorManager.WRITE_FAILURE);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void flush() {
		// nop
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public synchronized void close() throws SecurityException {
		if (sock != null) {
			sock.close();
			sock = null;
		}
	}

}
