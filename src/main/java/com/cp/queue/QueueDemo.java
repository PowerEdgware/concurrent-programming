package com.cp.queue;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class QueueDemo {

	public static void main(String[] args) {

		SimpleBlockQueue<String> queue = new SimpleBlockQueue<>(16);
		final Thread takeThread = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				String ele = null;
				try {
					System.out.println(Thread.currentThread().getName() + " takes elem=" + ele + " at="
							+ LocalDateTime.now() + "before take queuesize=" + queue.size());
					ele = queue.take();
					System.out.println(Thread.currentThread().getName() + " takes elem=" + ele + " at="
							+ LocalDateTime.now() + " after take queuesize=" + queue.size());
					LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(10));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					e.printStackTrace();
				}
			}
			System.out.println(Thread.currentThread().getName() + " stopped at=" + LocalDateTime.now());
		}, "takeThread");
		takeThread.start();

		final Thread putThread = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				String val = getValue();
				try {
					System.out.println(Thread.currentThread().getName() + " before puts elem=" + val + " at="
							+ LocalDateTime.now() + " before put queuesize=" + queue.size());
					queue.put(val);
					System.out.println(Thread.currentThread().getName() + " puts elem=" + val + " at="
							+ LocalDateTime.now() + " after put queuesize=" + queue.size());
					LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					e.printStackTrace();
				}
			}
			System.out.println(Thread.currentThread().getName() + " stopped at=" + LocalDateTime.now());
		}, "putThread");
		putThread.start();

		int s = -1;
		try {
			s = System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (Thread.activeCount() > 1 && s != 'q') {
			Thread.yield();
			LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2000));
			try {
				s = System.in.read();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		putThread.interrupt();
		takeThread.interrupt();

	}

	static String getValue() {
		return (new Random().nextInt(10000) + 999) + "";
	}
}
