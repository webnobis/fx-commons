package com.webnobis.commons.fx.test;

import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * JUnit 5 extension to test Java Fx applications
 * 
 * @author steffen
 *
 */
public class GuiTestExtension
		implements ParameterResolver, BeforeAllCallback, BeforeEachCallback, AfterAllCallback, InvocationInterceptor {

	static final int DEFAULT_TIMEOUT_SECONDS = 10;

	private static final int STAGE_SHOWING_CHECK_INTERVAL_MILLIS = 200;

	private static final Logger log = LoggerFactory.getLogger(GuiTestExtension.class);

	private static final AtomicReference<Stage> stageRef = new AtomicReference<>();

	private static final AtomicReference<Thread> platformThreadRef = new AtomicReference<>();

	private static final AtomicInteger instanceCounterRef = new AtomicInteger();

	/**
	 * Starts the Java Fx platform at first test and waits until the Java Fx
	 * platform is running
	 */
	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		if (instanceCounterRef.getAndIncrement() < 1) {
			CountDownLatch waitForPlatform = new CountDownLatch(1);
			Platform.setImplicitExit(false);
			Platform.startup(() -> {
				Optional.of(Thread.currentThread()).filter(unused -> Platform.isFxApplicationThread())
						.ifPresent(platformThreadRef::set);
				log.debug("java fx started");
				waitForPlatform.countDown();
			});
			waitForPlatform.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
	}

	/**
	 * Exits the Java Fx platform at last test, if running
	 */
	@Override
	public void afterAll(ExtensionContext context) throws Exception {
		if (instanceCounterRef.decrementAndGet() < 1) {
			Optional.ofNullable(platformThreadRef.getAndSet(null)).filter(thread -> thread.isAlive())
					.ifPresent(unused -> Platform.exit());
			log.debug("java fx stopped");
		}
	}

	/**
	 * Creates a new primary stage
	 */
	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		if (context.getTestMethod().map(Method::getParameterTypes).map(Arrays::stream)
				.flatMap(stream -> stream.filter(Stage.class::isAssignableFrom).findFirst()).isPresent()) {
			CountDownLatch waitForStage = new CountDownLatch(1);
			Platform.runLater(() -> {
				stageRef.set(new GuiTestStage(context.getTestClass().map(Class::getSimpleName)
						.flatMap(c -> context.getTestMethod().map(Method::getName).map(m -> c.concat("#").concat(m)))
						.orElse("unknown"), Instant.now()));
				waitForStage.countDown();
			});
			waitForStage.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
	}

	@Override
	public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext context) throws Throwable {
		AtomicReference<Throwable> exceptionRef = new AtomicReference<>();
		CountDownLatch waitForStageNotMoreShowing = new CountDownLatch(1);
		Platform.runLater(() -> {
			try {
				invocation.proceed();
			} catch (Throwable t) {
				exceptionRef.set(t);
				waitForStageNotMoreShowing.countDown();
			}
			Optional.ofNullable(stageRef.getAndSet(null)).ifPresentOrElse(stage -> {
				Timer timer = new Timer("stage-checker");
				timer.scheduleAtFixedRate(new TimerTask() {

					@Override
					public void run() {
						if (!stage.isShowing()) {
							timer.cancel();
							waitForStageNotMoreShowing.countDown();
						}
					}
				}, STAGE_SHOWING_CHECK_INTERVAL_MILLIS, STAGE_SHOWING_CHECK_INTERVAL_MILLIS);
			}, () -> waitForStageNotMoreShowing.countDown());
		});
		waitUntilTimeout(context.getRequiredTestMethod(), waitForStageNotMoreShowing);
		Throwable t = exceptionRef.get();
		if (t != null) {
			throw t;
		}
	}

	private void waitUntilTimeout(Method method, CountDownLatch waitFor) throws InterruptedException, TimeoutException {
		long value;
		TimeUnit unit;
		if (method.isAnnotationPresent(Timeout.class)) {
			Timeout timeout = method.getAnnotation(Timeout.class);
			value = timeout.value();
			unit = timeout.unit();
		} else {
			value = DEFAULT_TIMEOUT_SECONDS;
			unit = TimeUnit.SECONDS;
		}
		if (!waitFor.await(value, unit)) {
			throw new TimeoutException(
					MessageFormat.format("timeout, method execution takes longer than {0}s", unit.toSeconds(value)));
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		Class<?> type = parameterContext.getParameter().getType();
		return Stage.class.isAssignableFrom(type);
	}

	/**
	 * Accepts Stage object
	 */
	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		Class<?> type = parameterContext.getParameter().getType();
		if (Stage.class.isAssignableFrom(type)) {
			return stageRef.get();
		}
		throw new ParameterResolutionException("only stage parameter is supported");
	}

	private final class GuiTestStage extends Stage {

		private static final Logger log = LoggerFactory.getLogger(GuiTestExtension.GuiTestStage.class);

		// overwritten, because package scope method
		private boolean primary = true;

		private final String testMethod;

		private final Instant created;

		GuiTestStage(String testMethod, Instant created) {
			super();
			this.testMethod = testMethod;
			this.created = created;
			log.debug("stage {} created", this);
		}

		@Override
		public void close() {
			super.close();
			log.debug("stage {} closed", this);
		}

		@Override
		public String toString() {
			return "GuiTestStage [testMethod=" + testMethod + ", created=" + created + ", primary=" + primary + "]";
		}
	}

}
