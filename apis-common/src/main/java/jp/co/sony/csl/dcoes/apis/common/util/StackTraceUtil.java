package jp.co.sony.csl.dcoes.apis.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * スタックトレースまわりのどうってことないツール.
 * @author OES Project
 */
public class StackTraceUtil {

	private StackTraceUtil() { }

	/**
	 * 現在のスレッドのスタックトレースの最後の要素を取得する.
	 * @return 現在のスレッドのスタックトレースの最後の stacktraceelement オブジェクト
	 */
	public static StackTraceElement lastStackTrace() {
		return lastStackTrace(null);
	}
	/**
	 * 現在のスレッドのスタックトレースの最後の要素を取得する.
	 * {@code classes} でスルーするクラスを指定できる.
	 * スルー機能は例えばユーティリティオブジェクト中で呼び出す際にそのオブジェクト内の処理を含めたくない場合など.
	 * @param classes スルーするクラスの配列
	 * @return 現在のスレッドのスタックトレースの最後の stacktraceelement オブジェクト.
	 *         ただし {@code classes} で指定したクラス内の処理は除く.
	 */
	public static StackTraceElement lastStackTrace(Class<?>[] classes) {
		Map<String, Object> classNames = new HashMap<>();
		classNames.put(Thread.class.getName(), Boolean.TRUE);
		classNames.put(StackTraceUtil.class.getName(), Boolean.TRUE);
		if (classes != null) {
			for (Class<?> aClass : classes) {
				classNames.put(aClass.getName(), Boolean.TRUE);
			}
		}
		for (StackTraceElement aSte : Thread.currentThread().getStackTrace()) {
			// スルーするクラスをスルーするー
			if (classNames.containsKey(aSte.getClassName())) continue;
			return aSte;
		}
		return null;
	}

	/**
	 * 現在のスレッドのスタックトレースを取得する.
	 * @return 現在のスレッドのスタックトレース
	 */
	public static StackTraceElement[] stackTrace() {
		return stackTrace(null);
	}
	/**
	 * 現在のスレッドのスタックトレースを取得する.
	 * {@code classes} でスルーするクラスを指定できる.
	 * スルー機能は例えばユーティリティオブジェクト中で呼び出す際にそのオブジェクト内の処理を含めたくない場合など.
	 * @param classes スルーするクラスの配列
	 * @return 現在のスレッドのスタックトレース.
	 *         ただし {@code classes} で指定したクラス内の処理は除く.
	 */
	public static StackTraceElement[] stackTrace(Class<?>[] classes) {
		Map<String, Object> classNames = new HashMap<>();
		classNames.put(Thread.class.getName(), Boolean.TRUE);
		classNames.put(StackTraceUtil.class.getName(), Boolean.TRUE);
		if (classes != null) {
			for (Class<?> aClass : classes) {
				classNames.put(aClass.getName(), Boolean.TRUE);
			}
		}
		List<StackTraceElement> result = new ArrayList<>();
		for (StackTraceElement aSte : Thread.currentThread().getStackTrace()) {
			if (classNames.containsKey(aSte.getClassName())) continue;
			result.add(aSte);
		}
		return result.toArray(new StackTraceElement[result.size()]);
	}
}
