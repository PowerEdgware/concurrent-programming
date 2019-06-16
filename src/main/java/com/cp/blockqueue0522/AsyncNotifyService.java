package com.cp.blockqueue0522;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cp.multithread.pojo.HttpRequestEntity;
import com.cp.multithread.pojo.HttpResponseEntity;
import com.cp.util.HttpUtil;
import com.cp.util.SimpleThreadFactory;

/**
 * 异步方式实现订单办理完成的回调通知
 * 
 * @author 阿菜
 *
 */
@Service
public class AsyncNotifyService {

	private static Logger log = LoggerFactory.getLogger(AsyncNotifyService.class);

	@Value("${async.notify.nthreads}")
	private static int nThreads = 4;
	@Value("${async.notify.pollTimeout}")
	private int pollTimeout = 6 * 1000;
	private AtomicBoolean stoped = new AtomicBoolean(false);
	private ThreadPoolExecutor executor;
	private LinkedBlockingQueue<String> simpleUrlQueue;

	@PostConstruct
	private void init() {
		executor = new ThreadPoolExecutor(nThreads, nThreads * 2, 180L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(1024), SimpleThreadFactory.create("AsyncNotify"));
		simpleUrlQueue = new LinkedBlockingQueue<String>();
		// load from file
		deserializeQueue();

		// pre start pool thread
		startPooolThread();
	}

	private void deserializeQueue() {
		// TODO use protobuf or jdk
		// deserialize data to queue
	}

	private void startPooolThread() {
		for (int i = 0; i < nThreads; i++) {
			NotifyUrlRunner notifyUrlRunner = new NotifyUrlRunner();
			executor.submit(notifyUrlRunner);
		}
	}

	@PreDestroy
	private void destroy() {
		stoped.set(true);
		// do save
		serializeQueue();
	}

	private void serializeQueue() {
		// 序列化到磁盘
		// simpleUrlQueue
	}

	public boolean add2CallbackQueue(String notifyUrl) {
		if (null == notifyUrl) {
			log.error("add notifyUrl null");
			return false;
		}
		//TODO  do loop offer if necessary
		return simpleUrlQueue.offer(notifyUrl);
	}

	class NotifyUrlRunner implements Runnable {
		private Logger log = LoggerFactory.getLogger(NotifyUrlRunner.class);

		@Override
		public void run() {
			String url = null;
			while (!stoped.get()) {
				url = getUrl(false);
				if (!StringUtils.isBlank(url)) {
					dotask(url);
				}
			}
			if (stoped.get()) {
				executeRemained();
			}
		}

		private void executeRemained() {
			String url = null;
			for (;;) {
				url = getUrl(true);
				if (null == url) {
					return;
				}
				if (!StringUtils.isBlank(url)) {
					dotask(url);
				}
			}
		}

		private String getUrl(boolean isRemainWork) {
			if (isRemainWork) {
				return simpleUrlQueue.poll();
			} else {
				return pollWithTimeOut();
			}
		}

		private String pollWithTimeOut() {
			String url = null;
			try {
				url = simpleUrlQueue.poll(pollTimeout, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				if (stoped.get()) {
					log.error(e.toString() + ", notifyUrlRunner stop");
				} else {
					log.error(e.toString(), e);
				}
			}
			return url;
		}

		private void dotask(String url) {
			log.debug("incoming url=" + url);
			HttpResponseEntity response = null;
			try {
				HttpRequestEntity request = new HttpRequestEntity();
				response = HttpUtil.get(url, request);
			} catch (Exception e) {
				e.printStackTrace();
			}
			log.debug("response=" + response + " for url=" + url);
		}

	}
}
