package com.webnobis.commons.fx.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import javafx.application.Platform;
import javafx.stage.Stage;

@ExtendWith(GuiTestExtension.class)
class GuiTestExtensionTest {

	@Test
	void testSimple(Stage stage) {
		assertNotNull(stage);
		assertFalse(stage.isShowing());
	}

	@Test
	void testShowing(Stage stage) {
		CountDownLatch waitForShowing = new CountDownLatch(1);
		Platform.runLater(() -> {
			waitForShowing.countDown();
			stage.show();
		});
		Platform.runLater(() -> {
			try {
				waitForShowing.await();
			} catch (InterruptedException e) {
				fail(e);
			}
			assertTrue(stage.isShowing());
			stage.close();
		});
	}

	@Test
	void testWithout() {
		assertTrue(true);
	}

	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	@Test
	void testTimeout() throws InterruptedException {
		Thread.sleep(TimeUnit.SECONDS.toMillis(GuiTestExtension.DEFAULT_TIMEOUT_SECONDS + 3));
		assertTrue(true);
	}

}
