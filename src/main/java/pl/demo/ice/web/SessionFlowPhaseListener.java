package pl.demo.ice.web;

import org.springframework.webflow.execution.repository.FlowExecutionAccessException;
import org.springframework.webflow.executor.jsf.FlowExecutionHolder;
import org.springframework.webflow.executor.jsf.FlowPhaseListener;

import javax.faces.context.FacesContext;
import java.io.IOException;

/** Retains the current legacy flow execution for refreshes of the root URL. */
public class SessionFlowPhaseListener extends FlowPhaseListener {
    public static final String EXECUTION_KEY = SessionFlowPhaseListener.class.getName() + ".executionKey";

    @Override
    protected void prepareApplicationView(FacesContext context, FlowExecutionHolder holder) {
        super.prepareApplicationView(context, holder);
        if (holder.getFlowExecution().isActive() && holder.getFlowExecutionKey() != null) {
            context.getExternalContext().getSessionMap().put(EXECUTION_KEY, holder.getFlowExecutionKey().toString());
        } else {
            context.getExternalContext().getSessionMap().remove(EXECUTION_KEY);
        }
    }

    @Override
    protected void handleFlowExecutionAccessException(FlowExecutionAccessException exception, FacesContext context) {
        // Only recover stale keys supplied by our root refresh; preserve normal Web Flow error handling otherwise.
        if (!Boolean.TRUE.equals(context.getExternalContext().getRequestMap().get(SessionFlowPhaseListener.class.getName() + ".refresh"))) {
            super.handleFlowExecutionAccessException(exception, context);
            return;
        }
        context.getExternalContext().getSessionMap().remove(EXECUTION_KEY);
        try {
            context.getExternalContext().redirect(context.getExternalContext().getRequestContextPath() + "/");
            context.responseComplete();
        } catch (IOException error) {
            throw new IllegalStateException("Cannot restart expired flow", error);
        }
    }
}
