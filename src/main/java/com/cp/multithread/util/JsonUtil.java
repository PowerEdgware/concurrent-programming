package com.cp.multithread.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

public class JsonUtil {

	
	public static <T> T fromJson(String json, Class<T> clazz) {
		return JSON.parseObject(json, clazz);
	}

	public static <T> T fromJson(String json, TypeReference<T> type) {
		return JSON.parseObject(json, type);
	}

	public static <T> String toJson(T obj) {
		return JSON.toJSONString(obj);
	}
}
