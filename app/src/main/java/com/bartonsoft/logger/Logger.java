package com.bartonsoft.logger;

import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

public class Logger {
	private static final Object lock = new Object();
	private static final StringBuilder headersb = new StringBuilder();
	private static final Formatter header = new Formatter(headersb, Locale.US);
	private static final Pattern lineEnd = Pattern.compile("\\n\\r*");
	private static final Pattern nonPrintable = Pattern.compile("[^\\x20-\\x7f\n\r\t\\p{InMiscellaneousSymbolsAndPictographs}\\p{InEmoticons}]");
	private static final HashMap<Class<?>, String> classNames = new HashMap<Class<?>, String>(256);

	private static final String TAG = "TESTLog";

	public static final boolean IS_DEBUG_ENABLED = true;
	public static final boolean IS_WARNING_ENABLED = true;
	public static final boolean IS_INFO_ENABLED = true;
	public static final boolean IS_ERROR_ENABLED = true;
	private static final boolean CHECK_LOCK = false;

	public static void debug(Object... objs) {
		if (IS_DEBUG_ENABLED) {
			log(Level.INFO, getCallerFrame(), objs);
		}
	}

	public static void info(Object... objs) {
		if (IS_INFO_ENABLED) {
			log(Level.INFO, getCallerFrame(), objs);
		}
	}

	public static void warn(Object... objs) {
		if (IS_WARNING_ENABLED) {
			log(Level.FINE, getCallerFrame(), objs);
		}
	}

	public static void error(Object... objs) {
		final StackTraceElement frame = getCallerFrame();
		log(Level.INFO, frame, objs);
		logError(false, frame, objs);
	}

	public static void error(Boolean x, Object... objs) {
		final StackTraceElement frame = getCallerFrame();
		log(Level.INFO, frame, objs);
		logError(false, frame, objs);
	}

	public static void fatal(Object... objs) {
		final StackTraceElement frame = getCallerFrame();
		log(Level.INFO, frame, objs);
		logError(true, frame, objs);
	}

	private static void log(Level level, StackTraceElement caller, Object... objs) {
		final int pid = Process.myPid();
		final int tid = Process.myTid();
		if (objs != null) {
			synchronized (lock) {
				final String header = getHeader(level, tid);
				final String message = formatMessage(caller, objs);
				if (message.length() > 2000) {
					// split into lines to ensure it is all logged
					boolean first = true;
					for (String line : message.split("\n")) {
						if (first) {
							first = false;
							line = header + line;
						}
						logLine(level, line);
					}
				}
				else {
					logLine(level, header + message);
				}
			}
		}
	}

	private static void logLine(Level level, String line) {
		if (level == Level.FINER) {
			Log.d(TAG, line);
		}
		else if (level == Level.SEVERE) {
			Log.e(TAG, line);
		}
		else if (level == Level.INFO) {
			Log.i(TAG, line);
		}
		else if (level == Level.FINE) {
			Log.w(TAG, line);
		}
	}

	public static String getThreadStacks(String tag, Thread thread) {
		final StringBuilder buf = new StringBuilder();
		if (tag != null) {
			buf.append("Thread stacks on " + tag + ":\n");
		}
		try {
			if (thread != null) {
				getStack(buf, thread, thread.getStackTrace());
			}
			else {
				final Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
				for (Thread t : stacks.keySet()) {
					getStack(buf, t, stacks.get(t));
				}
			}
		}
		catch (Exception e) {
			buf.append("Got exception while getting stack traces: "
				+ e.getMessage());
		}
		return buf.toString();
	}

	private static void getStack(StringBuilder buf, Thread t, StackTraceElement[] stack) {
		buf.append(t.toString() + "\n");
		for (StackTraceElement elem : stack) {
			buf.append("\t" + elem.toString() + "\n");
		}
		buf.append("\n");
	}

	private static void postErrorToAcra(boolean fatal, final StackTraceElement caller, final Object... objs) {
		log(fatal ? Level.SEVERE : Level.SEVERE, caller, objs);
	}

	private static void logError(boolean fatal, StackTraceElement caller, Object... objs) {
		if (Logger.IS_ERROR_ENABLED) {
			if (Logger.IS_DEBUG_ENABLED || fatal) {
				postErrorToAcra(fatal, caller, objs);
			}
			else {
				try {
					long i = SystemClock.uptimeMillis();
					if (i % 10 == 1) {
						postErrorToAcra(false, caller, objs);
					}
				}
				catch (Exception e) {
					if (Logger.IS_DEBUG_ENABLED) {
						Logger.debug(e);
					}
				}
			}
		}
	}

	private static String getHeader(Level level, int tid) {
		if (headersb == null) {
			return "";
		}
		headersb.setLength(0);
		getHeader(headersb, level, tid);
		return headersb.toString();
	}

	private static void getHeader(StringBuilder sb, Level level, int tid) {
		final int pri = getPriority(tid);
		sb.append(level.toString());
		sb.append(" [");
		sb.append(Thread.currentThread().getName());
		sb.append(",pri=");
		sb.append(pri);
		sb.append("] ");
	}

	private static int getPriority(final int tid) {
		final int pri = Process.getThreadPriority(tid);
		return pri;
	}

	private static String formatMessage(StackTraceElement caller, Object... objs) {
		if (CHECK_LOCK && !Thread.holdsLock(lock)) {
			throw new RuntimeException("Logger.formatMessage: not holding lock");
		}
		final int numObjs;
		if (objs == null || (numObjs = objs.length) == 0) {
			return "";
		}

		final StringBuilder sb = headersb;
		if (sb == null) {
			return "";
		}
		sb.setLength(0);

		String file = null;
		if (caller != null) {
			file = caller.getFileName();
			if (file != null) {
				final int pos = file.indexOf(".java");
				if (pos > 0) {
					file = file.substring(0, pos);
				}
				sb.append(file);
				final int line = caller.getLineNumber();
				if (line >= 0) {
					sb.append(':');
					sb.append(line);
				}
				sb.append(": ");
			}
		}

		boolean delim = false;
		for (int j = 0; j < numObjs; ++j) {
			final Object o = objs[j];
			if (delim) {
				sb.append('\n');
			}
			else {
				delim = true;
			}
			if (o instanceof Throwable) {
				Throwable e = (Throwable)o;
				do {
					String emsg = e.getMessage();
					sb.append(e.getClass().getName());
					if (emsg != null) {
						sb.append(": ");
						sb.append(emsg);
					}
					sb.append("\n");
					for (StackTraceElement ste : e.getStackTrace()) {
						sb.append("  ");
						sb.append(ste.toString());
						sb.append("\n");
					}
					e = e.getCause();
					if (e != null) {
						sb.append("caused by:\n");
					}
				} while (e != null);

			}
			else if (o instanceof Class) {
				// ignore initial class name if same as filename
				final Class<?> cls = (Class<?>)o;
				final String name = getSimpleName(cls);
				if (j != 0 || !name.equals(file)) {
					sb.append(name);
					sb.append(": ");
				}
				delim = false;

			}
			else if (o == null) {
				sb.append("<null>");

			}
			else {
				final String[] lines = lineEnd.split(o.toString());
				final int num = lines.length;
				for (int i = 0; i < num; ++i) {
					if (i > 0) {
						sb.append('\n');
					}
					sb.append(lines[i]);
				}
			}
		}

		return sb.toString();
	}

	private static String getSimpleName(Class<?> cls) {
		String name = classNames.get(cls);
		if (name == null) {
			name = cls.getSimpleName();
			if (name.length() == 0) {
				name = cls.getName();
				final int dot = name.lastIndexOf('.');
				if (dot != -1) {
					name = name.substring(dot + 1);
				}
			}
			classNames.put(cls, name);
		}
		return name;
	}

	public static String dumpIntent(Intent intent, String prefix) {
		if (intent == null) {
			return "null";
		}
		final StringBuilder sb = new StringBuilder();
		sb.append(intent.toString());
		final Uri data = intent.getData();
		if (data != null) {
			sb.append(" data=");
			sb.append(data.toString());
		}
		final Bundle b = intent.getExtras();
		if (b != null) {
			b.setClassLoader(Logger.class.getClassLoader());
			if (b.size() != 0) {
				sb.append(":\n");
				final Iterator<String> iterator = b.keySet().iterator();
				while (iterator.hasNext()) {
					final String key = iterator.next();
					if (prefix != null) {
						sb.append(prefix);
					}
					sb.append(key);
					sb.append(" = ");
					sb.append(toString(b.get(key)));
					if (iterator.hasNext()) {
						sb.append("\n");
					}
				}
			}
		}
		return sb.toString();
	}

	public static String toString(Object obj) {
		if (obj == null) {
			return "null";
		}
		else if (obj instanceof Object[]) {
			return Arrays.toString((Object[])obj);
		}
		else if (obj instanceof int[]) {
			return Arrays.toString((int[])obj);
		}
		else if (obj instanceof long[]) {
			return Arrays.toString((long[])obj);
		}
		else if (obj instanceof boolean[]) {
			return Arrays.toString((boolean[])obj);
		}
		else if (obj instanceof float[]) {
			return Arrays.toString((float[])obj);
		}
		else {
			return obj.toString();
		}
	}

	private static StackTraceElement getCallerFrame() {
		final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		return stack.length > 4 ? stack[4] : null;
	}

	public static String getCaller() {
		return getCaller(1, 5, true, true);
	}

	public static String getCaller(int depth, boolean includeMethod, boolean simpleClassName) {
		return getCaller(1, depth, includeMethod, simpleClassName);
	}

	public static String getCaller(int skip, int depth, boolean includeMethod, boolean simpleClassName) {
		final StringBuilder sb = new StringBuilder();
		final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		final int len = stack.length;
		final int start = 4 + skip;
		final int end = depth + start;
		String lastClass = null;
		String lastMethod = null;
		for (int i = start; i < len && i < end; ++i) {
			final StackTraceElement frame = stack[i];
			if (i > start) {
				sb.append(" / ");
			}
			String cls = frame.getClassName();
			if (simpleClassName) {
				final int pos = cls.lastIndexOf('.');
				if (pos >= 0) {
					cls = cls.substring(pos + 1);
				}
			}
			if (!cls.equals(lastClass)) {
				sb.append(cls);
				lastClass = cls;
			}
			if (includeMethod) {
				final String method = frame.getMethodName();
				if (!method.equals(lastMethod)) {
					sb.append('.');
					sb.append(method);
					lastMethod = method;
				}
				final int line = frame.getLineNumber();
				if (line >= 0) {
					sb.append(':');
					sb.append(line);
				}
			}
		}
		return sb.toString();
	}

	public static String getStackTrace() {
		final StringBuilder sb = new StringBuilder();
		final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		final int len = stack.length;
		if (len > 3) {
			for (int i = 3; i < len; ++i) {
				sb.append("\n    ");
				sb.append(stack[i]);
			}
		}
		return sb.toString();
	}

	private static String getFname(String fname, String ext, int num) {
		return fname + "-" + num + "." + ext;
	}

}
