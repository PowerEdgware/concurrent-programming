package com.cp.multithread.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cp.multithread.pojo.DemoTask;
import com.cp.multithread.pojo.JsonMessage;
import com.cp.multithread.task.DemoTaskExecutor;

@RestController
public class DemoController {

	@Autowired
	private DemoTaskExecutor taskExecutor;

	@RequestMapping("/demoTask")
	public JsonMessage<String> addDemoTask(HttpServletRequest request, @RequestParam(name = "taskId") String taskId) {
		JsonMessage<String> msg = new JsonMessage<>();
		//do some business
		DemoTask demoTask = buildTask(taskId);
		boolean suc = taskExecutor.submitTask(demoTask);
		if (!suc) {
			msg.code = 1001;
		}
		return msg;
	}

	private DemoTask buildTask(String taskId) {
		DemoTask demoTask = new DemoTask();
		demoTask.taskId = taskId;
		return demoTask;
	}
}
