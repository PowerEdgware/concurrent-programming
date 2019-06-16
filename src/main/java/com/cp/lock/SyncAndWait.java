package com.cp.lock;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

public class SyncAndWait {

	Lock lock = new ReentrantLock();

	Condition waitWhileEmpty = lock.newCondition();

	String result;

	public String getAndExecudeName() throws InterruptedException {
		try {
			lock.lock();
			while (result == null && Thread.currentThread().getName().startsWith("Thread-")) {
				waitWhileEmpty.await();//线程A这里signal之后被中断,A从await处返回后，线程调用了selfInterrupt()中断自己，此时Thread.interrupted()返回true
			}
			waitWhileEmpty.await();//线程A再次调用await,由于Thread.interrupted()为真，所以上边对A的中断在这里得到了异常的结果，抛出：InterruptedException
			System.out.println(Thread.currentThread() + " hold lock=" + lock);
			LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1000));
			return result;
		} finally {
			lock.unlock();
		}
	}

	public void setResult(String result) {
		try {
			lock.lock();
			this.result = result;
			waitWhileEmpty.signal();
		} finally {
			lock.unlock();
		}
	}

	public static void main(String[] args) {
		SyncAndWait syncAndWait = new SyncAndWait();
		Thread waitThread = new Thread(() -> {
			String result;
			try {
				result = syncAndWait.getAndExecudeName();
				System.out.println(result);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}, "Thread-Wait");
		waitThread.start();

		Thread holdLockThread = new Thread(() -> {
			try {
				syncAndWait.getAndExecudeName();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}, "holdLockThread");

		holdLockThread.start();

		Thread daemonThread = new Thread(() -> {
			LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(10));// wait for
																// debug
			System.out.println(Thread.currentThread().getName() + " Ready to interrupt " + waitThread);
			while (waitThread.isAlive() && !waitThread.isInterrupted()) {
				waitThread.interrupt();
				System.out.println(Thread.currentThread().getName() + " interrupts " + waitThread.getName());
				// wait a while
				LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
			}
		}, "daemonThread");
		daemonThread.setDaemon(true);
		daemonThread.start();

		try {
			while (System.in.read() == 'q') {
				holdLockThread.interrupt();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
