package com.cp.multithread.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cp.multithread.pojo.DemoTask;
import com.cp.multithread.pojo.HttpMediaType;
import com.cp.multithread.pojo.HttpRequestEntity;
import com.cp.multithread.pojo.HttpResponseEntity;
import com.cp.multithread.pojo.JsonMessage;
import com.cp.multithread.service.DemoService;
import com.cp.multithread.util.HttpUtil;
import com.cp.multithread.util.JsonUtil;

/**
 * 
 * 针对业务处理，推送任务到中心平台，确保任务数据一定成功，所以失败以后放入延迟队列重新处理：需要完善延迟策略
 * 根据数据量的大小选择序列化：默认JDK的序列化方式
 */
@Service
public class DemoTaskExecutor {

	static Logger log = LoggerFactory.getLogger(DemoTaskExecutor.class);

	static final int defaultNThreads = Runtime.getRuntime().availableProcessors();
	@Value("${demo.task.nThreads}")
	private int nThread = 5;
	private int poolTimeout = 5 * 1000;// mills
	private AtomicBoolean running = new AtomicBoolean(true);

	private ExecutorService matchTaskExecutor;
	private LinkedBlockingQueue<DemoTask> taskQueue;

	@Autowired
	public DemoService service;

	@Value("${submit.url}")
	private String submitUrl = "";// 提交的url
	@Value("${task.data.path}")
	private String taskDatPath = "";//

	@PostConstruct
	public void init() {
		matchTaskExecutor = Executors.newFixedThreadPool(nThread);
		taskQueue = new LinkedBlockingQueue<>(1024 * 4);

		derializer();

		startRunner();
	}

	private void derializer() {
		File distFile = new File(taskDatPath + File.pathSeparator + "demotask.dat");
		if (!distFile.exists()) {
			return;
		}
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(new FileInputStream(distFile));
			List<DemoTask> data = (List<DemoTask>) ois.readObject();
			log.info("derializer task dat=" + data);
			if (data != null) {
				taskQueue.addAll(data);
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
		matchTaskExecutor.shutdown();
		while (!matchTaskExecutor.isTerminated()) {
			sleepWhile(1000);
		}
		log.info("demotask remain queue size=" + taskQueue.size());

		serializer();
	}

	private void serializer() {
		File distFile = new File(taskDatPath + File.pathSeparator + "demotask.dat");
		ObjectOutputStream oos = null;
		try {
			if (taskQueue.isEmpty()) {
				return;
			}
			List<DemoTask> data = new ArrayList<>();
			taskQueue.drainTo(data);
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
		for (int i = 0; i < nThread; i++) {
			matchTaskExecutor.submit(() -> {
				while (true) {
					DemoTask taskItem = null;
					try {
						taskItem = taskQueue.poll(poolTimeout, TimeUnit.MILLISECONDS);
						if (taskItem != null) {
							doWork(taskItem);
						} else if (!running.get()) {
							break;
						}
					} catch (Exception e) {
						if (!(e instanceof InterruptedException)) {
							log.error("process task failed,taskItem=" + taskItem, e);
						}
					}
				}
			});
		}
	}

	private void doWork(DemoTask demoTask) throws Exception {
		String data = service.doDB(demoTask.taskId);
		log.debug("data=" + data + " for task=" + demoTask);
		if (StringUtils.isBlank(data)) {
			// no need optimization
			return;
		}
		data = processDataBeforeSend(data);

		if (needSend(data)) {
			doSend(demoTask.taskId, data);
		}
	}

	private boolean needSend(String data) {
		return !data.isEmpty();
	}

	private String processDataBeforeSend(String data) {
		return data;
	}

	private void doSend(String taskId, String data) {
		HttpResponseEntity response = null;
		String jsonData = buildData(taskId, data);
		try {
			HttpRequestEntity request = new HttpRequestEntity();
			request.body = jsonData;
			response = HttpUtil.post(submitUrl, request, HttpMediaType.APPLICATION_JSON);
		} catch (Exception e) {
		}
		log.info("resp=" + response + " for jsonData=" + jsonData);

		// 失败的消息需要根据策略延迟发送
		handleResponse(response, jsonData);

	}

	private String buildData(String taskId, String data) {
		TaskPacket packet = new TaskPacket();
		packet.msgId = buildReqId();
		packet.taskId = taskId;
		packet.realData = data;
		return JsonUtil.toJson(packet);
	}

	private void handleResponse(HttpResponseEntity response, String jsonDat) {
		boolean needRetry = true;
		if (response != null && response.body != null) {
			JsonMessage<String> baseMsg = JsonUtil.fromJson(response.body, JsonMessage.class);
			if (baseMsg != null && baseMsg.code == 0) {
				needRetry = false;
			}
		}
		if (needRetry) {
			boolean submitted = DemoDelayTaskExecutor.submitFailedTask(jsonDat);
			log.info("jsonDat =" + jsonDat + " submitted result=" + submitted);
		}
	}

	private String buildReqId() {
		return UUID.randomUUID().toString();
	}

	public boolean submitTask(DemoTask taskItem) {
		if (taskItem == null) {
			return false;
		}
		boolean suc = true;
		for (int i = 0; i < 10; i++) {
			suc = taskQueue.offer(taskItem);
			if (suc) {
				break;
			}
			// Thread.yield();
			sleepWhile(TimeUnit.MILLISECONDS.convert(20, TimeUnit.MILLISECONDS));
		}
		return suc;
	}

	void sleepWhile(long timeout) {
		try {
			TimeUnit.MILLISECONDS.sleep(timeout);
		} catch (InterruptedException e) {
		}
	}

	public int getPoolTimeout() {
		return poolTimeout;
	}

	public void setPoolTimeout(int poolTimeout) {
		this.poolTimeout = poolTimeout;
	}
}

class TaskPacket {
	public String msgId;// 消息unique ID
	public String taskId;
	public String realData;
}
