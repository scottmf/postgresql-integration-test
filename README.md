# postgresql-integration-test

Wraps <a href='https://github.com/yandex-qatools/postgresql-embedded'>PostgreSQL Embedded by yandex-qatools</a> with <a href='https://github.com/spring-projects/spring-boot'>Spring Boot</a>

## To Use

##### Sample integration test

<pre><code>
@EnablePostgres
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = PostgresAutoConfigurationApplication.class)
public class IntegrationTest {
    @Autowired
    private DataSource dataSource;

    @Test
    public void test() {
        // test something with a pg backend ...
    }
}
</code></pre>

##### Config properties - $HOME/.pgtest/devbuild.properties

<pre><code>
postgres.username=postgres
postgres.password=postgres
postgres.dbName=test
# postgres version valid values are 9.2, 9.3, 9.4 or 9.5
postgres.version=test
postgres.postExecSqlFiles=classpath:/path/to/sql/file,/absolute/path/to/sql/file,...
</code></pre>
