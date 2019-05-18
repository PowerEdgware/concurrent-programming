package com.cp.lock;

import java.util.concurrent.locks.Lock;

import com.cp.util.Profiler;

public class MetuxLockDemo {

	int count;

	Lock lock = new MutexLock();

	public void inc() {
		lock.lock();
		try {
			count++;
		} finally {
			lock.unlock();
		}
	}

	public int getCount() {
		return this.count;
	}

	public static void main(String[] args) {
		int threadCount = 10;
		MetuxLockDemo demo = new MetuxLockDemo();
		for (int i = 0; i < threadCount; i++) {
			Thread t = new Thread(demo.new CountRunner(demo), "count-" + i);
			t.start();
		}
		Profiler.start();
		// wait for complete
		while (Thread.activeCount() > 1) {
			Thread.yield();
		}
		long cost=Profiler.end();
		System.out.println("Done! \t" + demo.getCount()+" cost="+cost+" mills");

	}

	 class CountRunner implements Runnable {
		private MetuxLockDemo demo;

		public CountRunner(MetuxLockDemo demo) {
			this.demo = demo;
		}

		@Override
		public void run() {
			for (int i = 0; i < 1000; i++) {
				demo.inc();
			}
		}
	}
}
