package redhat.jenkins.plugins.rhda.utils;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class UtilsTest {
	
	@Test
	public void testUtilsFunctions() {
		System.setProperty("os.name", "Linux");
		assertTrue(Utils.isLinux());
		assertFalse(Utils.isWindows());
		assertFalse(Utils.isMac());
		
		System.setProperty("sun.arch.data.model", "64");
		assertTrue(Utils.is64());
		assertFalse(Utils.is32());
		
		String validJson = "{ 'a_b': 10}";
		assertTrue(Utils.isJSONValid(validJson));
		
		String invalidJson = "abcdefgh";
		assertFalse(Utils.isJSONValid(invalidJson));
	}

}
