package cucumber.runtime.formatter;


import cucumber.api.Result;
import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.*;
import cucumber.api.formatter.Formatter;
import cucumber.runtime.Argument;
import cucumber.util.FixJava;
import cucumber.util.Mapper;
import gherkin.ast.*;
import gherkin.pickles.PickleTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slf4jFormatter is edited version of PrettyFormatter.
 * <br /><br />
 * <i>Appendable</i> (out) was changed to Slf4j <i>Logger</i> (log).
 * <br />
 * <i>AnsiFormats</i> were changed to <i>LogLevel</i>s.
 */
public class Slf4jFormatter implements Formatter {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String SCENARIO_INDENT = "  ";
    private static final String STEP_INDENT = "    ";
    private static final String EXAMPLES_INDENT = "    ";
    private final TestSourcesModel testSources = new TestSourcesModel();
    private String currentFeatureFile;
    private TestCase currentTestCase;
    private ScenarioOutline currentScenarioOutline;
    private Examples currentExamples;
    private int locationIndentation;
    private final Mapper<Tag, String> tagNameMapper = Tag::getName;
    private final Mapper<PickleTag, String> pickleTagNameMapper = PickleTag::getName;

    private final EventHandler<TestSourceRead> testSourceReadHandler = this::handleTestSourceRead;
    private final EventHandler<TestCaseStarted> caseStartedHandler = this::handleTestCaseStarted;
    private final EventHandler<TestStepStarted> stepStartedHandler = this::handleTestStepStarted;
    private final EventHandler<TestStepFinished> stepFinishedHandler = this::handleTestStepFinished;
    private final EventHandler<WriteEvent> writeEventHandler = this::handleWrite;
    private final EventHandler<TestRunFinished> runFinishedHandler = event -> finishReport();

    private Map<String, LogLevel> msgLevelMap;

    @SuppressWarnings("WeakerAccess") // Used by PluginFactory
    public Slf4jFormatter() {
        msgLevelMap = new HashMap<>();
        msgLevelMap.put("undefined", LogLevel.INFO);
        msgLevelMap.put("pending", LogLevel.INFO);
        msgLevelMap.put("executing", LogLevel.INFO);
        msgLevelMap.put("comment", LogLevel.INFO);
        msgLevelMap.put("passed", LogLevel.INFO);
        msgLevelMap.put("output", LogLevel.INFO);

        msgLevelMap.put("outline", LogLevel.WARN);
        msgLevelMap.put("skipped", LogLevel.WARN);
        msgLevelMap.put("tag", LogLevel.WARN);

        msgLevelMap.put("failed", LogLevel.ERROR);
        msgLevelMap.put("ambiguous", LogLevel.ERROR);
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestSourceRead.class, testSourceReadHandler);
        publisher.registerHandlerFor(TestCaseStarted.class, caseStartedHandler);
        publisher.registerHandlerFor(TestStepStarted.class, stepStartedHandler);
        publisher.registerHandlerFor(TestStepFinished.class, stepFinishedHandler);
        publisher.registerHandlerFor(WriteEvent.class, writeEventHandler);
        publisher.registerHandlerFor(TestRunFinished.class, runFinishedHandler);
    }

    private void handleTestSourceRead(TestSourceRead event) {
        testSources.addTestSourceReadEvent(event.uri, event);
    }

    private void handleTestCaseStarted(TestCaseStarted event) {
        handleStartOfFeature(event);
        handleScenarioOutline(event);
        if (testSources.hasBackground(currentFeatureFile, event.testCase.getLine())) {
            printBackground(event.testCase);
            currentTestCase = event.testCase;
        } else {
            printScenarioDefinition(event.testCase);
        }
    }

    private void handleTestStepStarted(TestStepStarted event) {
        if (!event.testStep.isHook()) {
            if (isFirstStepAfterBackground(event.testStep)) {
                printScenarioDefinition(currentTestCase);
                currentTestCase = null;
            }
        }
    }

    private void handleTestStepFinished(TestStepFinished event) {
        TestStep testStep = event.testStep;
        if (!testStep.isHook()) {
            printStep(testStep, event.result);
        }
        printError(event.result);
    }

    private void handleWrite(WriteEvent event) {
        log(event.text);
    }

    private void finishReport() {
        logNewLine();
    }

    private void handleStartOfFeature(TestCaseStarted event) {
        if (currentFeatureFile == null || !currentFeatureFile.equals(event.testCase.getUri())) {
            if (currentFeatureFile != null) {
                logNewLine();
            }
            currentFeatureFile = event.testCase.getUri();
            printFeature(currentFeatureFile);
        }
    }

    private void handleScenarioOutline(TestCaseStarted event) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, event.testCase.getLine());
        if (TestSourcesModel.isScenarioOutlineScenario(astNode)) {
            ScenarioOutline scenarioOutline = (ScenarioOutline) TestSourcesModel.getScenarioDefinition(astNode);
            if (currentScenarioOutline == null || !currentScenarioOutline.equals(scenarioOutline)) {
                currentScenarioOutline = scenarioOutline;
                printScenarioOutline(currentScenarioOutline);
            }
            if (currentExamples == null || !currentExamples.equals(astNode.parent.node)) {
                currentExamples = (Examples) astNode.parent.node;
                printExamples(currentExamples);
            }
        } else {
            currentScenarioOutline = null;
            currentExamples = null;
        }
    }

    private void printScenarioOutline(ScenarioOutline scenarioOutline) {
        logNewLine();
        printTags(scenarioOutline.getTags(), SCENARIO_INDENT);
        log(SCENARIO_INDENT + getScenarioDefinitionText(scenarioOutline) + " " + getLocationText(currentFeatureFile, scenarioOutline.getLocation().getLine()));
        printDescription(scenarioOutline.getDescription());
        for (Step step : scenarioOutline.getSteps()) {
            log(STEP_INDENT + step.getKeyword() + step.getText(), msgLevelMap.get("skipped"));
        }
    }

    private void printExamples(Examples examples) {
        logNewLine();
        printTags(examples.getTags(), EXAMPLES_INDENT);
        log(EXAMPLES_INDENT + examples.getKeyword() + ": " + examples.getName());
        printDescription(examples.getDescription());
    }

    private void printStep(TestStep testStep, Result result) {
        String keyword = getStepKeyword(testStep);
        String stepText = testStep.getStepText();
        String locationPadding = createPaddingToLocation(STEP_INDENT, keyword + stepText);
        String formattedStepText = formatStepText(keyword, stepText, testStep.getDefinitionArgument());
        log(STEP_INDENT + formattedStepText + locationPadding + getLocationText(testStep.getCodeLocation()), msgLevelMap.get(result.getStatus().lowerCaseName()));
    }

    private String formatStepText(String keyword, String stepText, List<Argument> arguments) {
        int beginIndex = 0;
        StringBuilder result = new StringBuilder(keyword);
        for (Argument argument : arguments) {
            // can be null if the argument is missing.
            if (argument.getOffset() != null) {
                int argumentOffset = argument.getOffset();
                // a nested argument starts before the enclosing argument ends; ignore it when formatting
                if (argumentOffset < beginIndex) {
                    continue;
                }
                String text = stepText.substring(beginIndex, argumentOffset);
                result.append(text);
            }
            // val can be null if the argument isn't there, for example @And("(it )?has something")
            if (argument.getVal() != null) {
                result.append(argument.getVal());
                // set beginIndex to end of argument
                beginIndex = argument.getOffset() + argument.getVal().length();
            }
        }
        if (beginIndex != stepText.length()) {
            String text = stepText.substring(beginIndex, stepText.length());
            result.append(text);
        }
        return result.toString();
    }

    private String getScenarioDefinitionText(ScenarioDefinition definition) {
        return definition.getKeyword() + ": " + definition.getName();
    }

    private String getLocationText(String file, int line) {
        return getLocationText(file + ":" + line);
    }

    private String getLocationText(String location) {
        return "# " + location;
    }

    private StringBuffer stepText(TestStep testStep) {
        String keyword = getStepKeyword(testStep);
        return new StringBuffer(keyword + testStep.getStepText());
    }

    private String getStepKeyword(TestStep testStep) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testStep.getStepLine());
        if (astNode != null) {
            Step step = (Step) astNode.node;
            return step.getKeyword();
        } else {
            return "";
        }
    }

    private boolean isFirstStepAfterBackground(TestStep testStep) {
        return currentTestCase != null && !isBackgroundStep(testStep);
    }

    private boolean isBackgroundStep(TestStep testStep) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testStep.getStepLine());
        if (astNode != null) {
            return TestSourcesModel.isBackgroundStep(astNode);
        }
        return false;
    }

    private void printFeature(String path) {
        Feature feature = testSources.getFeature(path);
        printTags(feature.getTags());
        log(feature.getKeyword() + ": " + feature.getName());
        printDescription(feature.getDescription());
    }

    private void printTags(List<Tag> tags) {
        printTags(tags, "");
    }

    private void printTags(List<Tag> tags, String indent) {
        if (!tags.isEmpty()) {
            log(indent + FixJava.join(FixJava.map(tags, tagNameMapper), " "));
        }
    }

    private void printPickleTags(List<PickleTag> tags, String indent) {
        if (!tags.isEmpty()) {
            log(indent + FixJava.join(FixJava.map(tags, pickleTagNameMapper), " "));
        }
    }

    private void printDescription(String description) {
        if (description != null) {
            log(description);
        }
    }

    private void printBackground(TestCase testCase) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testCase.getLine());
        if (astNode != null) {
            Background background = TestSourcesModel.getBackgroundForTestCase(astNode);
            String backgroundText = getScenarioDefinitionText(background);
            calculateLocationIndentation(SCENARIO_INDENT + backgroundText, testCase.getTestSteps(), true);
            String locationPadding = createPaddingToLocation(SCENARIO_INDENT, backgroundText);
            logNewLine();
            log(SCENARIO_INDENT + backgroundText + locationPadding + getLocationText(currentFeatureFile, background.getLocation().getLine()));
            printDescription(background.getDescription());
        }
    }

    private void printScenarioDefinition(TestCase testCase) {
        ScenarioDefinition scenarioDefinition = testSources.getScenarioDefinition(currentFeatureFile, testCase.getLine());
        String definitionText = scenarioDefinition.getKeyword() + ": " + testCase.getName();
        calculateLocationIndentation(SCENARIO_INDENT + definitionText, testCase.getTestSteps());
        String locationPadding = createPaddingToLocation(SCENARIO_INDENT, definitionText);
        logNewLine();
        printPickleTags(testCase.getTags(), SCENARIO_INDENT);
        log(SCENARIO_INDENT + definitionText + locationPadding + getLocationText(currentFeatureFile, testCase.getLine()));
        printDescription(scenarioDefinition.getDescription());
    }

    private void printError(Result result) {
        if (result.getError() != null) {
            log("      " + result.getErrorMessage(), msgLevelMap.get(result.getStatus().lowerCaseName()));
        }
    }

    private void calculateLocationIndentation(String definitionText, List<TestStep> testSteps) {
        calculateLocationIndentation(definitionText, testSteps, false);
    }

    private void calculateLocationIndentation(String definitionText, List<TestStep> testSteps, boolean useBackgroundSteps) {
        int maxTextLength = definitionText.length();
        for (TestStep step : testSteps) {
            if (step.isHook()) {
                continue;
            }
            if (isBackgroundStep(step) == useBackgroundSteps) {
                StringBuffer stepText = stepText(step);
                maxTextLength = Math.max(maxTextLength, STEP_INDENT.length() + stepText.length());
            }
        }
        locationIndentation = maxTextLength + 1;
    }

    private String createPaddingToLocation(String indent, String text) {
        StringBuilder padding = new StringBuilder();
        for (int i = indent.length() + text.length(); i < locationIndentation; ++i) {
            padding.append(' ');
        }
        return padding.toString();
    }


    private enum LogLevel {
        ERROR,
        WARN,
        INFO,
    }

    private void logNewLine() {
        log("");
    }

    private void log(String text) {
        log.info(text);
    }

    private void log(String text, LogLevel level) {
        switch (level) {
            case ERROR:
                log.error(text);
                break;
            case WARN:
                log.warn(text);
                break;
            case INFO:
            default:
                log.info(text);
                break;

        }
    }


}
