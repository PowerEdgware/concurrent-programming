package com.cp;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class ThreadInfoDemo {

	public static void main(String[] args) {
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		ThreadInfo[] infos = threadMXBean.dumpAllThreads(false, false);
		for (ThreadInfo threadInfo : infos) {
			System.out.println(
					threadInfo.getThreadId() + " " + threadInfo.getThreadName() + " " + threadInfo.getThreadState());
		}
		try {
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
