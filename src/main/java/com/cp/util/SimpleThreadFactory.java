package com.cp.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleThreadFactory implements java.util.concurrent.ThreadFactory {
	private static SimpleExceptionHandler UNCAUGHT_EXCEPTION_HANDLER = SimpleExceptionHandler.getInstance();

	private static AtomicInteger POOL_NUMBER = new AtomicInteger(1);
	private AtomicLong threadNumber = new AtomicLong(1);
	private ThreadGroup group;
	private String factoryName;
	private int threadPriority;
	private String poolName;
	private boolean isDaemon;

	public static SimpleThreadFactory create(String name) {
		return new SimpleThreadFactory(name, false, Thread.NORM_PRIORITY);
	}

	private SimpleThreadFactory(String name, boolean isDaemon, int threadPriority) {
		this.group = this.getThreadGroup();
		this.factoryName = name;
		this.isDaemon = isDaemon;
		this.threadPriority = threadPriority;
		this.poolName = factoryName + "_" + POOL_NUMBER.getAndIncrement();
	}

	@Override
	public Thread newThread(Runnable r) {
		String threadNum = String.valueOf(threadNumber.getAndIncrement());
		Thread thread = new Thread(group, r, poolName + "_thread_" + threadNum, 0);

		thread.setPriority(threadPriority);
		thread.setDaemon(isDaemon);
		thread.setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);
		return thread;
	}

	public static SimpleExceptionHandler getUncaught() {
		return UNCAUGHT_EXCEPTION_HANDLER;
	}

	public int getPoolNumber() {
		return POOL_NUMBER.get();
	}

	public long getThreadNumber() {
		return threadNumber.get();
	}

	public String getFactoryName() {
		return factoryName;
	}

	public int getThreadPriority() {
		return threadPriority;
	}

	public String getPoolName() {
		return poolName;
	}

	public boolean isDaemon() {
		return isDaemon;
	}

	private ThreadGroup getThreadGroup() {
		SecurityManager sm = System.getSecurityManager();
		if (null != sm) {
			return sm.getThreadGroup();
		}
		return Thread.currentThread().getThreadGroup();
	}

}
