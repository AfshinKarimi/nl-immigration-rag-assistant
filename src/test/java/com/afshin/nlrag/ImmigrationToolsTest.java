package com.afshin.nlrag;

import com.afshin.nlrag.retrieval.RagQueryService;
import com.afshin.nlrag.tools.ImmigrationTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;

class ImmigrationToolsTest {

    // Date tools do not call RagQueryService.
    private final ImmigrationTools tools = new ImmigrationTools(unusedRag());

    private static ObjectProvider<RagQueryService> unusedRag() {
        return new ObjectProvider<>() {
            @Override
            public RagQueryService getObject() throws BeansException {
                throw new UnsupportedOperationException("not used in this test");
            }

            @Override
            public RagQueryService getObject(Object... args) throws BeansException {
                throw new UnsupportedOperationException("not used in this test");
            }

            @Override
            public RagQueryService getIfAvailable() {
                return null;
            }

            @Override
            public RagQueryService getIfUnique() {
                return null;
            }
        };
    }

    @Test
    void shouldCalculateDateDifferenceCorrectly() {
        String result = tools.calculateDateDifference("2022-03-01", "2024-03-01");

        assertThat(result).contains("2 years, 0 months, 0 days");
    }

    @Test
    void shouldReturnFriendlyErrorForInvalidDateFormat() {
        String result = tools.calculateDateDifference("01-03-2022", "2024-03-01");

        assertThat(result).contains("Could not parse");
    }

    @Test
    void shouldEstimateRemaining30PercentRulingFromRecentStartDate() {
        String recentStart = java.time.LocalDate.now().minusYears(1).toString();

        String result = tools.estimateRemaining30PercentRuling(recentStart);

        assertThat(result).contains("remaining");
    }

    @Test
    void shouldReportExpiredRulingForOldStartDate() {
        String oldStart = java.time.LocalDate.now().minusYears(6).toString();

        String result = tools.estimateRemaining30PercentRuling(oldStart);

        assertThat(result).contains("already ended");
    }
}
