package net.adshares.esc.qa;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "cucumber.runtime.formatter.Slf4jFormatter"})
public class RunCucumberTest {
}