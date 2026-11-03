package avsa.tools;

import avsa.agent.AiTaskRunner;
import avsa.agent.Tool;

/** A tool whose deterministic result is completed by an AI task. */
public abstract class AiAssistedTool implements Tool {

    protected final AiTaskRunner ai;

    protected AiAssistedTool(AiTaskRunner ai) {
        this.ai = ai;
    }
}
