package com.cp.multithread.task;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import com.cp.multithread.pojo.JsonMessage;
import com.cp.multithread.util.HttpUtil;
import com.cp.multithread.util.JsonUtil;

/**
 * 延时任务，发送定时任务数据：根据重试策略重试
 *
 */
@Service
public class DemoDelayTaskExecutor {

	static Logger log = LoggerFactory.getLogger(DemoDelayTaskExecutor.class);

	static final int defaultNThreads = Runtime.getRuntime().availableProcessors();
	private AtomicBoolean running = new AtomicBoolean(true);

	private ExecutorService delayTaskExecutor;
	private static DelayQueue<DemoDelayTask> delayedQueue;

	@Value("${dest.url}")
	private String dataSendUrl = "";
	@Value("${seri.data.path}")
	private String taskDatPath = "";//序列化位置

	@Value("${delaytask.maxretry}")
	private int delayTaskMaxRetry = 10;//
	@Value("${delaytask.delaytime.mills}")
	private static int delayTime = 15 * 1000;//重试时间

	@PostConstruct
	public void init() {
		delayTaskExecutor = Executors.newFixedThreadPool(defaultNThreads);
		delayedQueue = new DelayQueue<>();
		derializer();
		startRunner();
	}

	private void derializer() {
		File distFile = new File(taskDatPath + File.pathSeparator + "delaytask.dat");
		if (!distFile.exists()) {
			return;
		}
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(new FileInputStream(distFile));
			List<DemoDelayTask> data = (List<DemoDelayTask>) ois.readObject();
			log.info("derializer delay task dat=" + data);
			if (data != null) {
				delayedQueue.addAll(data);
			}
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			distFile.delete();
			if (ois != null) {
				try {
					ois.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@PreDestroy
	public void stopTask() {
		running.set(false);
		delayTaskExecutor.shutdown();
		while (!delayTaskExecutor.isTerminated()) {
			sleepWhile(1000);
		}
		log.info("FaceMatchTask remain queue size=" + delayedQueue.size());

		serializer();
	}

	private void serializer() {
		File distFile = new File(taskDatPath + File.pathSeparator + "delaytask.dat");
		ObjectOutputStream oos = null;
		try {
			if (delayedQueue.isEmpty()) {
				return;
			}
			List<DemoDelayTask> data = new ArrayList<>();
			delayedQueue.drainTo(data);
			oos = new ObjectOutputStream(new FileOutputStream(distFile));
			oos.writeObject(data);
			log.info("serializer task data=" + data);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (oos != null) {
				try {
					oos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void startRunner() {
		for (int i = 0; i < defaultNThreads; i++) {
			delayTaskExecutor.submit(() -> {
				while (true) {
					DemoDelayTask taskItem = null;
					try {
						taskItem = delayedQueue.poll(5000, TimeUnit.MILLISECONDS);
						if (taskItem != null) {
							doWork(taskItem);
						} else if (!running.get()) {
							break;
						}
					} catch (Exception e) {
						if (!(e instanceof InterruptedException)) {
							log.error("process task failed,taskItem=" + taskItem, e);
						} else if (!running.get()) {
							break;
						}
					}
				}
			});
		}
	}

	private void doWork(DemoDelayTask taskItem) throws Exception {
		HttpResponseEntity response = null;
		if (taskItem.retry >= delayTaskMaxRetry) {
			log.warn("retry exhausted after " + delayTaskMaxRetry + " times delaytask=" + taskItem);
			return;
		}
		HttpRequestEntity request = buildRequest(taskItem);
		try {
			response = HttpUtil.post(dataSendUrl,request, com.cp.multithread.pojo.HttpMediaType.APPLICATION_JSON);
		} catch (Exception e) {
			e.printStackTrace();
		}
		log.debug("response=" + response + " for delayTask=" + taskItem);

		reExecuteIfNecessary(response, taskItem);

	}

	private HttpRequestEntity buildRequest(DemoDelayTask taskItem) {
		// TODO Auto-generated method stub
		return null;
	}

	//TODO  是否进行重试,成功则不在重试:可把重试策略单独出来
	void reExecuteIfNecessary(HttpResponseEntity response, DemoDelayTask taskItem) {
		try {
			taskItem.retry += 1;
			boolean needReExecute = true;
			if (response != null && !StringUtils.isBlank(response.body)) {
				JsonMessage<String> baseMsg = JsonUtil.fromJson(response.body, JsonMessage.class);
				if (baseMsg != null && baseMsg.code == 100) {
					needReExecute = false;
				}
			} else if (taskItem.retry >= delayTaskMaxRetry) {
				needReExecute = false;
			}
			if (needReExecute) {
				long newDelayTime = taskItem.retry * delayTime;
				taskItem.setNextExecutionTime(newDelayTime);
				boolean rst = delayedQueue.offer(taskItem);
				log.info("delay task add to queue again." + taskItem + " suc=" + rst);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean submitFailedTask(String demoData) {
		if (StringUtils.isBlank(demoData)) {
			return false;
		}
		DemoDelayTask taskItem = buildTask(demoData);
		boolean suc = true;
		for (int i = 0; i < 10; i++) {
			suc = delayedQueue.offer(taskItem);
			if (suc) {
				break;
			}
			// Thread.yield();
			sleepWhile(TimeUnit.MILLISECONDS.convert(20, TimeUnit.MILLISECONDS));
		}
		return suc;
	}

	private static DemoDelayTask buildTask(String demoData) {
		DemoDelayTask task = new DemoDelayTask(delayTime, 1, demoData);
		return task;
	}

	static void sleepWhile(long timeout) {
		try {
			TimeUnit.MILLISECONDS.sleep(timeout);
		} catch (InterruptedException e) {
		}
	}

	static class DemoDelayTask implements Delayed, Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 4026862541898101835L;

		private volatile long excutionTime = System.nanoTime();// 下次执行时间
		private volatile int retry;// 已经重试的次数
		String data;

		public DemoDelayTask() {
		}

		public DemoDelayTask(long delayTime, int maxretry, String data) {
			retry = maxretry;
			this.excutionTime = TimeUnit.NANOSECONDS.convert(delayTime, TimeUnit.MILLISECONDS) + System.nanoTime();
			this.data = data;
		}

		@Override
		public int compareTo(Delayed o) {
			if (o == this) {
				return 0;
			} else if (o instanceof DemoDelayTask) {
				DemoDelayTask other = (DemoDelayTask) o;
				long diff = excutionTime - other.excutionTime;
				if (diff < 0) {
					return -1;
				} else if (diff > 0) {
					return 1;
				}
			}
			long diff = getDelay(NANOSECONDS) - o.getDelay(NANOSECONDS);
			return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
		}

		@Override
		public long getDelay(TimeUnit unit) {
			return unit.convert(this.excutionTime - System.nanoTime(), TimeUnit.NANOSECONDS);
		}

		public void setNextExecutionTime(long newDelayTime) {
			this.excutionTime = TimeUnit.NANOSECONDS.convert(newDelayTime, TimeUnit.MILLISECONDS) + System.nanoTime();
		}

		public long getExcutionTime() {
			return excutionTime;
		}

		public void setExcutionTime(long excutionTime) {
			this.excutionTime = excutionTime;
		}

		public int getRetry() {
			return retry;
		}

		public void setRetry(int retry) {
			this.retry = retry;
		}

		public String getData() {
			return data;
		}

		public void setData(String data) {
			this.data = data;
		}

		@Override
		public String toString() {
			return "SnapFaceDelayTask [excutionTime=" + excutionTime + ", retry=" + retry + ", data=" + data + "]";
		}
	}
}
