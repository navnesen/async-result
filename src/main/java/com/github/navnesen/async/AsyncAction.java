package com.github.navnesen.async;

public interface AsyncAction<T> {
	T run() throws Throwable;
}
