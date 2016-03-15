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

import static java.lang.String.*;
import static java.util.Optional.*;
import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.Resource;

import ru.yandex.qatools.embed.postgresql.PostgresExecutable;
import ru.yandex.qatools.embed.postgresql.PostgresProcess;
import ru.yandex.qatools.embed.postgresql.PostgresStarter;
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.Credentials;
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.Net;
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.Storage;
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.Timeout;
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig;
import ru.yandex.qatools.embed.postgresql.distribution.Version.Main;
import de.flapdoodle.embed.process.distribution.IVersion;

@Configuration
@EnableConfigurationProperties(PostgresConfigurationProperties.class)
@PropertySource(value="${user.home}/.pgtest/devbuild.properties", ignoreResourceNotFound=true)
public class PostgresAutoConfiguration implements ApplicationListener<ApplicationEvent> {

    private final Log log = LogFactory.getLog(PostgresAutoConfiguration.class);

    private Optional<PostgresProcess> process = ofNullable(null);
    private PostgresExecutable exec;
    private PostgresConfig config;

    @Autowired
    private PostgresConfigurationProperties props;

    @Lazy @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource() {
        String url = format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s",
            config.net().host(), config.net().port(),
            config.storage().dbName(),
            config.credentials().username(), config.credentials().password());
        DataSource rtn = new DataSource();
        rtn.setDriverClassName("org.postgresql.Driver");
        rtn.setUsername(props.getUsername());
        rtn.setPassword(props.getPassword());
        rtn.setUrl(url);
        rtn.setMinIdle(2);
        rtn.setMaxActive(4);
        rtn.setMaxIdle(4);
        rtn.setInitialSize(2);
        rtn.setMaxWait(30000);
        rtn.setJdbcInterceptors("StatementFinalizer;ResetAbandonedTimer");
        rtn.setValidationQuery("select 1");
        rtn.setTimeBetweenEvictionRunsMillis(30000);
        rtn.setMinEvictableIdleTimeMillis(60000);
        rtn.setValidationInterval(30000);
        rtn.setLogAbandoned(true);
        rtn.setRemoveAbandoned(true);
        rtn.setRemoveAbandonedTimeout(60);
        rtn.setJmxEnabled(true);
        rtn.setAbandonWhenPercentageFull(75);
        rtn.setTestOnBorrow(true);
        rtn.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return rtn;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        log.info("receieved application event " + event.getClass().getName());
        if (event instanceof ContextRefreshedEvent) {
            ContextRefreshedEvent e = (ContextRefreshedEvent) event;
            ApplicationContext ctx = e.getApplicationContext();
            restartPostgres(ctx);
        } else if (event instanceof ContextClosedEvent) {
            process.ifPresent(p -> p.stop());
        }
    }

    private void restartPostgres(ApplicationContext ctx) {
        if (!process.isPresent() && exec == null) {
            initializePostgres(ctx);
        } else {
            process.ifPresent(p -> p.stop());
            process = empty();
            initializePostgres(ctx);
        }
    }

    private void initializePostgres(ApplicationContext ctx) {
        try {
            log.info("Initializing PostgreSQL");
            PostgresStarter<PostgresExecutable, PostgresProcess> runtime = PostgresStarter.getDefaultInstance();
            IVersion version = getVersion();
            config = new PostgresConfig(version, new Net(), new Storage(props.getDbName()), new Timeout(),
                new Credentials(props.getUsername(), props.getPassword()));
            exec = runtime.prepare(config);
            process = of(exec.start());
            // connecting to a running Postgres
            String url = format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s",
                config.net().host(), config.net().port(),
                config.storage().dbName(),
                config.credentials().username(), config.credentials().password());
            assertConnectionAndRunPostExecSqlFiles(url, ctx);
        } catch (IOException | SQLException e) {
            throw new RuntimeException("problem initializing postgres database: " + e,e);
        }
    }

    private void assertConnectionAndRunPostExecSqlFiles(String url, ApplicationContext ctx)
            throws SQLException, IOException {
        String postExecSqlFiles = props.getPostExecSqlFiles();
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            if (postExecSqlFiles != null && !postExecSqlFiles.trim().isEmpty()) {
                String[] files = postExecSqlFiles.split(",");
                for (String file : files) {
                    log.info("reading sql from file " + file);
                    Collection<String> sqls = getSqls(file, ctx);
                    for (String sql : sqls) {
                        try (Statement stmt = conn.createStatement()) {
                            log.info("executing " + sql);
                            stmt.execute(sql);
                        }
                    }
                }
            }
        }
    }

    private Collection<String> getSqls(String file, ApplicationContext ctx) throws IOException {
        StringBuilder builder = new StringBuilder();
        Resource resource = ctx.getResource(file);
        if (!resource.exists()) {
            return Collections.emptyList();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(resource.getFile()))) {
            String line;
            while (null != (line = reader.readLine())) {
                if (!line.startsWith("--")) {
                    builder.append(line);
                }
            }
        }
        return Arrays.asList(builder.toString().split(";"));
    }

    private IVersion getVersion() {
        String version = props.getVersion();
        Main[] enums = Main.class.getEnumConstants();
        for (Main m : enums) {
            if (m.asInDownloadPath().startsWith(version)) {
                return m;
            }
        }
        throw new IllegalArgumentException(
            format("unknown version %s check %s class for valid versions", version, Main.class));
    }

}
