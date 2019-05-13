package com.cp.multithread.pojo;

import java.util.Map;

public class HttpResponseEntity {

	public int responseCode = 200;
	public Map<String, String> respHeaderMap;

	public String contentType = "application/json";
	
	public String body;
}
