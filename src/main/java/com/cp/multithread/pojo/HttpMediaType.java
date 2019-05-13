package com.cp.multithread.pojo;

public enum HttpMediaType {

	APPLICATION_JSON("application/json");

	private String contentType;

	private HttpMediaType(String contentType) {
		this.contentType = contentType;
	}

	public String getContentType() {
		return this.contentType;
	}
}
