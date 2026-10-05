package com.switchtype.live;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommenderTest {

    @Test
    void bundledTableHasOneRowPerSimulatedUserType() throws Exception {
        List<Recommender.Recommendation> table = Recommender.bundledTable();
        assertEquals(5, table.size());
    }

    @Test
    void closestTypeIsChosenByMeanAndSpread() throws Exception {
        List<Recommender.Recommendation> table = Recommender.bundledTable();
        assertEquals("quick", Recommender.closest(table, 330, 55).profile);
        assertEquals("slow", Recommender.closest(table, 900, 200).profile);
        assertEquals("typical", Recommender.closest(table, 480, 90).profile);
    }
}
