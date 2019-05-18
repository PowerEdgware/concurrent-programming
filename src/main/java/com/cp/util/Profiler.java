package com.cp.util;

public class Profiler {

	private static final ThreadLocal<Long> Thread_local = new ThreadLocal<>() {
		protected Long initialValue() {
			return System.currentTimeMillis();
		};
	};

	public static final void start() {
		Thread_local.set(System.currentTimeMillis());
	}

	public static final long end() {
		return System.currentTimeMillis() - Thread_local.get();
	}
}
