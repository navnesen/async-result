package com.github.navnesen.async;

import com.github.navnesen.sync.Mutex;
import com.github.navnesen.util.Result;
import com.github.navnesen.util.common.InspectAction;
import com.github.navnesen.util.common.TypeActionMap;

public class AsyncResult<T> implements Awaitable<T> {

	public static <T> AsyncResult<T> ok(T value) {
		return new AsyncResult<>(Result.ok(value));
	}

	public static AsyncResult<?> err(Throwable exception) {
		return new AsyncResult<>(Result.err(exception));
	}

	protected Mutex<Result<T, Throwable>> internalResult = new Mutex<>(null);

	protected AsyncResult() {
	}

	protected AsyncResult(Result<T, Throwable> result) {
		try (var value = this.internalResult.lock()) {
			value.set(result);
		}
	}

	public AsyncResult(AsyncAction<T> action) {
		final StackTraceElement[] mainStackTrace;
		{
			var tempStackTrace = Thread.currentThread().getStackTrace();
			mainStackTrace = new StackTraceElement[tempStackTrace.length - 1];
			System.arraycopy(tempStackTrace, 1, mainStackTrace, 0, tempStackTrace.length - 1);
		}
		new Thread(() -> {
			Result<T, Throwable> completion;

			try {
				var result = action.run();
				completion = Result.ok(result);
			} catch (Throwable exception) {
				var originalStackTrace = exception.getStackTrace();
				var newStackTrace = new StackTraceElement[originalStackTrace.length - 2 + mainStackTrace.length];
				System.arraycopy(originalStackTrace, 0, newStackTrace, 0, originalStackTrace.length - 2);
				System.arraycopy(mainStackTrace, 0, newStackTrace, originalStackTrace.length - 2, mainStackTrace.length);
				exception.setStackTrace(newStackTrace);
				completion = Result.err(exception);
			}

			try {
				this.complete(completion);
			} catch (Exception e) {
				// will not happen
			}
		}).start();
	}

	public AsyncResult(AsyncActionResult<T> action) {
		final StackTraceElement[] mainStackTrace;
		{
			var tempStackTrace = Thread.currentThread().getStackTrace();
			mainStackTrace = new StackTraceElement[tempStackTrace.length - 1];
			System.arraycopy(tempStackTrace, 1, mainStackTrace, 0, tempStackTrace.length - 1);
		}
		new Thread(() -> {
			Result<T, Throwable> completion;

			try {
				completion = action.run();
			} catch (Throwable exception) {
				var originalStackTrace = exception.getStackTrace();
				var newStackTrace = new StackTraceElement[originalStackTrace.length - 2 + mainStackTrace.length];
				System.arraycopy(originalStackTrace, 0, newStackTrace, 0, originalStackTrace.length - 2);
				System.arraycopy(mainStackTrace, 0, newStackTrace, originalStackTrace.length - 2, mainStackTrace.length);
				exception.setStackTrace(newStackTrace);
				completion = Result.err(exception);
			}

			try {
				this.complete(completion);
			} catch (Exception e) {
				// will not happen
			}
		}).start();
	}

	public synchronized Result<T, Throwable> await() {
		Result<T, Throwable> result;
		try (var value = this.internalResult.lock()) {
			result = value.get();
		}
		while (result == null) {
			try {
				wait();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			try (var value = this.internalResult.lock()) {
				result = value.get();
			}
		}
		return result;
	}

	protected synchronized void complete(Result<T, Throwable> result) throws Exception {
		try (var value = this.internalResult.lock()) {
			if (value.get() != null) {
				throw new Exception("Async result is already completed!");
			}
			value.set(result);
		}
	}

	public <U> AsyncResult<U> map(TypeActionMap<T, U> action) {
		return new AsyncResult<>(() -> this.await().map(action));
	}

	public <U> AsyncResult<U> mapOr(TypeActionMap<T, U> action, T defaultValue) {
		return new AsyncResult<>(() -> this.await().mapOr(action, defaultValue));
	}

	public AsyncResult<T> mapErr(TypeActionMap<Throwable, Throwable> action) {
		return new AsyncResult<>(() -> this.await().mapErr(action));
	}

	public AsyncResult<T> inspect(InspectAction<T> action) {
		return new AsyncResult<>(() -> this.await().inspect(action));
	}

	public AsyncResult<T> inspectErr(InspectAction<Throwable> action) {
		return new AsyncResult<>(() -> this.await().inspectErr(action));
	}

	public <U> AsyncResult<U> and(AsyncResult<U> res) {
		return new AsyncResult<U>(() -> this.await().and(res.await()));
	}

	public <U> AsyncResult<U> andThen(TypeActionMap<T, AsyncResult<U>> action) {
		return new AsyncResult<>(() -> this.await().andThen((value) -> action.run(value).await()));
	}

	public AsyncResult<T> or(AsyncResult<T> res) {
		return new AsyncResult<>(() -> this.await().or(res.await()));
	}

	public AsyncResult<T> orElse(TypeActionMap<Throwable, AsyncResult<T>> action) {
		return new AsyncResult<>(() -> this.await().orElse((exception) -> action.run(exception).await()));
	}
}
