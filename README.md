# Java Fx Commons

## Test

### Java Fx JUnit5 Extension

It starts the Java Fx platform, to could test graphical Fx elements within tests.

The primary stage could be injected as test method parameter:

```java
@Test
void testSimple(Stage stage) {
	assertNotNull(stage);
	assertFalse(stage.isShowing());
}
```

After last test, the Java Fx platform will be exited.