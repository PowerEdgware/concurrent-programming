package com.cp.util;

import java.lang.Thread.UncaughtExceptionHandler;

public class SimpleExceptionHandler implements UncaughtExceptionHandler {

	private static volatile SimpleExceptionHandler instance;

	public static SimpleExceptionHandler getInstance() {
		if (instance == null) {
			synchronized (SimpleExceptionHandler.class) {
				if (instance == null) {
					instance = new SimpleExceptionHandler();
				}
			}
		}
		return instance;
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		// TODO do some err log
		t.interrupt();
	}

}
