package com.github.navnesen.async;

import com.github.navnesen.Result;

public interface AsyncActionResult<T> {
	Result<T, Throwable> run() throws Throwable;
}
