package net.adshares.esc.qa.stepdefs;

import cucumber.api.java.Before;
import net.adshares.esc.qa.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Cucumber hooks.
 */
public class Hooks {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static int testCount = 0;

    @Before
    public void beforeTest() {
        if (testCount == 0) {
            // this code will run only once before all tests

            // deletes esc cache
            try {
                Utils.deleteDirectory("log");
                Utils.deleteDirectory("out");
            } catch (IOException e) {
                log.warn("Unable to delete ESC cache");
            }
        }
        testCount++;
    }
}
