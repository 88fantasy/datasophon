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

package com.datasophon.api.migration;

import static com.datasophon.api.migration.DatabaseMigration.SPLIT;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.core.io.Resource;

@Data
@NoArgsConstructor
public class Migration implements Comparable<Migration> {
    
    private String version;
    
    private String executeUser;
    
    private Date executeDate;
    
    private boolean success;
    
    private Resource upgradeDDLFile;
    
    private Resource upgradeDMLFile;
    
    private Resource rollbackFile;
    
    public Migration(String version, Resource upgradeDDLFile, Resource upgradeDMLFile, Resource rollbackFile) {
        this.version = version;
        this.upgradeDDLFile = upgradeDDLFile;
        this.upgradeDMLFile = upgradeDMLFile;
        this.rollbackFile = rollbackFile;
    }
    
    @Override
    public int compareTo(Migration other) {
        int[] otherId = Arrays.stream(other.getVersion().split("\\.")).mapToInt(Integer::valueOf).toArray();
        int[] thisId = Arrays.stream(version.split("\\.")).mapToInt(Integer::valueOf).toArray();
        if (otherId.length != thisId.length) {
            return thisId.length - otherId.length;
        }
        for (int i = 0; i < thisId.length; i++) {
            if (thisId[i] != otherId[i]) {
                return thisId[i] - otherId[i];
            }
        }
        return 0;
    }
    
    public static boolean isMigrationFile(Resource resource) {
        if (resource == null) {
            return false;
        }
        String name = resource.getFilename();
        if (!StringUtils.endsWithIgnoreCase(name, ".sql")) {
            return false;
        }
        if (name == null || name.split(SPLIT).length != 2) {
            return false;
        }
        return name.startsWith(ScriptType.ROLLBACK.getPrefix()) || name.startsWith(ScriptType.UPGRADE.getPrefix());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Migration)) {
            return false;
        }
        Migration migration = (Migration) o;
        return version.equals(migration.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(version);
    }
}
