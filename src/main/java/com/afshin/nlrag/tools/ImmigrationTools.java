package com.afshin.nlrag.tools;

import com.afshin.nlrag.model.AssistantAnswer;
import com.afshin.nlrag.retrieval.RagQueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;

/**
 * Tools the AI agent can call to answer immigration/work-related questions.
 *
 * <p>Each {@code @Tool}-annotated method is exposed twice for free:</p>
 * <ol>
 *   <li>To the in-process {@link com.afshin.nlrag.agent.ImmigrationAgentService},
 *       which lets an LLM decide when to call it during a conversation
 *       (agentic tool-calling / function-calling).</li>
 *   <li>To any external MCP client (Claude Desktop, an IDE, another agent)
 *       via {@link com.afshin.nlrag.mcp.McpServerConfig}, which registers
 *       these same methods as MCP tools over the Model Context Protocol.</li>
 * </ol>
 *
 * <p>This is the concrete difference between "a RAG chatbot" and "an AI
 * agent": the RAG pipeline alone always does retrieve -&gt; generate. An
 * agent additionally decides, per turn, *whether* retrieval is even the
 * right action, or whether a different tool (e.g. a date calculation) is
 * what the question actually needs.</p>
 */
@Component
public class ImmigrationTools {

    /**
     * Resolved at call time, not construction time. Search wraps the RAG
     * pipeline, which needs the ChatModel; Spring AI may also discover
     * {@code @Tool} beans while that ChatModel is still being built.
     */
    private final ObjectProvider<RagQueryService> ragQueryService;

    public ImmigrationTools(ObjectProvider<RagQueryService> ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @Tool(description = """
            Search official Dutch immigration and labour-law documentation
            (IND procedures, 30% ruling, BSN, labour contracts) and answer a
            question with citations. Use this for any question about rules,
            eligibility, procedures, or definitions.
            """)
    public String searchImmigrationDocs(
            @ToolParam(description = "The user's question, in its original wording") String question) {
        AssistantAnswer answer = ragQueryService.getObject().answer(question);
        if (!answer.answeredWithConfidence()) {
            return answer.answer();
        }
        return answer.answer() + "\n\nSources: " + String.join("; ", answer.citations());
    }

    @Tool(description = """
            Calculate the number of days, months, and years between two
            ISO-8601 dates (yyyy-MM-dd). Use this for questions like
            "how many days until my visa expires" or "how long have I
            held the 30% ruling" instead of guessing the arithmetic.
            """)
    public String calculateDateDifference(
            @ToolParam(description = "Start date in yyyy-MM-dd format") String startDate,
            @ToolParam(description = "End date in yyyy-MM-dd format") String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            Period period = Period.between(start, end);
            long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end);
            return "%d years, %d months, %d days (%d total days)".formatted(
                    period.getYears(), period.getMonths(), period.getDays(), totalDays);
        } catch (DateTimeParseException e) {
            return "Could not parse one of the dates. Please use yyyy-MM-dd format.";
        }
    }

    @Tool(description = """
            Estimate the remaining duration, in years and months, of the
            30%% ruling given its start date, assuming the current maximum
            duration of 5 years. Use this rather than doing the arithmetic
            yourself so the 5-year rule stays in one place.
            """)
    public String estimateRemaining30PercentRuling(
            @ToolParam(description = "Start date of the 30%% ruling, yyyy-MM-dd") String startDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate expiry = start.plusYears(5);
            LocalDate today = LocalDate.now();
            if (today.isAfter(expiry)) {
                return "The 30% ruling period (5 years from " + startDate + ") has already ended, on " + expiry + ".";
            }
            Period remaining = Period.between(today, expiry);
            return "Approximately %d years and %d months remaining, ending on %s."
                    .formatted(remaining.getYears(), remaining.getMonths(), expiry);
        } catch (DateTimeParseException e) {
            return "Could not parse the start date. Please use yyyy-MM-dd format.";
        }
    }
}
