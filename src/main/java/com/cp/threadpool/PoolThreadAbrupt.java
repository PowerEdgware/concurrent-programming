package com.cp.threadpool;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class PoolThreadAbrupt implements Runnable {

	
	public static void main(String[] args) {
		
		int coresize=5;
		final ThreadPoolExecutor executor=new ThreadPoolExecutor(coresize, coresize, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(16));
		
		int loop=0;
		while(loop<coresize) {
			executor.execute(PoolThreadAbrupt::new);//PoolThreadAbrupt::new
			loop++;
		}
		final AtomicBoolean stop=new AtomicBoolean(false);
		
		Thread monitor=new Thread("monitor-thread") {
			@Override
			public void run() {
				while(!stop.get()) {
					System.err.println(this.getName()+" visits threadpool="+executor);
					System.err.println(this.getName()+" visits threadpool="+executor.getPoolSize());
					LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(3000));
					if(stop.get()) {
						break;
					}
					if(executor.getActiveCount()<=0) {
						executor.execute(new PoolThreadAbrupt());
					}
				}
			}
		};
		monitor.start();
		
		try {
			System.in.read();
			
			executor.shutdownNow();
			
			stop.compareAndSet(false, true);
			monitor.interrupt();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static int bound=10;
	
	@Override
	public void run() {
		while(!Thread.currentThread().isInterrupted()) {
			int rnd=ThreadLocalRandom.current().nextInt(bound);
			if(rnd%2==0) {
				System.out.println(Thread.currentThread().getName()+" Will throw Exception");
				throw new Error(Thread.currentThread().getName()+" exits!");
			}
			LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1000));
		}
		System.out.println(Thread.currentThread().getName()+" Exit");
	}
}
