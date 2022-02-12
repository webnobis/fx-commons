import com.webnobis.commons.fx.test.FxTestExtensions;

/**
 * Java Fx Commons
 * 
 * @author steffen
 *
 */
@FxTestExtensions 
module com.webnobis.commons.fx {

	requires javafx.graphics;

	exports com.webnobis.commons.fx.test;

}