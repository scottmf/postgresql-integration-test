/**
 * Copyright 2016 Scott Feldstein
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *    
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.scottieknows.test.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import de.flapdoodle.embed.process.distribution.IVersion;
import static ru.yandex.qatools.embed.postgresql.distribution.Version.Main.*;

@ConfigurationProperties("postgres")
public class PostgresConfigurationProperties {

    private String username = "postgres";
    private String password = "postgres";
    private String dbName = "test";
    /**
     * Comma separated list of files to execute after the database is initialized
     * Can either be an absolute path or classpath resource
     */
    private String postExecSqlFiles;
    private String version = V9_5.asInDownloadPath();

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPostExecSqlFiles() {
        return postExecSqlFiles;
    }

    public void setPostExecSqlFiles(String postExecSqlFiles) {
        this.postExecSqlFiles = postExecSqlFiles;
    }

    @Override
    public String toString() {
        return "PostgresConfigurationProperties [username=" + username + ", password=" + password + ", dbName="
            + dbName + ", postExecSqlFiles=" + postExecSqlFiles + ", version=" + version + "]";
    }

}
