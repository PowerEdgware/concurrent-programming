package com.cp.multithread.service;

import org.springframework.stereotype.Service;

@Service
public class DemoService {

	public String doDB(String reqId) {
		return "DB Operate";
	}
}
