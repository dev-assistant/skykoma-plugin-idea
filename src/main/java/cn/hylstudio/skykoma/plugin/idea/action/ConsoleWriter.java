package cn.hylstudio.skykoma.plugin.idea.action;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Writer;
import java.lang.ref.Reference;

public final class ConsoleWriter extends Writer {
    private static final Logger LOG = Logger.getInstance(ConsoleWriter.class);

    private final @NotNull Reference<? extends RunContentDescriptor> myDescriptor;
    private final Key<?> myOutputType;
    private final AnsiEscapeDecoder myAnsiEscapeDecoder;

    public ConsoleWriter(@NotNull Reference<? extends RunContentDescriptor> descriptor, Key<?> outputType) {
        myDescriptor = descriptor;
        myOutputType = outputType;
        myAnsiEscapeDecoder = new AnsiEscapeDecoder();
    }

    @Nullable
    public RunContentDescriptor getDescriptor() {
        return myDescriptor.get();
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        RunContentDescriptor descriptor = myDescriptor.get();
        ConsoleViewImpl console = ObjectUtils.tryCast(descriptor != null ? descriptor.getExecutionConsole() : null, ConsoleViewImpl.class);
        String text = new String(cbuf, off, len);
        if (console == null) {
            LOG.info(myOutputType + ": " + text);
        } else {
            myAnsiEscapeDecoder.escapeText(text, myOutputType, (s, attr) -> {
                console.print(s, ConsoleViewContentType.getConsoleViewType(attr));
            });
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}