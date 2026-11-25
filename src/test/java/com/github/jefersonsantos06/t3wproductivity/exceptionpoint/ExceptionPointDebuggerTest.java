package com.github.jefersonsantos06.t3wproductivity.exceptionpoint;

import com.intellij.debugger.DebuggerTestCase;
import com.intellij.debugger.impl.OutputChecker;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Debug de verdade de src/test/testData/exceptionPoint: cria o ponto pela ação e roda o app com o debugger. Se o ponto
 * pausasse, o processo não terminaria e o teste falharia por timeout.
 */
public class ExceptionPointDebuggerTest extends DebuggerTestCase {

    private static final String APP = "ExceptionPointApp";
    private final StringBuilder output = new StringBuilder();

    public void testThrowsDefaultExceptionWithoutPausing() throws Exception {
        assertEquals(SuspendPolicy.NONE, addExceptionPoint(ExceptionPointType.DEFAULT_EXCEPTION).getSuspendPolicy());
        createLocalProcess(APP);
    }

    public void testThrowsCheckedExceptionNotDeclaredByTheMethod() throws Exception {
        addExceptionPoint("new java.io.IOException(\"checada \" + i)");
        createLocalProcess(APP);
    }

    public void testDisabledPointDoesNotThrow() throws Exception {
        addExceptionPoint(ExceptionPointType.DEFAULT_EXCEPTION).setEnabled(false);
        createLocalProcess(APP);
    }

    /** Pausa num breakpoint comum antes do run(2), desabilita o ponto e segue. */
    public void testDisablingDuringTheSessionStopsTheException() throws Exception {
        final var point = addExceptionPoint(ExceptionPointType.DEFAULT_EXCEPTION);
        final var type = XDebuggerUtil.getInstance().findBreakpointType(JavaLineBreakpointType.class);
        final int line = lineOf("        run(2);");
        EdtTestUtil.runInEdtAndWait(() -> WriteAction.run(() -> XDebuggerManager.getInstance(myProject)
                .getBreakpointManager().addLineBreakpoint(type, appFile().getUrl(), line, type.createBreakpointProperties(appFile(), line))));
        createLocalProcess(APP);
        onBreakpoint(context -> {
            EdtTestUtil.runInEdtAndWait(() -> point.setEnabled(false));
            resume(context);
        });
    }

    @Override
    protected void checkTestOutput() {
        final var text = output.toString();
        switch (getTestName(true)) {
            case "throwsDefaultExceptionWithoutPausing" -> {
                assertTrue(text, text.contains("capturada 1: java.lang.RuntimeException: T3W:"));
                assertTrue(text, text.contains("capturada 2: java.lang.RuntimeException: T3W:"));
                assertTrue(text, text.contains("capturada 3: java.lang.RuntimeException: T3W:")); // thread virtual
                assertFalse(text, text.contains("trabalhando"));
                assertEquals(text, 3, StringUtil.getOccurrenceCount(text, "T3W:")); // a expressão não é logada
            }
            case "throwsCheckedExceptionNotDeclaredByTheMethod" ->
                    assertTrue(text, text.contains("capturada 2: java.io.IOException: checada 2"));
            case "disablingDuringTheSessionStopsTheException" -> {
                assertTrue(text, text.contains("capturada 1: java.lang.RuntimeException: T3W:"));
                assertTrue(text, text.contains("trabalhando 2\nok 2"));
            }
            default -> {
                assertTrue(text, text.contains("trabalhando 3\nok 3"));
                assertFalse(text, text.contains("capturada"));
            }
        }
    }

    /** Roda a ação na linha marcada com "ponto de exceção", respondendo o diálogo com a expressão. */
    private XBreakpoint<?> addExceptionPoint(final String exception) throws IOException {
        final var file = appFile();
        final int line = lineOf("        System.out.println(\"trabalhando \" + i); // ponto de exceção");
        final var previousDialog = TestDialogManager.setTestInputDialog(message -> exception);
        try {
            EdtTestUtil.runInEdtAndWait(() -> {
                final var editor = FileEditorManager.getInstance(myProject)
                        .openTextEditor(new OpenFileDescriptor(myProject, file, line, 0), false);
                final var context = SimpleDataContext.builder()
                        .add(CommonDataKeys.PROJECT, myProject)
                        .add(CommonDataKeys.EDITOR, editor)
                        .add(CommonDataKeys.VIRTUAL_FILE, file)
                        .build();
                final var action = new AddExceptionPointAction();
                action.actionPerformed(TestActionEvent.createTestEvent(action, context));
            });
        } finally {
            TestDialogManager.setTestInputDialog(previousDialog);
        }
        final var points = XDebuggerManager.getInstance(myProject).getBreakpointManager()
                .getBreakpoints(ExceptionPointType.class);
        assertEquals(1, points.size());
        final XBreakpoint<?> point = points.iterator().next();
        assertEquals(exception, ExceptionPointType.exceptionOf(point));
        return point;
    }

    private VirtualFile appFile() {
        final var file = LocalFileSystem.getInstance().refreshAndFindFileByPath(getTestAppPath() + "/src/" + APP + ".java");
        assertNotNull(file);
        return file;
    }

    private int lineOf(final String text) throws IOException {
        final int line = VfsUtilCore.loadText(appFile()).lines().toList().indexOf(text);
        assertTrue(text, line >= 0);
        return line;
    }

    @Override
    protected String getTestAppPath() {
        return Path.of("src/test/testData/exceptionPoint").toAbsolutePath().toString();
    }

    @Override
    protected void compileProject() throws Exception {
        // javac do mesmo JDK que roda o app (no processo de teste o ToolProvider não acha o compilador)
        final var javac = Path.of(getTestProjectJdk().getHomePath(), "bin", "javac").toString();
        final var source = getTestAppPath() + "/src/" + APP + ".java";
        assertEquals(0, new ProcessBuilder(javac, "-g", "-d", getAppOutputPath(), source).inheritIO().start().waitFor());
    }

    @Override
    protected OutputChecker initOutputChecker() {
        return new OutputChecker(this::getTestAppPath, this::getAppOutputPath);
    }

    @Override
    public void print(final String s, final Key outputType) {
        output.append(s);
    }

    @Override
    public void println(final String s, final Key outputType) {
        output.append(s).append('\n');
    }
}
