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

 *
 */

package com.datasophon.worker.hook.db;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum ScriptType {
    
    UPGRADE("V"),
    
    ROLLBACK("R");
    
    private final String prefix;
    
    ScriptType(String prefix) {
        this.prefix = prefix;
    }
    
    public static ScriptType of(String version) {
        if (StringUtils.isBlank(version)) {
            return null;
        }
        if (version.startsWith(UPGRADE.getPrefix())) {
            return UPGRADE;
        } else if (version.startsWith(ROLLBACK.getPrefix())) {
            return ROLLBACK;
        } else {
            return null;
        }
    }
}
