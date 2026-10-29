package network.foodsharing.common.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Test
    void detectsDelimitersAndHandlesQuotesBomAndLineBreaks() {
        assertThat(CsvParser.parse("a,b,c\n1,2,3\n")).containsExactly(List.of("a", "b", "c"), List.of("1", "2", "3"));
        assertThat(CsvParser.parse("﻿a;b\r\n\"x;y\";\"he said \"\"hi\"\"\"\r\n"))
                .containsExactly(List.of("a", "b"), List.of("x;y", "he said \"hi\""));
        assertThat(CsvParser.parse("a\tb\n1\t\"two\nlines\"")).containsExactly(List.of("a", "b"),
                List.of("1", "two\nlines"));
        assertThat(CsvParser.parse("a,b\n\n , \n1,2")).containsExactly(List.of("a", "b"), List.of("1", "2"));
        assertThat(CsvParser.parse("")).isEmpty();
        assertThat(CsvParser.parse(null)).isEmpty();
    }

    @Test
    void commentLinesStartWithHash() {
        assertThat(CsvParser.isComment(List.of("# model pending", "x"))).isTrue();
        assertThat(CsvParser.isComment(List.of("  #note"))).isTrue();
        assertThat(CsvParser.isComment(List.of("a#b", "#"))).isFalse();
        assertThat(CsvParser.isComment(List.of())).isFalse();
    }
}
