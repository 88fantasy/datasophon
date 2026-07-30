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

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Rejects lineage operations when this process does not own the dedicated MySQL lease. */
public final class LineageLeaseGuard {

    public static final String UNAVAILABLE_MESSAGE =
            "Lineage service is unavailable because this Master does not own the lineage lease";

    private static final Logger log = LoggerFactory.getLogger(LineageLeaseGuard.class);

    private final BooleanSupplier owner;

    public LineageLeaseGuard(LineageMasterLease lease) {
        this(lease::isOwner);
    }

    public LineageLeaseGuard(BooleanSupplier owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public boolean isOwner() {
        return owner.getAsBoolean();
    }

    public void requireOwner() {
        if (!isOwner()) {
            log.error(UNAVAILABLE_MESSAGE);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
    }
}
