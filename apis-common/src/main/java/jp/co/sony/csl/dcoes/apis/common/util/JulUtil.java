package jp.co.sony.csl.dcoes.apis.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * ログ出力まわりのツール.
 * @author OES Project
 */
public class JulUtil {

	private JulUtil() { }

	private static final Map<Handler, Level> originalLevelMap_ = new HashMap<>();
	/**
	 * {@link MulticastHandler} のログレベルを変更する.
	 * {@code value} が {@code null} なら起動時のレベルに戻す.
	 * @param value ログレベル
	 */
	public static void setRootMulticastHandlerLevel(Level value) {
		Logger root = LogManager.getLogManager().getLogger("");
		if (root != null) {
			for (Handler handler : root.getHandlers()) {
				if (handler instanceof MulticastHandler) {
					Level org = originalLevelMap_.get(handler);
					if (org == null) {
						org = handler.getLevel();
						originalLevelMap_.put(handler, org);
					}
					if (value != null) {
						handler.setLevel(value);
					} else {
						handler.setLevel(org);
					}
				}
			}
		}
	}
	/**
	 * UDP マルチキャストのログレベルを変更する.
	 * {@code value} が {@code null} なら起動時のレベルに戻す.
	 * @param value ログレベル
	 * @throws IllegalArgumentException {@link Level#parse(String)}
	 */
	public static void setRootMulticastHandlerLevel(String value) {
		setRootMulticastHandlerLevel((StringUtil.nullIfEmpty(value) != null) ? Level.parse(value) : (Level) null);
	}

	//// copied from java.util.logging.LogManager

	/**
	 * {@link LogManager#getProperty(String)} の中継.
	 * @param name property name
	 * @return property value
	 */
	public static String getProperty(String name) {
		return LogManager.getLogManager().getProperty(name);
	}

	/**
	 * {@link LogManager#getStringProperty(String, String)} からコピー.
	 * @param name property name
	 * @param defaultValue default value
	 * @return property value
	 */
	// Package private method to get a String property.
	// If the property is not defined we return the given
	// default value.
	public static String getStringProperty(String name, String defaultValue) {
		String val = getProperty(name);
		if (val == null) {
			return defaultValue;
		}
		return val.trim();
	}

	/**
	 * {@link LogManager#getIntProperty(String, int)} からコピー.
	 * @param name property name
	 * @param defaultValue default value
	 * @return property value
	 */
	// Package private method to get an integer property.
	// If the property is not defined or cannot be parsed
	// we return the given default value.
	public static int getIntProperty(String name, int defaultValue) {
		String val = getProperty(name);
		if (val == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(val.trim());
		} catch (Exception ex) {
			return defaultValue;
		}
	}

	/**
	 * {@link LogManager#getLevelProperty(String, Level)} からコピー.
	 * @param name property name
	 * @param defaultValue default value
	 * @return property value
	 */
	// Package private method to get a Level property.
	// If the property is not defined or cannot be parsed
	// we return the given default value.
	public static Level getLevelProperty(String name, Level defaultValue) {
		String val = getProperty(name);
		if (val == null) {
			return defaultValue;
		}
//		Level l = Level.findLevel(val.trim());
		Level l = Level.parse(val.trim());
		return l != null ? l : defaultValue;
	}

	/**
	 * {@link LogManager#getFilterProperty(String, Filter)} からコピー.
	 * @param name property name
	 * @param defaultValue default value
	 * @return property value
	 */
	// Package private method to get a filter property.
	// We return an instance of the class named by the "name"
	// property. If the property is not defined or has problems
	// we return the defaultValue.
	public static Filter getFilterProperty(String name, Filter defaultValue) {
		String val = getProperty(name);
		try {
			if (val != null) {
				Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(val);
				return (Filter) clz.getDeclaredConstructor().newInstance();
			}
		} catch (Exception ex) {
			// We got one of a variety of exceptions in creating the
			// class or creating an instance.
			// Drop through.
		}
		// We got an exception.  Return the defaultValue.
		return defaultValue;
	}

	/**
	 * {@link LogManager#getFormatterProperty(String, Formatter)} からコピー.
	 * @param name property name
	 * @param defaultValue default value
	 * @return property value
	 */
	// Package private method to get a formatter property.
	// We return an instance of the class named by the "name"
	// property. If the property is not defined or has problems
	// we return the defaultValue.
	public static Formatter getFormatterProperty(String name, Formatter defaultValue) {
		String val = getProperty(name);
		try {
			if (val != null) {
				Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(val);
				return (Formatter) clz.getDeclaredConstructor().newInstance();
			}
		} catch (Exception ex) {
			// We got one of a variety of exceptions in creating the
			// class or creating an instance.
			// Drop through.
		}
		// We got an exception.  Return the defaultValue.
		return defaultValue;
	}

}
