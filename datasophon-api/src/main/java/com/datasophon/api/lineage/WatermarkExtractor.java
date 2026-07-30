/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.lineage;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** Extracts the ordering watermark independently from ingest persistence. */
@FunctionalInterface
public interface WatermarkExtractor {

    Extraction extract(DecodedLineageEvent event, Instant receivedAt);

    record Extraction(long epochMillis, Source source) {

        public boolean degraded() {
            return source == Source.RECEIVED_AT;
        }
    }

    enum Source {
        NOMINAL_TIME,
        EVENT_TIME,
        RECEIVED_AT
    }

    final class Default implements WatermarkExtractor {

        @Override
        public Extraction extract(DecodedLineageEvent event, Instant receivedAt) {
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(receivedAt, "receivedAt");
            // TODO L0-#8: 上游可靠单调序号来源尚未实机确认。
            Long nominal = parseEpochMillis(event.nominalStartTime());
            if (nominal != null) {
                return new Extraction(nominal, Source.NOMINAL_TIME);
            }
            Long eventTime = parseEpochMillis(event.eventTime());
            if (eventTime != null) {
                return new Extraction(eventTime, Source.EVENT_TIME);
            }
            return new Extraction(receivedAt.toEpochMilli(), Source.RECEIVED_AT);
        }

        private static Long parseEpochMillis(String value) {
            if (value == null) {
                return null;
            }
            try {
                return Instant.parse(value).toEpochMilli();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
