package com.cp.queue;

import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleBlockQueue<E> {

	private Lock lock = new ReentrantLock();
	private Condition full = lock.newCondition();
	private Condition empty = lock.newCondition();
	private LinkedList<E> dataList;
	private final int capacity;

	public SimpleBlockQueue() {
		this(Integer.MAX_VALUE);
	}

	public SimpleBlockQueue(int capacity) {
		this.capacity = capacity;
		dataList = new LinkedList<>();
	}

	public void put(E e) throws InterruptedException {
		Objects.requireNonNull(e, "element can't be null");
		lock.lock();
		try {
			while (dataList.size() == capacity) {
				full.await();
			}
			dataList.addLast(e);// ensure FIFO
			// notify not empty
			empty.signal();
		} finally {
			lock.unlock();
		}
	}

	public E take() throws InterruptedException {
		lock.lock();
		try {
			while (dataList.isEmpty()) {
				empty.await();
			}
			E e = dataList.poll();

			// notify not full
			full.signal();

			return e;
		} finally {
			lock.unlock();
		}
	}

	public int size() throws InterruptedException {
		lock.lock();
		try {
			return dataList.size();
		} finally {
			lock.unlock();
		}
	}

}
