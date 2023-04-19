package com.github.navnesen.async;

import com.github.navnesen.util.Result;

public interface Awaitable<T> {
	Result<T, Throwable> await();
}
