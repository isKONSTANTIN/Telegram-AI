package su.knst.telegram.ai.logging;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

public class SLF4JProvider implements SLF4JServiceProvider {
    protected LoggerFactory factory;

    @Override
    public ILoggerFactory getLoggerFactory() {
        return factory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return null;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return null;
    }

    @Override
    public String getRequestedApiVersion() {
        return "2.0.7";
    }

    @Override
    public void initialize() {
        factory = new LoggerFactory();
    }
}
